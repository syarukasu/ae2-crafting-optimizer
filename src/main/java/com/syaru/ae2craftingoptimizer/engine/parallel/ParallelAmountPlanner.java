package com.syaru.ae2craftingoptimizer.engine.parallel;

import com.syaru.ae2craftingoptimizer.engine.BigCountMath;
import com.syaru.ae2craftingoptimizer.engine.CheckedLongMath;
import com.syaru.ae2craftingoptimizer.engine.CompiledPattern;
import com.syaru.ae2craftingoptimizer.engine.CountOverflowException;
import com.syaru.ae2craftingoptimizer.engine.PlanningCancellationToken;
import com.syaru.ae2craftingoptimizer.engine.PlanningCancelledException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReference;

/** 一つのcanonical Graphの需要伝播を、edge-local contributionで最大4本に分割する。 */
final class ParallelAmountPlanner {
    /** 既存plannerと同じ64 node単位で協調cancelを観測する。 */
    private static final int CANCELLATION_CHECK_INTERVAL_MASK = 63;
    private static final int SIGNED_LONG_MAGNITUDE_BITS = Long.SIZE - 1;

    private ParallelAmountPlanner() {
    }

    static <K> CompletableFuture<AmountOutcome<K>> planAsync(
            ParallelPlannerPool pool,
            ParallelPlanGraph<K> graph,
            ParallelPlanRequest<K> request,
            PlanningCancellationToken cancellation) {
        Objects.requireNonNull(pool, "pool");
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(cancellation, "cancellation");
        BigInteger[] exactInventory = captureInventory(graph, request);

        if (fitsLong(request.requestedAmount(), exactInventory)) {
            long[] longInventory = new long[exactInventory.length];
            for (int node = 0; node < exactInventory.length; node++) {
                longInventory[node] = exactInventory[node].longValueExact();
            }
            CompletableFuture<LongPassResult> longPass = new LongPassState<>(
                    pool,
                    graph,
                    request.requestedAmount().longValueExact(),
                    longInventory,
                    cancellation).start();
            return longPass.handle((result, failure) -> {
                Throwable cause = unwrap(failure);
                if (cause == null) {
                    return CompletableFuture.completedFuture(fromLong(graph, request, result));
                }
                if (!(cause instanceof CountOverflowException)) {
                    return ParallelAmountPlanner.<AmountOutcome<K>>failedFuture(cause);
                }
                return startBig(pool, graph, request, exactInventory, cancellation, true);
            }).thenCompose(future -> future);
        }
        return startBig(pool, graph, request, exactInventory, cancellation, false);
    }

    private static <K> CompletableFuture<AmountOutcome<K>> startBig(
            ParallelPlannerPool pool,
            ParallelPlanGraph<K> graph,
            ParallelPlanRequest<K> request,
            BigInteger[] exactInventory,
            PlanningCancellationToken cancellation,
            boolean promotedFromLong) {
        return new BigPassState<>(
                pool,
                graph,
                request.requestedAmount(),
                exactInventory,
                request.maximumBits(),
                cancellation)
                .start()
                .thenApply(result -> fromBig(graph, request, result, promotedFromLong));
    }

    private static <K> AmountOutcome<K> fromLong(
            ParallelPlanGraph<K> graph,
            ParallelPlanRequest<K> request,
            LongPassResult result) {
        BigInteger[] patternExecutions = widen(result.patternExecutions);
        BigInteger[] contributions = widen(result.edgeContributions);
        BigInteger exactBytes = ParallelExactByteCounter.calculate(
                graph,
                request.requestedAmount(),
                patternExecutions,
                contributions,
                request.amountPerByte(),
                request.maximumBits());
        ParallelPlanBlueprint<K> blueprint = blueprint(
                graph,
                request,
                patternExecutions,
                widen(result.used),
                widen(result.terminalRemainders),
                exactBytes,
                ParallelPlanBlueprint.ArithmeticMode.CHECKED_LONG);
        return new AmountOutcome<>(blueprint, result.metrics, false);
    }

    private static <K> AmountOutcome<K> fromBig(
            ParallelPlanGraph<K> graph,
            ParallelPlanRequest<K> request,
            BigPassResult result,
            boolean promotedFromLong) {
        BigInteger exactBytes = ParallelExactByteCounter.calculate(
                graph,
                request.requestedAmount(),
                result.patternExecutions,
                result.edgeContributions,
                request.amountPerByte(),
                request.maximumBits());
        ParallelPlanBlueprint<K> blueprint = blueprint(
                graph,
                request,
                result.patternExecutions,
                result.used,
                result.terminalRemainders,
                exactBytes,
                ParallelPlanBlueprint.ArithmeticMode.BIG_INTEGER);
        return new AmountOutcome<>(blueprint, result.metrics, promotedFromLong);
    }

