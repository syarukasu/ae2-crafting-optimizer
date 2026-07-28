package com.syaru.ae2craftingoptimizer.engine.craftingtable;

import java.math.BigInteger;
import java.util.Collections;
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
                checkedCounts(
                        required,
                        "required");
        // 一キーでも不足する場合は、親レシピを物理Workerへ渡さない。
        for (Map.Entry<K, BigInteger> entry :
                checked.entrySet()) {
            // 現在量が必要量未満なら、この依存段はまだ実行不能。
            if (amount(
                            entry.getKey())
                    .compareTo(
                            entry.getValue())
                    < 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * 全入力を原子的に予約する。
     *
     * <p>全キーを先に検査するため、途中まで減算した状態で失敗しない。</p>
     */
    public void debitExact(
            Map<K, BigInteger> required) {
        Map<K, BigInteger> checked =
                checkedCounts(
                        required,
                        "required");
        // 事前検査で全キーが揃う場合だけ、後段の減算へ進む。
        if (!containsAll(
                checked)) {
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
                checkedCounts(
                        produced,
                        "produced");
        // 同じキーの中間素材と返却物は、数量だけを正確に合算する。
        for (Map.Entry<K, BigInteger> entry :
                checked.entrySet()) {
            amounts.merge(
                    entry.getKey(),
                    entry.getValue(),
                    BigInteger::add);
        }
    }

    public boolean isEmpty() {
        return amounts.isEmpty();
    }

    public Map<K, BigInteger> snapshot() {
        return Collections.unmodifiableMap(
                new LinkedHashMap<>(
                        amounts));
    }

    public void restore(
            Map<K, BigInteger> restored) {
        Map<K, BigInteger> checked =
                checkedCounts(
                        restored,
                        "restored");
        amounts.clear();
        amounts.putAll(
                checked);
    }

    private static <K> Map<K, BigInteger> checkedCounts(
            Map<K, BigInteger> source,
            String name) {
        Objects.requireNonNull(
                source,
                name);
        Map<K, BigInteger> result =
                new LinkedHashMap<>();
        // Escrowへはnull、0、負数を入れず、保存順をそのまま維持する。
        for (Map.Entry<K, BigInteger> entry :
                source.entrySet()) {
            K key =
                    Objects.requireNonNull(
                            entry.getKey(),
                            name + " key");
            BigInteger amount =
                    Objects.requireNonNull(
                            entry.getValue(),
                            name + " amount");
            // 0以下の値は「存在しないキー」と区別できないため拒否する。
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException(
                        name + " contains a non-positive amount");
            }
            result.put(
                    key,
                    amount);
        }
        return result;
    }
}
