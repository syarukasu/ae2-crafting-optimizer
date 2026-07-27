package com.syaru.ae2craftingoptimizer.api.vector;

import java.math.BigInteger;
import java.util.Objects;

/** 一AEKeyの正確在庫操作結果。失敗時に状態を推測せず隔離できる情報を返す。 */
public record ExactStorageMutationResult(
        boolean successful,
        boolean stateUncertain,
        BigInteger appliedAmount,
        String detail) {
    public ExactStorageMutationResult {
        appliedAmount = Objects.requireNonNull(appliedAmount, "appliedAmount");
        detail = Objects.requireNonNull(detail, "detail");
        if (appliedAmount.signum() < 0) {
            throw new IllegalArgumentException("appliedAmount must not be negative");
        }
        // 成功時だけ正の全量を返し、失敗時の予定量を実績として扱わない。
        if (successful && appliedAmount.signum() == 0) {
            throw new IllegalArgumentException(
                    "successful exact storage mutation must apply a positive amount");
        }
        if (!successful && appliedAmount.signum() != 0) {
            throw new IllegalArgumentException(
                    "failed exact storage mutation must not report committed stock");
        }
    }

    public static ExactStorageMutationResult success(BigInteger amount) {
        return new ExactStorageMutationResult(true, false, amount, "");
    }

    public static ExactStorageMutationResult rejected(String detail) {
        return new ExactStorageMutationResult(
                false, false, BigInteger.ZERO, Objects.requireNonNull(detail, "detail"));
    }

    public static ExactStorageMutationResult uncertain(String detail) {
        return new ExactStorageMutationResult(
                false, true, BigInteger.ZERO, Objects.requireNonNull(detail, "detail"));
    }
}
