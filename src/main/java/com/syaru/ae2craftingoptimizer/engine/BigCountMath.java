package com.syaru.ae2craftingoptimizer.engine;

import java.math.BigInteger;
import java.util.Map;
import java.util.Objects;

public final class BigCountMath {
    public static final int HARD_MAXIMUM_BITS = 1_048_576;

    private BigCountMath() {
    }

    /** Exact byte length of BigInteger's signed two's-complement encoding without allocating it. */
    public static long encodedBytes(BigInteger value) {
        requireNonNegative(value, "encoded value");
        return ((long) value.bitLength() + 8L) / 8L;
    }

    public static BigInteger requireNonNegative(BigInteger value, String context) {
        Objects.requireNonNull(value, context);
        if (value.signum() < 0) {
            throw new IllegalArgumentException("negative crafting count at " + context);
        }
        return value;
    }

    public static BigInteger requireMaximumBits(
            BigInteger value,
            String context,
            int maximumBits) {
        requireNonNegative(value, context);
        if (maximumBits < 1 || maximumBits > HARD_MAXIMUM_BITS) {
            throw new IllegalArgumentException(
                    "maximumBits must be between 1 and " + HARD_MAXIMUM_BITS);
        }
        if (value.bitLength() > maximumBits) {
            throw new IllegalArgumentException(context + " exceeds " + maximumBits + " bits");
        }
        return value;
    }

    public static BigInteger add(
            BigInteger left,
            BigInteger right,
            String context,
            int maximumBits) {
        requireMaximumBits(left, context + "/left", maximumBits);
        requireMaximumBits(right, context + "/right", maximumBits);
        return requireMaximumBits(left.add(right), context, maximumBits);
    }

    public static BigInteger multiply(
            BigInteger left,
            BigInteger right,
            String context,
            int maximumBits) {
        requireMaximumBits(left, context + "/left", maximumBits);
        requireMaximumBits(right, context + "/right", maximumBits);
        return requireMaximumBits(left.multiply(right), context, maximumBits);
    }

    public static BigInteger ceilDiv(BigInteger dividend, BigInteger divisor, String context) {
        requireNonNegative(dividend, context);
        Objects.requireNonNull(divisor, "divisor");
        if (divisor.signum() <= 0) {
            throw new IllegalArgumentException("divisor must be positive at " + context);
        }
        BigInteger[] division = dividend.divideAndRemainder(divisor);
        return division[1].signum() == 0 ? division[0] : division[0].add(BigInteger.ONE);
    }

    public static <K> void merge(
            Map<K, BigInteger> target,
            K key,
            BigInteger amount,
            String context) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(key, "key");
        requireNonNegative(amount, context);
        if (amount.signum() != 0) {
            target.merge(key, amount, BigInteger::add);
        }
    }

    public static <K> void merge(
            Map<K, BigInteger> target,
            K key,
            BigInteger amount,
            String context,
            int maximumBits) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(key, "key");
        requireMaximumBits(amount, context + "/amount", maximumBits);
        if (amount.signum() != 0) {
            BigInteger current = target.getOrDefault(key, BigInteger.ZERO);
            target.put(key, add(current, amount, context, maximumBits));
        }
    }
}
