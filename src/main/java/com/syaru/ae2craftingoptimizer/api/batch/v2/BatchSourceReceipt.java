package com.syaru.ae2craftingoptimizer.api.batch.v2;

import appeng.api.stacks.GenericStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record BatchSourceReceipt(
        UUID transactionId,
        State state,
        long executions,
        String taskFingerprint,
        int accountedOutputs,
        long updatedTick,
        List<GenericStack> extractedInputs) {
    public static final int MAX_EXTRACTED_INPUT_ENTRIES = 16_384;

    public BatchSourceReceipt(
            UUID transactionId,
            State state,
            long executions,
            String taskFingerprint,
            int accountedOutputs,
            long updatedTick) {
        this(transactionId, state, executions, taskFingerprint, accountedOutputs, updatedTick, List.of());
    }

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
        extractedInputs = copyExtractedInputs(extractedInputs);
        if (state == State.STAGED && !extractedInputs.isEmpty()) {
            throw new IllegalArgumentException("STAGED receipts cannot already contain extracted inputs");
        }
    }

    private static List<GenericStack> copyExtractedInputs(List<GenericStack> inputs) {
        Objects.requireNonNull(inputs, "extractedInputs");
        if (inputs.size() > MAX_EXTRACTED_INPUT_ENTRIES) {
            throw new IllegalArgumentException(
                    "extractedInputs exceeds " + MAX_EXTRACTED_INPUT_ENTRIES + " entries");
        }
        List<GenericStack> copy = new ArrayList<>(inputs.size());
        for (GenericStack stack : inputs) {
            Objects.requireNonNull(stack, "extractedInputs entry");
            if (stack.amount() <= 0L) {
                throw new IllegalArgumentException("extracted input amounts must be positive");
            }
            copy.add(stack);
        }
        return List.copyOf(copy);
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
