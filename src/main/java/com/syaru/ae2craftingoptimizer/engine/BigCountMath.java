package com.syaru.ae2craftingoptimizer.engine;

import java.math.BigInteger;
import java.util.Map;
import java.util.Objects;

public final class BigCountMath {
    /** Minecraft内で保存・同期・計算を許可する10進桁数の実装上限。 */
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
        checkedBitLength(value, context, null, maximumBits);
        return value;
    }

    private static int checkedBitLength(
            BigInteger value,
            String context,
            String suffix,
            int maximumBits) {
        Objects.requireNonNull(value, context);
        if (value.signum() < 0) {
            throw new IllegalArgumentException(
                    "negative crafting count at " + formatContext(context, suffix));
        }
        // 呼出側が実装上限を越える設定値を渡した場合は、値の検査前に拒否する。
        if (maximumBits < 1 || maximumBits > HARD_MAXIMUM_BITS) {
            throw new IllegalArgumentException(
                    "maximumBits must be between 1 and " + HARD_MAXIMUM_BITS);
        }
        // 管理者設定のbit上限を越える値は、NBTやpacketを割り当てる前に拒否する。
        int bits = value.bitLength();
        if (bits > maximumBits) {
            throw new IllegalArgumentException(bitLimitMessage(context, suffix, maximumBits));
        }
        // 最大bit長の一部は16,385桁へ届くため、厳密な10進16,384桁上限も比較する。
        if (value.compareTo(HARD_MAXIMUM_VALUE) > 0) {
            throw new IllegalArgumentException(
                    formatContext(context, suffix)
                            + " exceeds the hard "
                            + HARD_MAXIMUM_DECIMAL_DIGITS
                            + " decimal-digit limit");
        }
        return bits;
    }

    private static String formatContext(String context, String suffix) {
        return suffix == null ? context : context + suffix;
    }

    private static String bitLimitMessage(String context, String suffix, int maximumBits) {
        return formatContext(context, suffix) + " exceeds " + maximumBits + " bits";
    }

    public static BigInteger hardMaximumValue() {
        return HARD_MAXIMUM_VALUE;
    }

    public static BigInteger add(
            BigInteger left,
            BigInteger right,
            String context,
            int maximumBits) {
        checkedBitLength(left, context, "/left", maximumBits);
        checkedBitLength(right, context, "/right", maximumBits);
        return requireMaximumBits(left.add(right), context, maximumBits);
    }

    public static BigInteger multiply(
            BigInteger left,
            BigInteger right,
            String context,
            int maximumBits) {
        int leftBits = checkedBitLength(left, context, "/left", maximumBits);
        int rightBits = checkedBitLength(right, context, "/right", maximumBits);
        // 正の整数の積は、両辺のビット長の和 - 1 以上のビット長を持つ。
        // 片方がゼロなら、もう片方は検証済みなのでこの条件を満たさない。
        if (leftBits + rightBits - 1 > maximumBits
                && left.getClass() == BigInteger.class && right.getClass() == BigInteger.class) {
            throw new IllegalArgumentException(bitLimitMessage(context, null, maximumBits));
        }
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
        checkedBitLength(amount, context, "/amount", maximumBits);
        // 0量はMapを増やさず、そのまま無視する。
        if (amount.signum() != 0) {
            BigInteger current = target.getOrDefault(key, BigInteger.ZERO);
            target.put(key, add(current, amount, context, maximumBits));
        }
    }
}
