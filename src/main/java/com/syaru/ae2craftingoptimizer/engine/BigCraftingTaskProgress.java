package com.syaru.ae2craftingoptimizer.engine;

import java.math.BigInteger;

public final class BigCraftingTaskProgress {
    private final BigInteger total;
    private BigInteger completed = BigInteger.ZERO;

    public BigCraftingTaskProgress(BigInteger total) {
        this(total, BigInteger.ZERO);
    }

    public BigCraftingTaskProgress(BigInteger total, BigInteger completed) {
        this.total = BigCountMath.requireNonNegative(total, "task/total");
        this.completed = BigCountMath.requireNonNegative(completed, "task/completed");
        if (this.completed.compareTo(this.total) > 0) {
            throw new IllegalArgumentException("completed task count exceeds total");
        }
    }

    public synchronized BigExecutionWindow nextWindow(long maximumExecutions) {
        return BigExecutionWindow.next(total, completed, maximumExecutions);
    }

    public synchronized void complete(long executions) {
        if (executions <= 0L) {
            throw new IllegalArgumentException("completed executions must be positive");
        }
        BigInteger next = completed.add(BigInteger.valueOf(executions));
        if (next.compareTo(total) > 0) {
            throw new IllegalArgumentException("cannot complete more executions than the task total");
        }
        completed = next;
    }

    public BigInteger total() {
        return total;
    }

    public synchronized BigInteger completed() {
        return completed;
    }

    public synchronized BigInteger remaining() {
        return total.subtract(completed);
    }

    public synchronized boolean isComplete() {
        return completed.equals(total);
    }
}
