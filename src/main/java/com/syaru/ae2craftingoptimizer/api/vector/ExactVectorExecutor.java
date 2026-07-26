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

    /**
     * simulate拒否が、設備枠・電力・一時在庫などの再試行可能な状態かを返す。
     *
     * <p>既定はfalseで、既存Executorの拒否は従来どおり通常経路へ戻る。再試行を宣言した
     * Executorが一つでもある場合、ACOは入力所有権を移さず親Jobを次tickまで保持する。</p>
     */
    default boolean shouldRetryRejectedOffer(
            PreparedVectorBatch plan,
            VectorExecutionOffer offer) {
        return false;
    }

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
