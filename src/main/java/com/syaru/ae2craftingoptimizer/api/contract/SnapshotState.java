package com.syaru.ae2craftingoptimizer.api.contract;

/** Explicit state values for external snapshots; empty output is never used as a status flag. */
public enum SnapshotState {
    ACTIVE,
    PAUSED,
    QUARANTINED,
    CANCELLED,
    ACKNOWLEDGED
}
