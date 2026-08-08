package com.syaru.ae2craftingoptimizer.api.contract;

/** Durable receipt reservation states shared by ACO and future AAC adapters. */
public enum ReceiptReservationState {
    RESERVED,
    RUNNING,
    OUTPUT_READY,
    ACKNOWLEDGED,
    CANCELLED,
    FORGOTTEN,
    QUARANTINED
}
