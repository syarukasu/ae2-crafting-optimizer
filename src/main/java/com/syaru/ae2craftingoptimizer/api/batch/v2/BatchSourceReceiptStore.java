package com.syaru.ae2craftingoptimizer.api.batch.v2;

import java.util.UUID;

public interface BatchSourceReceiptStore {
    boolean aco$isBatchSourceReceiptLedgerHealthy();

    boolean aco$hasAnyUnresolvedBatchSourceReceipt();

    BatchSourceReceipt aco$getBatchSourceReceipt(UUID transactionId);

    boolean aco$hasUnresolvedBatchSourceReceipt(String taskFingerprint);

    boolean aco$stageBatchSourceReceipt(BatchSourceReceipt receipt);

    void aco$advanceBatchSourceReceipt(
            UUID transactionId,
            BatchSourceReceipt.State state,
            int accountedOutputs,
            long updatedTick);

    /** Removes evidence only after the matching durable journal record is terminal. */
    boolean aco$removeTerminalBatchSourceReceipt(UUID transactionId);
}
