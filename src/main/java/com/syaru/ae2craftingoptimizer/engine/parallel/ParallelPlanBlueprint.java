package com.syaru.ae2craftingoptimizer.engine.parallel;

import com.syaru.ae2craftingoptimizer.engine.BigCountMath;
import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** ACO workerからAE2計算workerへ返す、live Patternを含まない正確な結果。 */
public record ParallelPlanBlueprint<K>(
        K requestedOutput,
        BigInteger requestedAmount,
        BigInteger exactBytes,
        Map<String, BigInteger> patternExecutions,
        Map<K, BigInteger> usedInventory,
        Map<K, BigInteger> emitted,
        Map<K, BigInteger> missing,
        ArithmeticMode arithmeticMode,
        boolean ae2BytesProven,
        int expandedNodes,
        ParallelRevisionVector revisions) {
    public ParallelPlanBlueprint {
        Objects.requireNonNull(requestedOutput, "requestedOutput");
        Objects.requireNonNull(arithmeticMode, "arithmeticMode");
        Objects.requireNonNull(revisions, "revisions");
        requestedAmount = nonNegative(requestedAmount, "requestedAmount");
        exactBytes = nonNegative(exactBytes, "exactBytes");
        patternExecutions = immutableCounts(patternExecutions, "patternExecutions");
        usedInventory = immutableCounts(usedInventory, "usedInventory");
        emitted = immutableCounts(emitted, "emitted");
        missing = immutableCounts(missing, "missing");
        if (expandedNodes < 0) {
            throw new IllegalArgumentException("expandedNodes must not be negative");
        }
    }

    public boolean craftable() {
        return missing.isEmpty();
    }

    private static BigInteger nonNegative(BigInteger value, String name) {
        return BigCountMath.requireNonNegative(Objects.requireNonNull(value, name), name);
    }

    private static <K> Map<K, BigInteger> immutableCounts(Map<K, BigInteger> source, String name) {
        Objects.requireNonNull(source, name);
        Map<K, BigInteger> copy = new LinkedHashMap<>();
        for (Map.Entry<K, BigInteger> entry : source.entrySet()) {
            K key = Objects.requireNonNull(entry.getKey(), name + " key");
            BigInteger amount = nonNegative(entry.getValue(), name + " amount");
            if (amount.signum() > 0) {
                copy.put(key, amount);
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    public enum ArithmeticMode {
        CHECKED_LONG,
        BIG_INTEGER
    }
}
