package com.syaru.ae2craftingoptimizer.batch;

import com.syaru.ae2craftingoptimizer.access.PatternProviderTransactionAccess;
import com.syaru.ae2craftingoptimizer.api.batch.PatternBatchContext;
import com.syaru.ae2craftingoptimizer.api.batch.v2.BatchOwnershipProof;
import com.syaru.ae2craftingoptimizer.api.batch.v2.BatchPayloadFingerprint;
import com.syaru.ae2craftingoptimizer.api.batch.v2.NativeBatchReceipt;
import com.syaru.ae2craftingoptimizer.api.batch.v2.NativeBatchReceiptStore;
import com.syaru.ae2craftingoptimizer.api.batch.v2.PatternBatchCommit;
import com.syaru.ae2craftingoptimizer.api.batch.v2.PreparedPatternBatch;

/**
 * Pattern Providerの永続send bufferをV2取引のEscrowとして使う共通処理。
 * 外部機械への挿入成功ではなく、Providerが完全payloadを所有した時点だけをcommitとする。
 */
public final class PatternProviderBatchEscrow {
    private PatternProviderBatchEscrow() {
    }

    public static PatternBatchCommit stage(
            PatternBatchContext context,
            PreparedPatternBatch prepared,
            String patternFingerprint,
            String rejectionReceipt) {
        if (!(context.provider() instanceof PatternProviderTransactionAccess access)
                || !(context.provider() instanceof NativeBatchReceiptStore store)
                || !store.aco$isNativeBatchReceiptLedgerHealthy()) {
            return new PatternBatchCommit(0L, rejectionReceipt, prepared.adapterData());
        }
        BatchOwnershipProof proof = access.aco$stageOwnedBatch(
                context.pattern(),
                context.providerSide(),
                prepared,
                patternFingerprint,
                context.level().getGameTime());
        if (proof == null) {
            return new PatternBatchCommit(0L, rejectionReceipt, prepared.adapterData());
        }
        if (!proof.matches(prepared)) {
            throw new IllegalStateException("Pattern Provider returned a mismatched ownership proof");
        }
        NativeBatchReceipt receipt = store.aco$getNativeBatchReceipt(prepared.transactionId());
        String digest = BatchPayloadFingerprint.of(prepared);
        if (receipt == null
                || receipt.state() != NativeBatchReceipt.State.ACCEPTED
                || receipt.executions() != prepared.offeredExecutions()
                || !receipt.patternFingerprint().equals(patternFingerprint)
                || !receipt.payloadDigest().equals(digest)) {
            throw new IllegalStateException("Pattern Provider escrow receipt does not match its payload");
        }
        return new PatternBatchCommit(
                prepared.offeredExecutions(),
                receipt.transactionId() + ":" + receipt.state() + ":" + receipt.executions(),
                prepared.adapterData(),
                proof);
    }
}
