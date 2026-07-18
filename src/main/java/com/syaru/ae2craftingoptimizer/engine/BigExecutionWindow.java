package com.syaru.ae2craftingoptimizer.engine;

import java.math.BigInteger;

public record BigExecutionWindow(BigInteger offset, long executions, BigInteger remainingAfter) {
    public BigExecutionWindow {
        BigCountMath.requireNonNegative(offset, "window/offset");
        if (executions <= 0L) {
            throw new IllegalArgumentException("window executions must be positive");
        }
        BigCountMath.requireNonNegative(remainingAfter, "window/remainingAfter");
    }

    public static BigExecutionWindow next(
            BigInteger total,
            BigInteger alreadyExecuted,
            long maximumExecutions) {
        BigCountMath.requireNonNegative(total, "window/total");
        BigCountMath.requireNonNegative(alreadyExecuted, "window/alreadyExecuted");
        if (alreadyExecuted.compareTo(total) > 0 || maximumExecutions <= 0L) {
            throw new IllegalArgumentException("invalid execution window bounds");
        }
        BigInteger remaining = total.subtract(alreadyExecuted);
        if (remaining.signum() == 0) {
            throw new IllegalStateException("execution is already complete");
        }
        long count = remaining.min(BigInteger.valueOf(maximumExecutions)).longValueExact();
        return new BigExecutionWindow(
                alreadyExecuted,
                count,
                remaining.subtract(BigInteger.valueOf(count)));
    }
}
