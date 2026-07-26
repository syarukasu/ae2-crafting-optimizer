package com.syaru.ae2craftingoptimizer.engine.vector;

import appeng.api.stacks.AEKey;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Vector計画が参照したキーだけを保持する不変の正確在庫Snapshot。 */
public record VectorInventorySnapshot(
        Map<AEKey, BigInteger> amounts,
        boolean complete) {
    public VectorInventorySnapshot {
        Objects.requireNonNull(amounts, "amounts");
        Map<AEKey, BigInteger> checked = new LinkedHashMap<>();
        // 負数・null・0を排除し、long飽和値を正確値と取り違えない。
        for (Map.Entry<AEKey, BigInteger> entry : amounts.entrySet()) {
            AEKey key = Objects.requireNonNull(entry.getKey(), "inventory key");
            BigInteger amount = Objects.requireNonNull(
                    entry.getValue(), "inventory amount");
            if (amount.signum() < 0) {
                throw new IllegalArgumentException(
                        "vector inventory amount must not be negative");
            }
            if (amount.signum() > 0) {
                checked.put(key, amount);
            }
        }
        amounts = Map.copyOf(checked);
    }

    public BigInteger amount(AEKey key) {
        return amounts.getOrDefault(
                Objects.requireNonNull(key, "key"), BigInteger.ZERO);
    }
}
