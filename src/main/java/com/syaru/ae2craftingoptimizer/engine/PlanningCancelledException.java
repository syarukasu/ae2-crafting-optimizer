package com.syaru.ae2craftingoptimizer.engine;

public final class PlanningCancelledException extends RuntimeException {
    private final int expandedRequests;

    public PlanningCancelledException(int expandedRequests) {
        super("crafting calculation was cancelled after " + expandedRequests + " expanded requests");
        this.expandedRequests = expandedRequests;
    }

    public int expandedRequests() {
        return expandedRequests;
    }
}
