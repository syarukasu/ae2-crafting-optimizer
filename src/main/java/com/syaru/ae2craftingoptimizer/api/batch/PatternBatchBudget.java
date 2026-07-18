package com.syaru.ae2craftingoptimizer.api.batch;

/**
 * 一回のAdapter commitに対する不変の操作数・実時間境界。
 *
 * <p>機械へ複数回操作するAdapterは、各操作の間に{@link #canStartAnother(long)}を確認する。
 * 原子的なNative Queue投入は{@link #maximumExecutions()}を直接使用できるが、
 * Adapter自身も一回の呼び出し時間を制限しなければならない。</p>
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
