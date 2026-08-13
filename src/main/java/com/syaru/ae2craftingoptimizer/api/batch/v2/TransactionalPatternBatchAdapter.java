package com.syaru.ae2craftingoptimizer.api.batch.v2;

import com.syaru.ae2craftingoptimizer.api.batch.PatternBatchBudget;
import com.syaru.ae2craftingoptimizer.api.batch.PatternBatchContext;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

public interface TransactionalPatternBatchAdapter {
    ResourceLocation id();

    default int priority() {
        return 0;
    }

    /**
     * CPUのtick予算へBatchをどう数えるか。
     *
     * <p>既存Adapterとの互換性を保つため、既定値は論理実行数である。</p>
     */
    default BatchCpuAccountingMode cpuAccountingMode() {
        return BatchCpuAccountingMode.LOGICAL_EXECUTIONS;
    }

    /**
     * 電力会計の所有者。
     *
     * <p>既存AdapterはAE2送信元会計を維持する。NeoECO Workerのように
     * 実機側で電力と進捗を持つAdapterだけがTARGETを明示する。</p>
     */
    default BatchEnergyAccountingMode energyAccountingMode() {
        return BatchEnergyAccountingMode.SOURCE_LOGICAL_EXECUTIONS;
    }

    boolean supports(PatternBatchContext context);

    /**
     * 在庫、waitingFor、電力を適用した後で一括経路を使う最小実行回数。
     *
     * <p>ACOはこの数まで入力が同時に揃わない場合、入力を抽出せず通常AE2経路へ戻す。</p>
     */
    default long minimumExecutions(PatternBatchContext context) {
        return 1L;
    }

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

    /** Recovery entry point for integrations that use only the public API. */
    default BatchRecoveryResult reconcileTarget(
            ServerLevel level,
            BatchTransactionRecord record) {
        throw new UnsupportedOperationException(
                "adapter does not implement public transaction recovery");
    }

    /** Called after the matching durable journal record reached a terminal phase. */
    default void forgetResolvedTarget(PatternBatchContext context, UUID transactionId) {
    }

    /** Recovery-side counterpart for public-API integrations. */
    default void forgetResolvedTarget(ServerLevel level, BatchTransactionRecord record) {
    }
}