    private static <K> ParallelPlanBlueprint<K> blueprint(
            ParallelPlanGraph<K> graph,
            ParallelPlanRequest<K> request,
            BigInteger[] patternExecutions,
            BigInteger[] used,
            BigInteger[] terminalRemainders,
            BigInteger exactBytes,
            ParallelPlanBlueprint.ArithmeticMode arithmeticMode) {
        Map<String, BigInteger> patterns = new LinkedHashMap<>();
        Map<K, BigInteger> usedInventory = new LinkedHashMap<>();
        Map<K, BigInteger> emitted = new LinkedHashMap<>();
        Map<K, BigInteger> missing = new LinkedHashMap<>();
        for (int node = 0; node < graph.nodeCount(); node++) {
            BigInteger executions = zero(patternExecutions[node]);
            CompiledPattern<K> pattern = graph.patternAt(node);
            if (executions.signum() > 0 && pattern != null) {
                patterns.merge(
                        pattern.id(),
                        executions,
                        (left, right) -> BigCountMath.add(
                                left,
                                right,
                                "parallel/result/pattern",
                                request.maximumBits()));
            }
            BigInteger usedAmount = zero(used[node]);
            if (usedAmount.signum() > 0) {
                usedInventory.put(graph.keyAt(node), usedAmount);
            }
            BigInteger remainder = zero(terminalRemainders[node]);
            if (remainder.signum() == 0) {
                continue;
            }
            if (graph.isEmittableAt(node)) {
                emitted.put(graph.keyAt(node), remainder);
            } else {
                missing.put(graph.keyAt(node), remainder);
            }
        }
        return new ParallelPlanBlueprint<>(
                graph.root(),
                request.requestedAmount(),
                exactBytes,
                patterns,
                usedInventory,
                emitted,
                missing,
                arithmeticMode,
                graph.hasTreeExactByteShape(),
                graph.nodeCount(),
                request.revisions());
    }

    private static <K> BigInteger[] captureInventory(
            ParallelPlanGraph<K> graph,
            ParallelPlanRequest<K> request) {
        BigInteger[] inventory = new BigInteger[graph.nodeCount()];
        for (int node = 0; node < inventory.length; node++) {
            BigInteger amount = node == graph.rootIndex()
                    ? BigInteger.ZERO
                    : request.inventory().getOrDefault(
                            graph.keyAt(node),
                            BigInteger.ZERO);
            inventory[node] = BigCountMath.requireMaximumBits(
                    amount,
                    "parallel/inventory/" + node,
                    request.maximumBits());
        }
        return inventory;
    }

    private static boolean fitsLong(BigInteger requested, BigInteger[] inventory) {
        if (requested.bitLength() > SIGNED_LONG_MAGNITUDE_BITS) {
            return false;
        }
        for (BigInteger amount : inventory) {
            if (amount.bitLength() > SIGNED_LONG_MAGNITUDE_BITS) {
                return false;
            }
        }
        return true;
    }

    private static BigInteger[] widen(long[] source) {
        BigInteger[] result = new BigInteger[source.length];
        for (int index = 0; index < source.length; index++) {
            if (source[index] != 0L) {
                result[index] = BigInteger.valueOf(source[index]);
            }
        }
        return result;
    }

    private static Throwable unwrap(Throwable failure) {
        if (failure instanceof CompletionException completion && completion.getCause() != null) {
            return completion.getCause();
        }
        return failure;
    }

    private static <T> CompletableFuture<T> failedFuture(Throwable failure) {
        CompletableFuture<T> result = new CompletableFuture<>();
        result.completeExceptionally(failure);
        return result;
    }

    private static BigInteger zero(BigInteger value) {
        return value == null ? BigInteger.ZERO : value;
    }

    record AmountMetrics(
            int processedNodes,
            int maximumActiveWorkers,
            int[] nodesByWorker,
            long elapsedNanos) {
        AmountMetrics {
            nodesByWorker = nodesByWorker.clone();
        }

        @Override
        public int[] nodesByWorker() {
            return nodesByWorker.clone();
        }

        int workersUsed() {
            int used = 0;
            for (int count : nodesByWorker) {
                if (count > 0) {
                    used++;
                }
            }
            return used;
        }
    }

