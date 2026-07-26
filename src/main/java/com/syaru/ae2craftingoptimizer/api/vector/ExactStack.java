package com.syaru.ae2craftingoptimizer.api.vector;

import appeng.api.stacks.AEKey;
import java.math.BigInteger;
import java.util.Objects;

/** AEKey一種類と、その正確な非負longを超えられる数量。 */
public record ExactStack(AEKey key, BigInteger amount) {
    public ExactStack {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(amount, "amount");
        // 0量は台帳へ保存せず、各キーを一意な正数差分として扱う。
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("exact stack amount must be positive");
        }
    }
}
