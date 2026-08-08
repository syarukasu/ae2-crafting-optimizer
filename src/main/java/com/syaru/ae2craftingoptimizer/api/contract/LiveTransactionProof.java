package com.syaru.ae2craftingoptimizer.api.contract;

import java.util.Arrays;
import java.util.Objects;

/** Immutable proof returned by a live transaction authority. */
public final class LiveTransactionProof {
    private final String ownerRuntimeId;
    private final long ownerGeneration;
    private final String transactionId;
    private final byte[] payloadDigest;
    private final LiveTransactionState result;
    private final String accountingStateHint;

    public LiveTransactionProof(
            String ownerRuntimeId,
            long ownerGeneration,
            String transactionId,
            byte[] payloadDigest,
            LiveTransactionState result,
            String accountingStateHint,
            ExactCountLimits limits) {
        if (ownerGeneration < 0L) {
            throw new IllegalArgumentException("ownerGeneration must not be negative");
        }
        Objects.requireNonNull(limits, "limits").validateRequiredIdentifier(ownerRuntimeId);
        limits.validateRequiredIdentifier(transactionId);
        limits.validateDigest(payloadDigest);
        limits.validateIdentifier(accountingStateHint);
        this.ownerRuntimeId = ownerRuntimeId;
        this.ownerGeneration = ownerGeneration;
        this.transactionId = transactionId;
        this.payloadDigest = payloadDigest.clone();
        this.result = Objects.requireNonNull(result, "result");
        this.accountingStateHint = accountingStateHint;
    }

    public static LiveTransactionProof unknown(
            String ownerRuntimeId,
            long ownerGeneration,
            String transactionId,
            byte[] payloadDigest,
            ExactCountLimits limits) {
        return new LiveTransactionProof(
                ownerRuntimeId,
                ownerGeneration,
                transactionId,
                payloadDigest,
                LiveTransactionState.UNKNOWN,
                "unknown",
                limits);
    }

    public static LiveTransactionProof absentConfirmed(
            String ownerRuntimeId,
            long ownerGeneration,
            String transactionId,
            byte[] payloadDigest,
            ExactCountLimits limits) {
        return new LiveTransactionProof(
                ownerRuntimeId,
                ownerGeneration,
                transactionId,
                payloadDigest,
                LiveTransactionState.ABSENT_CONFIRMED,
                "absent_confirmed",
                limits);
    }

    public String ownerRuntimeId() {
        return ownerRuntimeId;
    }

    public long ownerGeneration() {
        return ownerGeneration;
    }

    public String transactionId() {
        return transactionId;
    }

    public byte[] payloadDigest() {
        return payloadDigest.clone();
    }

    public LiveTransactionState result() {
        return result;
    }

    public String accountingStateHint() {
        return accountingStateHint;
    }

    public boolean isAuthoritativelyAbsent() {
        return result == LiveTransactionState.ABSENT_CONFIRMED;
    }

    public boolean sameTransaction(String transactionId, byte[] digest) {
        return this.transactionId.equals(transactionId) && Arrays.equals(payloadDigest, digest);
    }
}
