package com.syaru.ae2craftingoptimizer.engine;

@FunctionalInterface
public interface PlanningGuard {
    PlanningGuard NONE = expandedRequests -> {
    };

    void checkpoint(int expandedRequests);

    static PlanningGuard none() {
        return NONE;
    }
}
