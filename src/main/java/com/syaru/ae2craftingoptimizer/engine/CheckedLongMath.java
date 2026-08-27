package com.syaru.ae2craftingoptimizer.engine;

import java.util.List;
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

    /** 正常系では診断文字列を連結せず、失敗した添字だけを例外へ展開する。 */
    public static long addIndexed(
            long left,
            long right,
            String contextPrefix,
            int index) {
        // 負数はクラフト数量の不変条件違反なので、正確な添字付きで拒否する。
        if (left < 0L || right < 0L) {
            return add(left, right, indexedContext(contextPrefix, index));
        }
        // 非負数同士は残容量比較だけでoverflowを検出できる。
        if (left > Long.MAX_VALUE - right) {
            throw new CountOverflowException(
                    "addition",
                    left,
                    right,
                    indexedContext(contextPrefix, index));
        }
        return left + right;
    }

    /** 正常系では診断文字列を連結せず、失敗した添字だけを例外へ展開する。 */
    public static long multiplyIndexed(
            long left,
            long right,
            String contextPrefix,
            int index) {
        // 負数はクラフト数量の不変条件違反なので、正確な添字付きで拒否する。
        if (left < 0L || right < 0L) {
            return multiply(left, right, indexedContext(contextPrefix, index));
        }
        // 0を先に処理し、除算によるoverflow判定で0除算を起こさない。
        if (left == 0L || right == 0L) {
            return 0L;
        }
        // 非負数同士は除算した上限との比較だけでoverflowを検出できる。
        if (left > Long.MAX_VALUE / right) {
            throw new CountOverflowException(
                    "multiplication",
                    left,
                    right,
                    indexedContext(contextPrefix, index));
        }
        return left * right;
    }

    /** 正常系では診断文字列を連結せず、失敗した添字だけを例外へ展開する。 */
    public static long ceilDivIndexed(
            long dividend,
            long divisor,
            String contextPrefix,
            int index) {
        // 不正値だけ従来APIへ渡し、同じ種類の例外と添字付きcontextを維持する。
        if (dividend < 0L || divisor <= 0L) {
            return ceilDiv(
                    dividend,
                    divisor,
                    indexedContext(contextPrefix, index));
        }
        long quotient = dividend / divisor;
        // 非負数のceil除算では、剰余がある場合だけ1を足してもlong上限を越えない。
        return dividend % divisor == 0L ? quotient : quotient + 1L;
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

    /** 各要素をlongのまま保ちながら、要素間の合計だけがlong範囲を超えるか判定する。 */
    public static boolean sumExceedsLong(Map<?, Long> counts, String context) {
        return sumExceedsLong(List.of(counts), context);
    }

    /** 複数の排他的なカウンタを一つの合計として、加算せずにlong境界判定する。 */
    public static boolean sumExceedsLong(
            Iterable<? extends Map<?, Long>> counters,
            String context) {
        Objects.requireNonNull(counters, "counters");
        long remaining = Long.MAX_VALUE;
        // 加算そのものを行わず残量から比較し、境界判定中のoverflowを避ける。
        for (Map<?, Long> counts : counters) {
            Objects.requireNonNull(counts, "counts");
            for (long amount : counts.values()) {
                requireNonNegative(amount, context);
                if (amount > remaining) {
                    return true;
                }
                remaining -= amount;
            }
        }
        return false;
    }

    public static long requireNonNegative(long value, String context) {
        if (value < 0L) {
            throw new IllegalArgumentException("negative crafting count " + value + " at " + context);
        }
        return value;
    }

    private static String indexedContext(String contextPrefix, int index) {
        return Objects.requireNonNull(contextPrefix, "contextPrefix") + '/' + index;
    }
}