    record AmountOutcome<K>(
            ParallelPlanBlueprint<K> blueprint,
            AmountMetrics metrics,
            boolean promotedFromLong) {
        AmountOutcome {
            Objects.requireNonNull(blueprint, "blueprint");
            Objects.requireNonNull(metrics, "metrics");
        }
    }

    private abstract static class PassState<K, R> {
        final ParallelPlannerPool pool;
        final ParallelPlanGraph<K> graph;
        final PlanningCancellationToken cancellation;
        final AtomicInteger remainingWorkers = new AtomicInteger(ParallelPlannerPool.PARALLELISM);
        final AtomicInteger processed = new AtomicInteger();
        final AtomicInteger activeWorkers = new AtomicInteger();
        final AtomicInteger maximumActiveWorkers = new AtomicInteger();
        final AtomicIntegerArray nodesByWorker =
                new AtomicIntegerArray(ParallelPlannerPool.PARALLELISM);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final AtomicInteger frontierPhase = new AtomicInteger();
        final AtomicInteger frontierArrivals = new AtomicInteger();
        final CompletableFuture<R> result = new CompletableFuture<>();
        final long startedNanos = System.nanoTime();

        PassState(
                ParallelPlannerPool pool,
                ParallelPlanGraph<K> graph,
                PlanningCancellationToken cancellation) {
            this.pool = pool;
            this.graph = graph;
            this.cancellation = cancellation;
        }

        final CompletableFuture<R> start() {
            pool.executeSessionWorkers(this::runWorker);
            return result;
        }

        private void runWorker(int workerId) {
            try {
                for (int frontier = 0; frontier < graph.frontierCount(); frontier++) {
                    if (failure.get() != null) {
                        break;
                    }
                    processFrontier(workerId, graph.frontierAt(frontier));
                    if (!awaitFrontier(frontier)) {
                        break;
                    }
                }
            } catch (RuntimeException | Error thrown) {
                failure.compareAndSet(null, thrown);
            } finally {
                if (remainingWorkers.decrementAndGet() == 0) {
                    finish();
                }
            }
        }

        private void processFrontier(int workerId, int[] frontier) {
            int localProcessed = 0;
            int first = (int) ((long) frontier.length * workerId
                    / ParallelPlannerPool.PARALLELISM);
            int limit = (int) ((long) frontier.length * (workerId + 1)
                    / ParallelPlannerPool.PARALLELISM);
            boolean active = first < limit;
            if (active) {
                int activeCount = activeWorkers.incrementAndGet();
                maximumActiveWorkers.accumulateAndGet(activeCount, Math::max);
            }
            try {
                for (int offset = first; offset < limit; offset++) {
                    if ((localProcessed & CANCELLATION_CHECK_INTERVAL_MASK) == 0) {
                        cancellation.checkpoint(processed.get() + localProcessed);
                    }
                    processNode(frontier[offset]);
                    localProcessed++;
                }
            } finally {
                if (localProcessed != 0) {
                    nodesByWorker.addAndGet(workerId, localProcessed);
                    processed.addAndGet(localProcessed);
                }
                if (active) {
                    activeWorkers.decrementAndGet();
                }
            }
        }

        private boolean awaitFrontier(int expectedPhase) {
            if (failure.get() != null) {
                return false;
            }
            int arrivals = frontierArrivals.incrementAndGet();
            if (arrivals == ParallelPlannerPool.PARALLELISM) {
                frontierArrivals.set(0);
                frontierPhase.incrementAndGet();
                return true;
            }
            while (frontierPhase.get() == expectedPhase) {
                if (failure.get() != null) {
                    return false;
                }
                cancellation.checkpoint(processed.get());
                Thread.onSpinWait();
            }
            return failure.get() == null;
        }

        private void finish() {
            Throwable thrown = failure.get();
            if (thrown != null) {
                result.completeExceptionally(thrown);
                return;
            }
            if (processed.get() != graph.nodeCount()) {
                result.completeExceptionally(new IllegalStateException(
                        "parallel amount pass did not process every graph node"));
                return;
            }
            try {
                result.complete(createResult(metrics()));
            } catch (RuntimeException | Error resultFailure) {
                result.completeExceptionally(resultFailure);
            }
        }

