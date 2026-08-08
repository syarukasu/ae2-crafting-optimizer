package com.syaru.ae2craftingoptimizer.api.contract;

import java.util.Objects;

/** Immutable revision hint used only to wake a waiting integration. */
public final class BatchTargetRevision {
    private final String targetIdentity;
    private final String runtimeIdentity;
    private final String transactionId;
    private final long ownershipRevision;
    private final long progressRevision;
    private final long receiptRevision;
    private final String stateHint;

    public BatchTargetRevision(
            String targetIdentity,
            String runtimeIdentity,
            String transactionId,
            long ownershipRevision,
            long progressRevision,
            long receiptRevision,
            String stateHint,
            ExactCountLimits limits) {
        if (ownershipRevision < 0L || progressRevision < 0L || receiptRevision < 0L) {
            throw new IllegalArgumentException("revisions must not be negative");
        }
        Objects.requireNonNull(limits, "limits").validateRequiredIdentifier(targetIdentity);
        limits.validateRequiredIdentifier(runtimeIdentity);
        limits.validateRequiredIdentifier(transactionId);
        limits.validateIdentifier(stateHint);
        this.targetIdentity = targetIdentity;
        this.runtimeIdentity = runtimeIdentity;
        this.transactionId = transactionId;
        this.ownershipRevision = ownershipRevision;
        this.progressRevision = progressRevision;
        this.receiptRevision = receiptRevision;
        this.stateHint = stateHint;
    }

    public String targetIdentity() {
        return targetIdentity;
    }

    public String runtimeIdentity() {
        return runtimeIdentity;
    }

    public String transactionId() {
        return transactionId;
    }

    public long ownershipRevision() {
        return ownershipRevision;
    }

    public long progressRevision() {
        return progressRevision;
    }

    public long receiptRevision() {
        return receiptRevision;
    }

    public String stateHint() {
        return stateHint;
    }
}
