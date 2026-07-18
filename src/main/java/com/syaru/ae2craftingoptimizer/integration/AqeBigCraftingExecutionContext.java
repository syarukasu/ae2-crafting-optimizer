package com.syaru.ae2craftingoptimizer.integration;

import java.util.Objects;
import java.util.function.Supplier;

/** 標準容量判定へ、現在投入中のBig子Job一件分だけを一時的に貸し出すサーバースレッド文脈。 */
public final class AqeBigCraftingExecutionContext {
    private static final ThreadLocal<Allowance> CURRENT = new ThreadLocal<>();

    private AqeBigCraftingExecutionContext() {
    }

    public static <T> T withAllowance(Object cluster, long bytes, Supplier<T> action) {
        Objects.requireNonNull(cluster, "cluster");
        Objects.requireNonNull(action, "action");
        if (bytes <= 0L || CURRENT.get() != null) {
            throw new IllegalStateException("invalid or nested AQE BigInteger child allowance");
        }
        CURRENT.set(new Allowance(cluster, bytes));
        try {
            return action.get();
        } finally {
            CURRENT.remove();
        }
    }

    public static long allowanceFor(Object cluster) {
        Allowance allowance = CURRENT.get();
        return allowance != null && allowance.cluster() == cluster ? allowance.bytes() : 0L;
    }

    private record Allowance(Object cluster, long bytes) {
    }
}
