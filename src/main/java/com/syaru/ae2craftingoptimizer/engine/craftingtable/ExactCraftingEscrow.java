package com.syaru.ae2craftingoptimizer.engine.craftingtable;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 一つのクラフト注文が所有する、数量を丸めない中間在庫。
 *
 * <p>MEから予約した境界素材、物理Workerが完成させた中間素材、最終成果物を
 * 同じ台帳で扱う。数量に応じた要素数やlong Windowは作らない。</p>
 */
public final class ExactCraftingEscrow<K> {
    private final Map<K, BigInteger> amounts =
            new LinkedHashMap<>();

    public ExactCraftingEscrow() {
    }

    public ExactCraftingEscrow(
            Map<K, BigInteger> initial) {
        restore(initial);
    }

    public BigInteger amount(
            K key) {
        return amounts.getOrDefault(
                Objects.requireNonNull(
                        key,
                        "key"),
                BigInteger.ZERO);
    }

    /** 全入力を一度に引けるかを、台帳へ触らず確認する。 */
    public boolean containsAll(
            Map<K, BigInteger> required) {
        Map<K, BigInteger> checked =
                ExactCountMap.mutablePositiveCopy(
                        required,
                        "required");
        return ExactCountMap.containsAll(amounts, checked);
    }

    /**
     * 全入力を原子的に予約する。
     *
     * <p>全キーを先に検査するため、途中まで減算した状態で失敗しない。</p>
     */
    public void debitExact(
            Map<K, BigInteger> required) {
        Map<K, BigInteger> checked =
                ExactCountMap.mutablePositiveCopy(
                        required,
                        "required");
        // 事前検査で全キーが揃う場合だけ、後段の減算へ進む。
        if (!ExactCountMap.containsAll(amounts, checked)) {
            throw new IllegalStateException(
                    "crafting escrow does not contain every required input");
        }
        // 検査済み数量を一キーずつ引き、0量キーは台帳から除く。
        for (Map.Entry<K, BigInteger> entry :
                checked.entrySet()) {
            BigInteger remaining =
                    amount(
                                    entry.getKey())
                            .subtract(
                                    entry.getValue());
            // 使い切ったキーは残量0としてMapへ保持しない。
            if (remaining.signum() == 0) {
                amounts.remove(
                        entry.getKey());
            } else {
                amounts.put(
                        entry.getKey(),
                        remaining);
            }
        }
    }

    /** 物理Workerが確定した出力をキー別BigIntegerのまま加算する。 */
    public void credit(
            Map<K, BigInteger> produced) {
        Map<K, BigInteger> checked =
                ExactCountMap.mutablePositiveCopy(
                        produced,
                        "produced");
        // 同じキーの中間素材と返却物は、数量だけを正確に合算する。
        for (Map.Entry<K, BigInteger> entry :
                checked.entrySet()) {
            ExactCountMap.mergePositive(
                    amounts,
                    entry.getKey(),
                    entry.getValue());
        }
    }

    public boolean isEmpty() {
        return amounts.isEmpty();
    }

    public Map<K, BigInteger> snapshot() {
        return ExactCountMap.immutableOrderedCopy(amounts);
    }

    public void restore(
            Map<K, BigInteger> restored) {
        Map<K, BigInteger> checked =
                ExactCountMap.mutablePositiveCopy(
                        restored,
                        "restored");
        amounts.clear();
        amounts.putAll(
                checked);
    }
}
