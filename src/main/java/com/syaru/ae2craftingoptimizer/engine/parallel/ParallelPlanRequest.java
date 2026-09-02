package com.syaru.ae2craftingoptimizer.engine.parallel;

import com.syaru.ae2craftingoptimizer.engine.BigCountMath;
import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** live AE2参照を含まない、一件のParallel Plan Session入力。 */
public record ParallelPlanRequest<K>(
        ParallelPatternIndex<K> patterns,
        K requestedOutput,
        BigInteger requestedAmount,
        Map<K, BigInteger> inventory,
        Map<K, Long> amountPerByte,
        int maximumBits,
        ParallelRevisionVector revisions) {
    public ParallelPlanRequest {
        Objects.requireNonNull(patterns, "patterns");
        Objects.requireNonNull(requestedOutput, "requestedOutput");
        requestedAmount = BigCountMath.requireMaximumBits(
                requestedAmount,
                "parallel/request",
                maximumBits);
        if (requestedAmount.signum() <= 0) {
            throw new IllegalArgumentException("requested amount must be positive");
        }
        inventory = immutableInventory(inventory, maximumBits);
        amountPerByte = immutableDivisors(amountPerByte);
        Objects.requireNonNull(revisions, "revisions");
        if (patterns.generation() != revisions.patternGeneration()) {
            throw new IllegalArgumentException("pattern index and revision vector do not match");
        }
    }

    private static <K> Map<K, BigInteger> immutableInventory(
            Map<K, BigInteger> source,
            int maximumBits) {
        Objects.requireNonNull(source, "inventory");
        Map<K, BigInteger> copy = new LinkedHashMap<>();
        for (Map.Entry<K, BigInteger> entry : source.entrySet()) {
            copy.put(
                    Objects.requireNonNull(entry.getKey(), "inventory key"),
                    BigCountMath.requireMaximumBits(
                            entry.getValue(),
                            "parallel/inventory",
                            maximumBits));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static <K> Map<K, Long> immutableDivisors(Map<K, Long> source) {
        Objects.requireNonNull(source, "amountPerByte");
        Map<K, Long> copy = new LinkedHashMap<>();
        for (Map.Entry<K, Long> entry : source.entrySet()) {
            K key = Objects.requireNonNull(entry.getKey(), "amountPerByte key");
            Long divisor = Objects.requireNonNull(entry.getValue(), "amountPerByte value");
            if (divisor <= 0L) {
                throw new IllegalArgumentException("amountPerByte must be positive");
            }
            copy.put(key, divisor);
        }
        return Collections.unmodifiableMap(copy);
    }
}
