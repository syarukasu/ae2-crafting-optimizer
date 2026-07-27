package com.syaru.ae2craftingoptimizer.engine.craftingtable;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 保存済みbefore/afterと現在値を照合し、まだ適用していないキーだけを返す。
 *
 * <p>数量ではなく境界キー数だけを走査する。現在値がどちらにも一致しないキーは、
 * 外部変更と途中変更を区別できないため推測せず不一致として返す。</p>
 */
final class ExactMutationReconciler {
    private ExactMutationReconciler() {
    }

    static <K> Optional<Map<K, BigInteger>> remainingAmounts(
            Map<K, BigInteger> before,
            Map<K, BigInteger> after,
            Map<K, BigInteger> current) {
        Map<K, BigInteger> checkedBefore =
                Objects.requireNonNull(
                        before,
                        "before");
        Map<K, BigInteger> checkedAfter =
                Objects.requireNonNull(
                        after,
                        "after");
        Map<K, BigInteger> checkedCurrent =
                Objects.requireNonNull(
                        current,
                        "current");
        // 三つのSnapshotが同じ境界キーを持たなければ安全な差分を作れない。
        if (!checkedBefore.keySet()
                        .equals(
                                checkedAfter.keySet())
                || !checkedBefore.keySet()
                        .equals(
                                checkedCurrent.keySet())) {
            return Optional.empty();
        }

        Map<K, BigInteger> remaining =
                new LinkedHashMap<>();
        // 各キーをbefore、afterのどちらかへ厳密に分類する。
        for (Map.Entry<K, BigInteger> entry :
                checkedBefore.entrySet()) {
            K key =
                    Objects.requireNonNull(
                            entry.getKey(),
                            "mutation key");
            BigInteger beforeAmount =
                    Objects.requireNonNull(
                            entry.getValue(),
                            "before amount");
            BigInteger afterAmount =
                    Objects.requireNonNull(
                            checkedAfter.get(
                                    key),
                            "after amount");
            BigInteger currentAmount =
                    Objects.requireNonNull(
                            checkedCurrent.get(
                                    key),
                            "current amount");
            // beforeのキーだけが、次の再試行で適用すべき残量になる。
            if (currentAmount.equals(
                    beforeAmount)) {
                BigInteger delta =
                        afterAmount.subtract(
                                        beforeAmount)
                                .abs();
                // beforeとafterが同量なら、保存された変更量が壊れている。
                if (delta.signum() == 0) {
                    return Optional.empty();
                }
                remaining.put(
                        key,
                        delta);
                continue;
            }
            // afterなら既に適用済みなので、同じキーをもう一度変更しない。
            if (currentAmount.equals(
                    afterAmount)) {
                continue;
            }
            // どちらでもない現在値は、第三者変更または証明不能な部分変更として拒否する。
            return Optional.empty();
        }
        return Optional.of(
                Collections.unmodifiableMap(
                        new LinkedHashMap<>(
                                remaining)));
    }
}
