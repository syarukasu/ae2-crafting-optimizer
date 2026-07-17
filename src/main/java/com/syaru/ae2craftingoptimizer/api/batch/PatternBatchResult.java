package com.syaru.ae2craftingoptimizer.api.batch;

public record PatternBatchResult(long acceptedExecutions) {
    public PatternBatchResult {
        if (acceptedExecutions < 0L) {
            throw new IllegalArgumentException("acceptedExecutions must not be negative");
        }
    }

    public static PatternBatchResult none() {
        return new PatternBatchResult(0L);
    }

    public static PatternBatchResult accepted(long executions) {
        return new PatternBatchResult(executions);
    }
}
