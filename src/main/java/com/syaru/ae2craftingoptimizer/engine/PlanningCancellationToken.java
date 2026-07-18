package com.syaru.ae2craftingoptimizer.engine;

import java.util.concurrent.atomic.AtomicBoolean;

public final class PlanningCancellationToken implements PlanningGuard {
    private final AtomicBoolean cancelled = new AtomicBoolean();

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    @Override
    public void checkpoint(int expandedRequests) {
        if (cancelled.get()) {
            throw new PlanningCancelledException(expandedRequests);
        }
    }
}
