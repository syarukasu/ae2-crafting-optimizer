package com.syaru.ae2craftingoptimizer.api.batch.v2;

import java.util.UUID;

public interface NativeBatchReceiptStore {
    boolean aco$isNativeBatchReceiptLedgerHealthy();

    NativeBatchReceipt aco$getNativeBatchReceipt(UUID transactionId);

    boolean aco$prepareNativeBatchReceipt(NativeBatchReceipt receipt);

    void aco$finishNativeBatchReceipt(UUID transactionId, NativeBatchReceipt.State state, long updatedTick);

    /** Removes evidence only after the matching durable journal record is terminal. */
    boolean aco$removeTerminalNativeBatchReceipt(UUID transactionId);
}
