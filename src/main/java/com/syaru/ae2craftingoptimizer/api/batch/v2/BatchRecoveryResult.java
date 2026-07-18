package com.syaru.ae2craftingoptimizer.api.batch.v2;

import java.util.Objects;

public record BatchRecoveryResult(TargetState targetState, long acceptedExecutions, String detail) {
    public BatchRecoveryResult {
        Objects.requireNonNull(targetState, "targetState");
        if (acceptedExecutions < 0L) {
            throw new IllegalArgumentException("acceptedExecutions must not be negative");
        }
        if (targetState == TargetState.ACCEPTED && acceptedExecutions == 0L) {
            throw new IllegalArgumentException("ACCEPTED recovery requires a positive execution count");
        }
        if (targetState != TargetState.ACCEPTED && acceptedExecutions != 0L) {
            throw new IllegalArgumentException("only ACCEPTED recovery may carry an execution count");
        }
        detail = Objects.requireNonNull(detail, "detail");
        if (detail.length() > 4096) {
            throw new IllegalArgumentException("recovery detail must not exceed 4096 characters");
        }
    }

    public enum TargetState {
        NOT_ACCEPTED,
        ACCEPTED,
        RETRY,
        QUARANTINE
    }
}
