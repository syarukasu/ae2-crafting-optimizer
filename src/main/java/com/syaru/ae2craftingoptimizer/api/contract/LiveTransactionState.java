package com.syaru.ae2craftingoptimizer.api.contract;

/** Proof result used when deciding whether a receipt may be treated as orphaned. */
public enum LiveTransactionState {
    ACTIVE,
    RECOVERABLE,
    ABSENT_CONFIRMED,
    UNKNOWN
}
