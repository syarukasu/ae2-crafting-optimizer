package com.syaru.ae2craftingoptimizer.api.batch.v2;

import java.util.Objects;
import java.util.UUID;

public record NativeBatchReceipt(
        UUID transactionId,
        State state,
        long executions,
        String patternFingerprint,
        String payloadDigest,
        long updatedTick) {
    /** Schema 1互換。digestを持たない旧Receiptは復旧時に所有権証明として使わない。 */
    public NativeBatchReceipt(
            UUID transactionId,
            State state,
            long executions,
            String patternFingerprint,
            long updatedTick) {
        this(transactionId, state, executions, patternFingerprint, "", updatedTick);
    }

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
        payloadDigest = Objects.requireNonNull(payloadDigest, "payloadDigest");
        if (payloadDigest.length() > 128) {
            throw new IllegalArgumentException("payloadDigest exceeds its safety bound");
        }
        if (updatedTick < 0L) {
            throw new IllegalArgumentException("updatedTick must not be negative");
        }
    }

    public boolean hasDurablePayloadProof() {
        return !payloadDigest.isBlank();
    }

    public enum State {
        PENDING,
        ACCEPTED,
        REJECTED
    }
}
