package com.syaru.ae2craftingoptimizer.engine;

import java.util.Map;
import java.util.Objects;

public record LongCraftingPlan<K>(
        K requestedKey,
        long requestedAmount,
        Map<String, Long> patternExecutions,
        Map<K, Long> usedInventory,
        Map<K, Long> emitted,
        Map<K, Long> missing) {
    public LongCraftingPlan {
        Objects.requireNonNull(requestedKey, "requestedKey");
        CheckedLongMath.requireNonNegative(requestedAmount, "plan/requestedAmount");
        patternExecutions = Map.copyOf(patternExecutions);
        usedInventory = Map.copyOf(usedInventory);
        emitted = Map.copyOf(emitted);
        missing = Map.copyOf(missing);
    }

    public boolean craftable() {
        return missing.isEmpty();
    }
}
