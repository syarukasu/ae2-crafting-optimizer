package com.syaru.ae2craftingoptimizer.engine;

import java.math.BigInteger;
import java.util.Map;
import java.util.Objects;

public final class BigCountMath {
    /** Minecraft内で保存・同期・計算を許可する10進桁数の暫定上限。 */
    public static final int HARD_MAXIMUM_DECIMAL_DIGITS = 16_384;
    /** 16,384桁を超えない厳密な最大値。BigIntegerは不変なので共有してよい。 */
    private static final BigInteger HARD_MAXIMUM_VALUE =
            BigInteger.TEN.pow(HARD_MAXIMUM_DECIMAL_DIGITS).subtract(BigInteger.ONE);
    /** 上限値を表現するために必要なbit数。現在は54,427bit。 */
    public static final int HARD_MAXIMUM_BITS = HARD_MAXIMUM_VALUE.bitLength();

    private BigCountMath() {
    }

    /** Exact byte length of BigInteger's signed two's-complement encoding without allocating it. */
    public static long encodedBytes(BigInteger value) {
        requireMaximumBits(value, "encoded value", HARD_MAXIMUM_BITS);
        return ((long) value.bitLength() + 8L) / 8L;
    }

    public static BigInteger requireNonNegative(BigInteger value, String context) {
        Objects.requireNonNull(value, context);
        // 負数はクラフト量として無効なので、全演算の入口で拒否する。
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
        // 呼出側が実装上限を越える設定値を渡した場合は、値の検査前に拒否する。
        if (maximumBits < 1 || maximumBits > HARD_MAXIMUM_BITS) {
            throw new IllegalArgumentException(
                    "maximumBits must be between 1 and " + HARD_MAXIMUM_BITS);
        }
        // 管理者設定のbit上限を越える値は、NBTやpacketを割り当てる前に拒否する。
        if (value.bitLength() > maximumBits) {
            throw new IllegalArgumentException(context + " exceeds " + maximumBits + " bits");
        }
        // 最大bit長の一部は16,385桁へ届くため、厳密な10進16,384桁上限も比較する。
        if (value.compareTo(HARD_MAXIMUM_VALUE) > 0) {
            throw new IllegalArgumentException(
                    context + " exceeds the hard " + HARD_MAXIMUM_DECIMAL_DIGITS + " decimal-digit limit");
        }
        return value;
    }

    public static BigInteger hardMaximumValue() {
        return HARD_MAXIMUM_VALUE;
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
        // 0以下の除数はceilDivを定義できないため拒否する。
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
        // 0量はMapを増やさず、そのまま無視する。
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
        // 0量はMapを増やさず、そのまま無視する。
        if (amount.signum() != 0) {
            BigInteger current = target.getOrDefault(key, BigInteger.ZERO);
            target.put(key, add(current, amount, context, maximumBits));
        }
    }
}
