package com.syaru.ae2craftingoptimizer.api.batch.v2;

import java.util.Objects;
import java.util.UUID;

public record BatchSourceReceipt(
        UUID transactionId,
        State state,
        long executions,
        String taskFingerprint,
        int accountedOutputs,
        long updatedTick) {
    public BatchSourceReceipt {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(state, "state");
        if (executions <= 0L) {
            throw new IllegalArgumentException("executions must be positive");
        }
        taskFingerprint = Objects.requireNonNull(taskFingerprint, "taskFingerprint");
        if (taskFingerprint.isEmpty()) {
            throw new IllegalArgumentException("taskFingerprint must not be empty");
        }
        if (accountedOutputs < 0) {
            throw new IllegalArgumentException("accountedOutputs must not be negative");
        }
        if (state != State.OUTPUT_ACCOUNTING
                && state != State.OUTPUTS_ACCOUNTING
                && state != State.ACCOUNTED
                && accountedOutputs != 0) {
            throw new IllegalArgumentException("accountedOutputs is only valid while accounting outputs");
        }
        if (updatedTick < 0L) {
            throw new IllegalArgumentException("updatedTick must not be negative");
        }
    }

    public enum State {
        STAGED,
        EXTRACTING,
        EXTRACTED,
        TARGET_ACCEPTED,
        ENERGY_ACCOUNTING,
        ENERGY_ACCOUNTED,
        PROGRESS_ACCOUNTED,
        OUTPUTS_ACCOUNTING,
        OUTPUT_ACCOUNTING,
        ACCOUNTED,
        ROLLED_BACK
    }
}
