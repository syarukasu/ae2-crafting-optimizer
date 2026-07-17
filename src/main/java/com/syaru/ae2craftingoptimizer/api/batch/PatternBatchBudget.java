package com.syaru.ae2craftingoptimizer.api.batch;

/**
 * Immutable operation and wall-clock boundary for one adapter commit.
 *
 * <p>Adapters that perform more than one machine-facing operation must check
 * {@link #canStartAnother(long)} between operations. An atomic native queue submission may use
 * {@link #maximumExecutions()} directly, but must keep its own call bounded.</p>
 */
public record PatternBatchBudget(long maximumExecutions, long deadlineNanos) {
    public PatternBatchBudget {
        if (maximumExecutions <= 0L) {
            throw new IllegalArgumentException("maximumExecutions must be positive");
        }
        if (deadlineNanos <= 0L) {
            throw new IllegalArgumentException("deadlineNanos must be positive");
        }
    }

    public boolean hasTimeRemaining() {
        return deadlineNanos == Long.MAX_VALUE || System.nanoTime() < deadlineNanos;
    }

    public boolean canStartAnother(long acceptedExecutions) {
        return acceptedExecutions >= 0L
                && acceptedExecutions < maximumExecutions
                && hasTimeRemaining();
    }
}
