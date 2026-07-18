package com.syaru.ae2craftingoptimizer.engine;

public final class StalePlanningSnapshotException extends RuntimeException {
    private final PlanningGenerationSnapshot snapshot;
    private final int expandedRequests;

    public StalePlanningSnapshotException(
            PlanningGenerationSnapshot snapshot,
            int expandedRequests) {
        super("crafting calculation snapshot became stale after " + expandedRequests + " expanded requests");
        this.snapshot = snapshot;
        this.expandedRequests = expandedRequests;
    }

    public PlanningGenerationSnapshot snapshot() {
        return snapshot;
    }

    public int expandedRequests() {
        return expandedRequests;
    }
}
