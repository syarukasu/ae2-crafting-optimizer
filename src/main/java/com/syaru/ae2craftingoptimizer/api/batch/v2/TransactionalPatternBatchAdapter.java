package com.syaru.ae2craftingoptimizer.api.batch.v2;

import com.syaru.ae2craftingoptimizer.api.batch.PatternBatchBudget;
import com.syaru.ae2craftingoptimizer.api.batch.PatternBatchContext;
import com.syaru.ae2craftingoptimizer.transaction.BatchTransactionRecord;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

public interface TransactionalPatternBatchAdapter {
    ResourceLocation id();

    default int priority() {
        return 0;
    }

    boolean supports(PatternBatchContext context);

    default long limitExecutions(PatternBatchContext context, long offeredExecutions) {
        return offeredExecutions;
    }

    /** Must validate and serialize intent without mutating the target or retaining input. */
    PreparedPatternBatch prepare(
            PatternBatchContext context,
            PatternBatchBudget budget,
            UUID transactionId);

    /** Must be idempotent for the transaction id and return a durable target receipt. */
    PatternBatchCommit commit(PatternBatchContext context, PreparedPatternBatch prepared);

    /** Called only before durable target acceptance. */
    void rollback(PatternBatchContext context, PreparedPatternBatch prepared);

    BatchRecoveryResult reconcileTarget(ServerLevel level, BatchTransactionRecord record);

    /** Called after the matching durable journal record reached a terminal phase. */
    default void forgetResolvedTarget(PatternBatchContext context, UUID transactionId) {
    }

    /** Recovery-side counterpart of {@link #forgetResolvedTarget(PatternBatchContext, UUID)}. */
    default void forgetResolvedTarget(ServerLevel level, BatchTransactionRecord record) {
    }
}
