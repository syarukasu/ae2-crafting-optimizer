package com.syaru.ae2craftingoptimizer.api.vector;

import java.util.Optional;
import java.util.UUID;

/** ACOの親Jobを作らず、形成済み外部設備でExact Vector計画を実行する契約。 */
public interface ExactVectorExecutor {
    VectorExecutorIdentity identity();

    boolean isAvailable();

    /**
     * 現在この設備が所有する未完了Transaction数。
     *
     * <p>ACOは同じGridの値を合算し、標準JobとBigInteger親Jobを共通上限で制御する。</p>
     */
    default int activeTransactionCount() {
        return 0;
    }

    VectorExecutionOffer simulate(PreparedVectorBatch plan);

    VectorStartResult start(PreparedVectorBatch plan);

    VectorTransactionStatus status(UUID transactionId);

    default Optional<VectorTransactionSnapshot> snapshot(UUID transactionId) {
        return Optional.empty();
    }

    /**
     * ACOが親Job会計を永続化した後、設備ReceiptをCOMPLETEDへ進める。
     */
    default boolean completeAccounting(UUID transactionId) {
        return false;
    }

    boolean cancel(UUID transactionId);
}