        private AmountMetrics metrics() {
            int[] counts = new int[ParallelPlannerPool.PARALLELISM];
            for (int worker = 0; worker < counts.length; worker++) {
                counts[worker] = nodesByWorker.get(worker);
            }
            return new AmountMetrics(
                    processed.get(),
                    maximumActiveWorkers.get(),
                    counts,
                    System.nanoTime() - startedNanos);
        }

        abstract void processNode(int node);

        abstract R createResult(AmountMetrics metrics);
    }

    private static final class LongPassState<K> extends PassState<K, LongPassResult> {
        private final long requestedAmount;
        private final long[] inventory;
        private final long[] edgeContributions;
        private final long[] patternExecutions;
        private final long[] used;
        private final long[] terminalRemainders;

        private LongPassState(
                ParallelPlannerPool pool,
                ParallelPlanGraph<K> graph,
                long requestedAmount,
                long[] inventory,
                PlanningCancellationToken cancellation) {
            super(pool, graph, cancellation);
            this.requestedAmount = requestedAmount;
            this.inventory = inventory;
            this.edgeContributions = new long[graph.edgeCount()];
            this.patternExecutions = new long[graph.nodeCount()];
            this.used = new long[graph.nodeCount()];
            this.terminalRemainders = new long[graph.nodeCount()];
        }

        @Override
        void processNode(int node) {
            long required = node == graph.rootIndex() ? requestedAmount : 0L;
            for (int edge : graph.incomingEdgesAt(node)) {
                required = CheckedLongMath.addIndexed(
                        required,
                        edgeContributions[edge],
                        "parallel/demand",
                        node);
            }
            if (required == 0L) {
                return;
            }
            long taken = Math.min(required, inventory[node]);
            used[node] = taken;
            long deficit = required - taken;
            if (deficit == 0L) {
                return;
            }
            if (graph.isEmittableAt(node) || graph.patternAt(node) == null) {
                terminalRemainders[node] = deficit;
                return;
            }
            long executions = CheckedLongMath.ceilDivIndexed(
                    deficit,
                    graph.outputAmountAt(node),
                    "parallel/executions",
                    node);
            patternExecutions[node] = executions;
            for (int slot = graph.firstInput(node); slot < graph.inputLimit(node); slot++) {
                int selected = selectLongAlternative(graph, slot, executions, inventory);
                edgeContributions[selected] = CheckedLongMath.multiplyIndexed(
                        graph.amountAtEdge(selected),
                        executions,
                        "parallel/input",
                        selected);
            }
        }

        @Override
        LongPassResult createResult(AmountMetrics metrics) {
            return new LongPassResult(
                    patternExecutions,
                    used,
                    terminalRemainders,
                    edgeContributions,
                    metrics);
        }
    }

    private static final class BigPassState<K> extends PassState<K, BigPassResult> {
        private final BigInteger requestedAmount;
        private final BigInteger[] inventory;
        private final int maximumBits;
        private final BigInteger[] edgeContributions;
        private final BigInteger[] patternExecutions;
        private final BigInteger[] used;
        private final BigInteger[] terminalRemainders;

        private BigPassState(
                ParallelPlannerPool pool,
                ParallelPlanGraph<K> graph,
                BigInteger requestedAmount,
                BigInteger[] inventory,
                int maximumBits,
                PlanningCancellationToken cancellation) {
            super(pool, graph, cancellation);
            this.requestedAmount = requestedAmount;
            this.inventory = inventory;
            this.maximumBits = maximumBits;
            this.edgeContributions = new BigInteger[graph.edgeCount()];
            this.patternExecutions = new BigInteger[graph.nodeCount()];
            this.used = new BigInteger[graph.nodeCount()];
            this.terminalRemainders = new BigInteger[graph.nodeCount()];
        }

        @Override
        void processNode(int node) {
            BigInteger required = node == graph.rootIndex() ? requestedAmount : BigInteger.ZERO;
            for (int edge : graph.incomingEdgesAt(node)) {
                required = BigCountMath.add(
                        required,
                        zero(edgeContributions[edge]),
                        "parallel/big-demand/" + node,
                        maximumBits);
            }
            if (required.signum() == 0) {
                return;
            }
            BigInteger taken = required.min(inventory[node]);
            used[node] = taken;
            BigInteger deficit = required.subtract(taken);
            if (deficit.signum() == 0) {
                return;
            }
            if (graph.isEmittableAt(node) || graph.patternAt(node) == null) {
                terminalRemainders[node] = deficit;
                return;
            }
            BigInteger executions = BigCountMath.requireMaximumBits(
                    BigCountMath.ceilDiv(
                            deficit,
                            BigInteger.valueOf(graph.outputAmountAt(node)),
                            "parallel/big-executions/" + node),
                    "parallel/big-executions/" + node,
                    maximumBits);
            patternExecutions[node] = executions;
            for (int slot = graph.firstInput(node); slot < graph.inputLimit(node); slot++) {
                int selected = selectBigAlternative(
                        graph,
                        slot,
                        executions,
                        inventory,
                        maximumBits);
                edgeContributions[selected] = BigCountMath.multiply(
                        BigInteger.valueOf(graph.amountAtEdge(selected)),
                        executions,
                        "parallel/big-input/" + selected,
                        maximumBits);
            }
        }

