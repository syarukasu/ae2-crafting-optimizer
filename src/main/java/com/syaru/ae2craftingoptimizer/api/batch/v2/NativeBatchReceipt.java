package com.syaru.ae2craftingoptimizer.api.batch.v2;

import java.util.Objects;
import java.util.UUID;

public record NativeBatchReceipt(
        UUID transactionId,
        State state,
        long executions,
        String patternFingerprint,
        long updatedTick) {
    public NativeBatchReceipt {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(state, "state");
        if (executions <= 0L) {
            throw new IllegalArgumentException("executions must be positive");
        }
        patternFingerprint = Objects.requireNonNull(patternFingerprint, "patternFingerprint");
        if (patternFingerprint.isEmpty()) {
            throw new IllegalArgumentException("patternFingerprint must not be empty");
        }
        if (updatedTick < 0L) {
            throw new IllegalArgumentException("updatedTick must not be negative");
        }
    }

    public enum State {
        PENDING,
        ACCEPTED,
        REJECTED
    }
}
