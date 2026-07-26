package com.syaru.ae2craftingoptimizer.api.vector;

import java.math.BigInteger;
import java.util.Objects;
import java.util.UUID;

/** ACOが親Jobを確定するために読む、数量全文を含まない小さなReceipt概要。 */
public record VectorTransactionSnapshot(
        UUID transactionId,
        VectorTransactionStatus status,
        int activeTick,
        int durationTicks,
        BigInteger consumedEnergyMicroAe,
        String detail) {
    public VectorTransactionSnapshot {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(consumedEnergyMicroAe, "consumedEnergyMicroAe");
        detail = Objects.requireNonNull(detail, "detail");
        if (activeTick < 0
                || durationTicks <= 0
                || activeTick > durationTicks
                || consumedEnergyMicroAe.signum() < 0) {
            throw new IllegalArgumentException(
                    "invalid vector transaction snapshot");
        }
    }
}