        @Override
        BigPassResult createResult(AmountMetrics metrics) {
            return new BigPassResult(
                    patternExecutions,
                    used,
                    terminalRemainders,
                    edgeContributions,
                    metrics);
        }
    }

    private static <K> int selectLongAlternative(
            ParallelPlanGraph<K> graph,
            int slot,
            long executions,
            long[] inventory) {
        int first = graph.firstAlternative(slot);
        int limit = graph.alternativeLimit(slot);
        if (limit - first == 1) {
            return first;
        }
        int selected = -1;
        int selectedRank = Integer.MAX_VALUE;
        CountOverflowException firstOverflow = null;
        for (int edge = first; edge < limit; edge++) {
            long required;
            try {
                required = CheckedLongMath.multiplyIndexed(
                        graph.amountAtEdge(edge),
                        executions,
                        "parallel/alternative",
                        edge);
            } catch (CountOverflowException overflow) {
                if (firstOverflow == null) {
                    firstOverflow = overflow;
                }
                continue;
            }
            int child = graph.childAtEdge(edge);
            int rank = alternativeRank(graph, child, required, inventory[child]);
            if (rank < selectedRank) {
                selected = edge;
                selectedRank = rank;
            }
        }
        if (selected >= 0) {
            return selected;
        }
        if (firstOverflow != null) {
            throw firstOverflow;
        }
        throw new IllegalStateException("parallel input slot has no long alternative");
    }

    private static <K> int selectBigAlternative(
            ParallelPlanGraph<K> graph,
            int slot,
            BigInteger executions,
            BigInteger[] inventory,
            int maximumBits) {
        int selected = -1;
        int selectedRank = Integer.MAX_VALUE;
        IllegalArgumentException firstOverflow = null;
        for (int edge = graph.firstAlternative(slot);
                edge < graph.alternativeLimit(slot);
                edge++) {
            BigInteger required;
            try {
                required = BigCountMath.multiply(
                        BigInteger.valueOf(graph.amountAtEdge(edge)),
                        executions,
                        "parallel/big-alternative/" + edge,
                        maximumBits);
            } catch (IllegalArgumentException overflow) {
                if (firstOverflow == null) {
                    firstOverflow = overflow;
                }
                continue;
            }
            int child = graph.childAtEdge(edge);
            int rank = alternativeRank(graph, child, required, inventory[child]);
            if (rank < selectedRank) {
                selected = edge;
                selectedRank = rank;
            }
        }
        if (selected >= 0) {
            return selected;
        }
        if (firstOverflow != null) {
            throw firstOverflow;
        }
        throw new IllegalStateException("parallel input slot has no BigInteger alternative");
    }

    private static <K> int alternativeRank(
            ParallelPlanGraph<K> graph,
            int child,
            long required,
            long available) {
        if (available >= required) {
            return 0;
        }
        if (graph.patternAt(child) != null || graph.isEmittableAt(child)) {
            return 1;
        }
        return available > 0L ? 2 : 3;
    }

    private static <K> int alternativeRank(
            ParallelPlanGraph<K> graph,
            int child,
            BigInteger required,
            BigInteger available) {
        if (available.compareTo(required) >= 0) {
            return 0;
        }
        if (graph.patternAt(child) != null || graph.isEmittableAt(child)) {
            return 1;
        }
        return available.signum() > 0 ? 2 : 3;
    }

    private record LongPassResult(
            long[] patternExecutions,
            long[] used,
            long[] terminalRemainders,
            long[] edgeContributions,
            AmountMetrics metrics) {
    }

    private record BigPassResult(
            BigInteger[] patternExecutions,
            BigInteger[] used,
            BigInteger[] terminalRemainders,
            BigInteger[] edgeContributions,
            AmountMetrics metrics) {
    }
}
