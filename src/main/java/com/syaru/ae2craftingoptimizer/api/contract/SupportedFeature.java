package com.syaru.ae2craftingoptimizer.api.contract;

/** Capability bits negotiated by ACO, AQE, and AAC at startup. */
public enum SupportedFeature {
    HOST_ATOMIC_SNAPSHOT,
    EXPLICIT_HOST_REGISTRATION,
    RECEIPT_SLOT_RESERVATION,
    LIVE_TRANSACTION_PROOF,
    TARGET_REVISION_WAKEUP,
    QUARANTINED_THREAD_STATE,
    EXACT_STORAGE_OPERATION_JOURNAL
}
