package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** BigInteger正本を変更せず、AE2のlong固定表示境界へ投影する共通処理。 */
final class BigIntegerPlanProjection {
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private BigIntegerPlanProjection() {
    }

    static KeyCounter projectKeyCounter(Map<AEKey, BigInteger> exact) {
        KeyCounter projected = new KeyCounter();
        // AE2の表示用Counterだけを作り、BigInteger Mapを正本として保持する。
        exact.forEach((key, amount) -> projected.add(key, saturatedLong(amount)));
        return projected;
    }

    static Map<IPatternDetails, Long> projectPatternCounter(
            Map<IPatternDetails, BigInteger> exact) {
        Map<IPatternDetails, Long> projected = new LinkedHashMap<>();
        // 画面互換のPattern回数だけを飽和し、正確な回数はSidecarへ残す。
        exact.forEach((pattern, amount) -> projected.put(pattern, saturatedLong(amount)));
        return Map.copyOf(projected);
    }

    static long saturatedLong(BigInteger amount) {
        Objects.requireNonNull(amount, "amount");
        // long範囲を超える値を負数へwrapさせず、画面互換の上限へ飽和する。
        if (exceedsLong(amount)) {
            return Long.MAX_VALUE;
        }
        return amount.longValueExact();
    }

    static boolean exceedsLong(BigInteger amount) {
        Objects.requireNonNull(amount, "amount");
        return amount.compareTo(LONG_MAX) > 0;
    }

    static <K> Map<K, BigInteger> immutablePositiveCounts(
            Map<K, BigInteger> counts,
            String name) {
        Map<K, BigInteger> copy = new LinkedHashMap<>();
        Objects.requireNonNull(counts, name).forEach((key, amount) -> {
            Objects.requireNonNull(key, name + " key");
            BigCountMath.requireNonNegative(amount, name);
            // 0以下の値は実行・表示会計へ含められないため拒否する。
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException(name + " values must be positive");
            }
            copy.put(key, amount);
        });
        return Map.copyOf(copy);
    }
}
