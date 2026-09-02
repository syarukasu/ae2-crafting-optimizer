package com.syaru.ae2craftingoptimizer.engine.parallel;

import com.syaru.ae2craftingoptimizer.engine.PlanningCancellationToken;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/** 一件のGraph buildとamount passを同じpool、snapshot、cancel tokenで結ぶ。 */
final class ParallelPlanSession<K> {
    private final ParallelPlanRequest<K> request;
    private final PlanningCancellationToken cancellation;
    private final AtomicReference<State> state = new AtomicReference<>(State.QUEUED);

    ParallelPlanSession(
            ParallelPlanRequest<K> request,
            PlanningCancellationToken cancellation) {
        this.request = Objects.requireNonNull(request, "request");
        this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
    }

    CompletableFuture<ParallelPlanResult<K>> start(
            ParallelPlannerPool pool,
            long queueWaitNanos) {
        ParallelPlanGraph<K> cached = request.patterns().cachedGraph(
                request.requestedOutput());
        if (cached != null) {
            if (!state.compareAndSet(State.QUEUED, State.AMOUNT_RUNNING)) {
                return failedFuture(new IllegalStateException(
                        "parallel session was started twice"));
            }
            CompletableFuture<ParallelPlanResult<K>> result = startAmountPass(
                    pool,
                    cached,
                    cachedGraphMetrics(),
                    queueWaitNanos,
                    true);
            trackCompletion(result);
            return result;
        }
        if (!state.compareAndSet(State.QUEUED, State.GRAPH_BUILDING)) {
            return failedFuture(new IllegalStateException("parallel session was started twice"));
        }
        CompletableFuture<ParallelPlanResult<K>> result = ParallelPlanGraph.buildAsync(
                        pool,
                        request.patterns(),
                        request.requestedOutput(),
                        cancellation)
                .thenCompose(graphOutcome -> {
                    if (graphOutcome.graph().isEmpty()) {
                        state.set(State.COMPLETED);
                        return CompletableFuture.completedFuture(ParallelPlanResult.unsupported(
                                graphOutcome.failure(),
                                ParallelPlanMetrics.graphOnly(
                                        graphOutcome.metrics(),
                                        queueWaitNanos)));
                    }
                    if (!state.compareAndSet(State.GRAPH_BUILDING, State.AMOUNT_RUNNING)) {
                        return failedFuture(new IllegalStateException(
                                "parallel session changed state before amount pass"));
                    }
                    ParallelPlanGraph<K> graph = graphOutcome.graph().orElseThrow();
                    request.patterns().cacheGraph(request.requestedOutput(), graph);
                    return startAmountPass(
                            pool,
                            graph,
                            graphOutcome.metrics(),
                            queueWaitNanos,
                            false);
                });
        trackCompletion(result);
        return result;
    }

    private void trackCompletion(CompletableFuture<ParallelPlanResult<K>> result) {
        result.whenComplete((ignored, failure) -> {
            if (failure == null) {
                state.set(State.COMPLETED);
            } else if (cancellation.isCancelled()) {
                state.set(State.CANCELLED);
            } else {
                state.set(State.FAILED);
            }
        });
    }

    private CompletableFuture<ParallelPlanResult<K>> startAmountPass(
            ParallelPlannerPool pool,
            ParallelPlanGraph<K> graph,
            ParallelPlanGraph.BuildMetrics graphMetrics,
            long queueWaitNanos,
            boolean graphCacheHit) {
        return ParallelAmountPlanner.planAsync(
                        pool,
                        graph,
                        request,
                        cancellation)
                .thenApply(amount -> ParallelPlanResult.completed(
                        amount.blueprint(),
                        ParallelPlanMetrics.completed(
                                graphMetrics,
                                amount,
                                queueWaitNanos,
                                graphCacheHit)));
    }

    private static ParallelPlanGraph.BuildMetrics cachedGraphMetrics() {
        return new ParallelPlanGraph.BuildMetrics(
                0,
                0L,
                0,
                new int[ParallelPlannerPool.PARALLELISM],
                0L);
    }

    void cancel() {
        cancellation.cancel();
        state.updateAndGet(current -> current == State.COMPLETED ? current : State.CANCELLED);
    }

    State state() {
        return state.get();
    }

    private static <T> CompletableFuture<T> failedFuture(Throwable failure) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(failure);
        return future;
    }

    enum State {
        QUEUED,
        GRAPH_BUILDING,
        AMOUNT_RUNNING,
        COMPLETED,
        CANCELLED,
        FAILED
    }
}
