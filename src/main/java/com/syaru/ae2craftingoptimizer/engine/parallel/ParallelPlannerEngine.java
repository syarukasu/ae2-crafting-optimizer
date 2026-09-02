package com.syaru.ae2craftingoptimizer.engine.parallel;

import com.syaru.ae2craftingoptimizer.engine.PlanningCancellationToken;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/** active sessionを一件に限定し、後続をbounded FIFOで保持するPlanner入口。 */
public final class ParallelPlannerEngine implements AutoCloseable {
    /** 一gridの短いburstを保持しつつSnapshot pinを無制限化しない固定待機件数。 */
    public static final int MAXIMUM_QUEUED_SESSIONS = 64;

    private final Object lock = new Object();
    private final ParallelPlannerPool pool;
    private final ArrayDeque<QueuedSession<?>> queue = new ArrayDeque<>();
    private QueuedSession<?> active;
    private boolean accepting = true;

    public ParallelPlannerEngine() {
        this(new ParallelPlannerPool());
    }

    ParallelPlannerEngine(ParallelPlannerPool pool) {
        this.pool = Objects.requireNonNull(pool, "pool");
    }

    public <K> Future<ParallelPlanResult<K>> submit(ParallelPlanRequest<K> request) {
        Objects.requireNonNull(request, "request");
        QueuedSession<K> queued = new QueuedSession<>(request, this::cancel);
        boolean startNow = false;
        synchronized (lock) {
            if (!accepting) {
                queued.future.complete(ParallelPlanResult.rejected(ParallelPlanFailure.SHUTDOWN));
                return queued.future;
            }
            if (active == null) {
                active = queued;
                startNow = true;
            } else if (queue.size() < MAXIMUM_QUEUED_SESSIONS) {
                queue.addLast(queued);
            } else {
                queued.future.complete(ParallelPlanResult.rejected(ParallelPlanFailure.QUEUE_FULL));
            }
        }
        if (startNow) {
            start(queued);
        }
        return queued.future;
    }

    public int queuedSessionCount() {
        synchronized (lock) {
            return queue.size();
        }
    }

    public boolean hasActiveSession() {
        synchronized (lock) {
            return active != null;
        }
    }

    public int plannerParallelism() {
        return pool.parallelism();
    }

    @Override
    public void close() {
        List<QueuedSession<?>> cancelled = new ArrayList<>();
        synchronized (lock) {
            if (!accepting) {
                return;
            }
            accepting = false;
            if (active != null) {
                cancelled.add(active);
                active = null;
            }
            cancelled.addAll(queue);
            queue.clear();
        }
        for (QueuedSession<?> session : cancelled) {
            session.session.cancel();
            session.future.complete(ParallelPlanResult.rejected(ParallelPlanFailure.SHUTDOWN));
        }
        pool.close();
    }

    private <K> void start(QueuedSession<K> queued) {
        long queueWaitNanos = Math.max(0L, System.nanoTime() - queued.enqueuedAtNanos);
        queued.session.start(pool, queueWaitNanos).whenComplete((result, failure) -> {
            if (!queued.future.isCancelled()) {
                if (failure == null) {
                    queued.future.complete(result);
                } else if (queued.session.state() == ParallelPlanSession.State.CANCELLED) {
                    queued.future.complete(ParallelPlanResult.rejected(ParallelPlanFailure.CANCELLED));
                } else {
                    queued.future.completeExceptionally(failure);
                }
            }
            finish(queued);
        });
    }

    private void finish(QueuedSession<?> completed) {
        QueuedSession<?> next = null;
        synchronized (lock) {
            if (active == completed) {
                active = null;
            }
            if (accepting && active == null) {
                next = queue.pollFirst();
                active = next;
            }
        }
        if (next != null) {
            startUnchecked(next);
        }
    }

    private void cancel(QueuedSession<?> cancelled) {
        boolean wasQueued;
        synchronized (lock) {
            wasQueued = queue.remove(cancelled);
            if (!wasQueued && active != cancelled) {
                return;
            }
        }
        cancelled.session.cancel();
        if (wasQueued) {
            cancelled.future.complete(ParallelPlanResult.rejected(ParallelPlanFailure.CANCELLED));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void startUnchecked(QueuedSession<?> queued) {
        start((QueuedSession) queued);
    }

    private static final class QueuedSession<K> {
        private final long enqueuedAtNanos = System.nanoTime();
        private final ParallelPlanSession<K> session;
        private final SessionFuture<K> future;

        private QueuedSession(
                ParallelPlanRequest<K> request,
                java.util.function.Consumer<QueuedSession<?>> cancellation) {
            PlanningCancellationToken token = new PlanningCancellationToken();
            this.session = new ParallelPlanSession<>(request, token);
            this.future = new SessionFuture<>(() -> cancellation.accept(this));
        }
    }

    private static final class SessionFuture<K> extends CompletableFuture<ParallelPlanResult<K>> {
        private final Runnable cancellation;

        private SessionFuture(Runnable cancellation) {
            this.cancellation = cancellation;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(false);
            if (cancelled) {
                cancellation.run();
            }
            return cancelled;
        }
    }
}
