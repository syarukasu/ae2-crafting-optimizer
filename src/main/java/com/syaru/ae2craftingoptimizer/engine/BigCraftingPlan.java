package com.syaru.ae2craftingoptimizer.engine;

import java.math.BigInteger;
import java.util.Map;
import java.util.Objects;

public record BigCraftingPlan<K>(
        K requestedKey,
        BigInteger requestedAmount,
        Map<String, BigInteger> patternExecutions,
        Map<K, BigInteger> usedInventory,
        Map<K, BigInteger> emitted,
        Map<K, BigInteger> missing,
        int expandedRequests) {
    public BigCraftingPlan {
        Objects.requireNonNull(requestedKey, "requestedKey");
        BigCountMath.requireNonNegative(requestedAmount, "plan/requestedAmount");
        patternExecutions = immutableCounts(patternExecutions, "patternExecutions");
        usedInventory = immutableCounts(usedInventory, "usedInventory");
        emitted = immutableCounts(emitted, "emitted");
        missing = immutableCounts(missing, "missing");
        if (expandedRequests < 0) {
            throw new IllegalArgumentException("expandedRequests must not be negative");
        }
    }

    public boolean craftable() {
        return missing.isEmpty();
    }

    private static <K> Map<K, BigInteger> immutableCounts(Map<K, BigInteger> source, String name) {
        Objects.requireNonNull(source, name);
        source.forEach((key, value) -> {
            Objects.requireNonNull(key, name + " key");
            BigCountMath.requireNonNegative(value, name);
        });
        return Map.copyOf(source);
    }
}
