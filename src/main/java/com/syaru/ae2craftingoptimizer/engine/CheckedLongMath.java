package com.syaru.ae2craftingoptimizer.engine;

import java.util.Map;
import java.util.Objects;

public final class CheckedLongMath {
    private CheckedLongMath() {
    }

    public static long add(long left, long right, String context) {
        requireNonNegative(left, context);
        requireNonNegative(right, context);
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ignored) {
            throw new CountOverflowException("addition", left, right, context);
        }
    }

    public static long multiply(long left, long right, String context) {
        requireNonNegative(left, context);
        requireNonNegative(right, context);
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException ignored) {
            throw new CountOverflowException("multiplication", left, right, context);
        }
    }

    public static long ceilDiv(long dividend, long divisor, String context) {
        requireNonNegative(dividend, context);
        if (divisor <= 0L) {
            throw new IllegalArgumentException("divisor must be positive at " + context);
        }
        long quotient = dividend / divisor;
        return dividend % divisor == 0L ? quotient : add(quotient, 1L, context + "/ceil");
    }

    public static long subtractFloorZero(long left, long right, String context) {
        requireNonNegative(left, context);
        requireNonNegative(right, context);
        return left <= right ? 0L : left - right;
    }

    public static <K> void merge(Map<K, Long> target, K key, long amount, String context) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(key, "key");
        requireNonNegative(amount, context);
        if (amount == 0L) {
            return;
        }
        target.put(key, add(target.getOrDefault(key, 0L), amount, context));
    }

    public static long requireNonNegative(long value, String context) {
        if (value < 0L) {
            throw new IllegalArgumentException("negative crafting count " + value + " at " + context);
        }
        return value;
    }
}
