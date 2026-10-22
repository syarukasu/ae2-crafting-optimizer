package com.syaru.ae2craftingoptimizer.api.processing;

import appeng.api.crafting.IPatternDetails;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchSnapshot;
import java.util.Optional;
import java.util.UUID;

/**
 * Opt-in, escrow-backed processing worker. Implement on the provider-owned or adjacent BlockEntity.
 * Inputs remain owned by ACO escrow; never extract them again or deliver outputs by another route.
 * The worker validates the native recipe, catalysts and full component keys before acceptance.
 * Persist identity and exact counts before returning true; retain completed receipts until forget.
 * False acceptance means no ownership and no side effects. All lifecycle operations are idempotent.
 * A successful cancellation guarantees no outputs can escape and all reserved inputs are refundable.
 * Partial irreversible work must finish to an exact output receipt, not report successful cancellation.
 */
public interface ProcessingPatternBatchTarget {
    int API_VERSION = 1;
    /** Read-only capability query; never reserve resources here. */
    boolean aco$supportsProcessingPattern(IPatternDetails pattern);

    boolean aco$acceptProcessingPatternBatch(ProcessingPatternBatchRequest request);
    boolean aco$ownsProcessingPatternBatch(UUID transactionId, String payloadDigest);

    /** Historical snapshot name; the receipt contract is shared, not restricted to crafting tables. */
    Optional<CraftingTableBatchSnapshot> aco$processingPatternBatchSnapshot(UUID transactionId, String payloadDigest);
    boolean aco$acknowledgeProcessingPatternBatch(UUID transactionId, String payloadDigest);
    boolean aco$forgetProcessingPatternBatch(UUID transactionId, String payloadDigest);
    boolean aco$cancelProcessingPatternBatch(UUID transactionId, String payloadDigest);
}
