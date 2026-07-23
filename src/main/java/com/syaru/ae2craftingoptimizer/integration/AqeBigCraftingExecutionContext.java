package com.syaru.ae2craftingoptimizer.integration;

import java.math.BigInteger;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 標準容量判定へ、現在投入中のBig子Job一件分だけを一時的に貸し出すサーバースレッド文脈。
 *
 * <p>Advanced AEへ見せる容量はlongだが、親Jobが所有する真の予約量はBigIntegerで保持する。
 * これにより、容量だけlongを超える子Windowを通常の新規巨大Jobと取り違えない。</p>
 */
public final class AqeBigCraftingExecutionContext {
    private static final ThreadLocal<Allowance> CURRENT = new ThreadLocal<>();

    private AqeBigCraftingExecutionContext() {
    }

    public static <T> T withAllowance(Object cluster, long bytes, Supplier<T> action) {
        return withAllowance(cluster, bytes, BigInteger.valueOf(bytes), action);
    }

    public static <T> T withAllowance(
            Object cluster,
            long compatibleBytes,
            BigInteger exactBytes,
            Supplier<T> action) {
        Objects.requireNonNull(cluster, "cluster");
        Objects.requireNonNull(exactBytes, "exactBytes");
        Objects.requireNonNull(action, "action");
        /*
         * compatibleBytesはAdvanced AEへ渡せる正数long、exactBytesはその真値。
         * 真値が互換値未満なら同じWindowを表していないため、提出前に拒否する。
         */
        if (compatibleBytes <= 0L
                || exactBytes.compareTo(BigInteger.valueOf(compatibleBytes)) < 0
                || CURRENT.get() != null) {
            throw new IllegalStateException("invalid or nested AQE BigInteger child allowance");
        }
        CURRENT.set(new Allowance(cluster, compatibleBytes, exactBytes));
        try {
            return action.get();
        } finally {
            CURRENT.remove();
        }
    }

    public static long allowanceFor(Object cluster) {
        Allowance allowance = CURRENT.get();
        return allowance != null && allowance.cluster() == cluster
                ? allowance.compatibleBytes()
                : 0L;
    }

    public static BigInteger exactAllowanceFor(Object cluster) {
        Allowance allowance = CURRENT.get();
        return allowance != null && allowance.cluster() == cluster
                ? allowance.exactBytes()
                : BigInteger.ZERO;
    }

    private record Allowance(
            Object cluster,
            long compatibleBytes,
            BigInteger exactBytes) {
    }
}
