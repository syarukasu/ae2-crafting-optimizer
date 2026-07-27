package com.syaru.ae2craftingoptimizer.engine.vector;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Exact Vectorの正確な数量を変更せず、AE2のlong表示へ進捗を投影する。
 */
public final class LongClampedProgressProjection {
    private static final BigInteger LONG_MAX =
            BigInteger.valueOf(Long.MAX_VALUE);

    private LongClampedProgressProjection() {
    }

    /**
     * AE2へ公開できる非負longへ飽和変換する。
     */
    public static long clamp(BigInteger exactAmount) {
        BigInteger checked =
                Objects.requireNonNull(exactAmount, "exactAmount");
        // 負数は進捗表示でも会計破損を隠すため受け付けない。
        if (checked.signum() < 0) {
            throw new IllegalArgumentException(
                    "exactAmount must not be negative");
        }
        return checked.compareTo(LONG_MAX) >= 0
                ? Long.MAX_VALUE
                : checked.longValueExact();
    }

    /**
     * 完了tick数に応じた表示専用の残量を返す。
     *
     * <p>正確値がlongを超えている間も、Long.MAX_VALUEを全体量とした比率表示で
     * 毎段階減少する。返り値を実在庫やTask会計へ書き戻してはいけない。</p>
     */
    public static long remaining(
            BigInteger exactAmount,
            int completedTicks,
            int totalTicks) {
        validateTicks(completedTicks, totalTicks);
        BigInteger facade =
                BigInteger.valueOf(clamp(exactAmount));
        // 0量または全段完了後は表示残量も0にする。
        if (facade.signum() == 0 || completedTicks == totalTicks) {
            return 0L;
        }
        // 開始前は不要なBigInteger乗算をせず、long facade全量を返す。
        if (completedTicks == 0) {
            return facade.longValueExact();
        }

        int remainingTicks = totalTicks - completedTicks;
        BigInteger numerator = facade.multiply(
                BigInteger.valueOf(remainingTicks));
        BigInteger divisor = BigInteger.valueOf(totalTicks);
        BigInteger[] quotientAndRemainder =
                numerator.divideAndRemainder(divisor);
        /*
         * 端数を切り上げ、最終tickより前に小さいTaskが表示上だけ
         * 先に消えてしまわないようにする。
         */
        BigInteger projected = quotientAndRemainder[1].signum() == 0
                ? quotientAndRemainder[0]
                : quotientAndRemainder[0].add(BigInteger.ONE);
        return projected.longValueExact();
    }

    /**
     * 数量の桁数に依存しない、0.0から1.0の表示進捗率を返す。
     */
    public static float progress(int completedTicks, int totalTicks) {
        validateTicks(completedTicks, totalTicks);
        return (float) ((double) completedTicks / (double) totalTicks);
    }

    private static void validateTicks(
            int completedTicks,
            int totalTicks) {
        // 進捗範囲外の値を表示だけで丸めず、呼出側のReceipt不一致として露出させる。
        if (totalTicks <= 0
                || completedTicks < 0
                || completedTicks > totalTicks) {
            throw new IllegalArgumentException(
                    "invalid Exact Vector display progress");
        }
    }
}
