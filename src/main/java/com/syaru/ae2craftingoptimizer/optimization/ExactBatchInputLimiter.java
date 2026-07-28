package com.syaru.ae2craftingoptimizer.optimization;

import java.math.BigInteger;
import java.util.Map;
import java.util.Objects;

/**
 * 一回分のキー別入力合計から、signed-longで安全に所有できる実行回数を求める。
 */
public final class ExactBatchInputLimiter {
    private static final BigInteger LONG_MAXIMUM =
            BigInteger.valueOf(Long.MAX_VALUE);

    private ExactBatchInputLimiter() {
    }

    public static <K> long limit(
            long requestedExecutions,
            Map<K, BigInteger> perExecution,
            AvailableAmount<K> availableAmount) {
        Objects.requireNonNull(perExecution, "perExecution");
        Objects.requireNonNull(availableAmount, "availableAmount");
        if (requestedExecutions <= 0L || perExecution.isEmpty()) {
            return 0L;
        }

        BigInteger safe =
                BigInteger.valueOf(requestedExecutions);
        for (Map.Entry<K, BigInteger> input :
                perExecution.entrySet()) {
            BigInteger perCraft = Objects.requireNonNull(
                    input.getValue(),
                    "perExecution amount");
            // 0以下の入力係数は正確な所有量を証明できないためBatch対象外にする。
            if (perCraft.signum() <= 0) {
                return 0L;
            }
            /*
             * 在庫APIへ渡す要求量自体もsigned longへ収める。
             * 実行回数が大きくても、キー単位の安全な最大回数だけを一度SIMULATEする。
             */
            BigInteger probeExecutions =
                    safe.min(LONG_MAXIMUM.divide(perCraft));
            if (probeExecutions.signum() <= 0) {
                return 0L;
            }
            long probeAmount = perCraft
                    .multiply(probeExecutions)
                    .longValueExact();
            long available = availableAmount.simulate(
                    input.getKey(),
                    probeAmount);
            // 負数は壊れた在庫応答なので、0へ丸めずBatchを拒否する。
            if (available < 0L) {
                return 0L;
            }
            safe = safe.min(
                    BigInteger.valueOf(available)
                            .divide(perCraft));
            if (safe.signum() <= 0) {
                return 0L;
            }
        }
        return safe.longValueExact();
    }

    @FunctionalInterface
    public interface AvailableAmount<K> {
        long simulate(K key, long requestedAmount);
    }
}
