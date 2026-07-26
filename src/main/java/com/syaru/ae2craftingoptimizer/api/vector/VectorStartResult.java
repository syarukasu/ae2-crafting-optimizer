package com.syaru.ae2craftingoptimizer.api.vector;

import java.util.Objects;
import java.util.UUID;

/** ExecutorがReceiptを永続化した後に返す開始結果。 */
public record VectorStartResult(
        boolean started,
        UUID transactionId,
        VectorTransactionStatus status,
        String rejectionReason) {
    public VectorStartResult {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(status, "status");
        rejectionReason = Objects.requireNonNull(
                rejectionReason, "rejectionReason");
        // 開始成功時はPREPARED以降、拒否時はPREPAREDのまま所有権未移転とする。
        if (started && status == VectorTransactionStatus.CANCELLED) {
            throw new IllegalArgumentException(
                    "a started vector transaction cannot already be cancelled");
        }
    }

    public static VectorStartResult started(
            UUID transactionId,
            VectorTransactionStatus status) {
        return new VectorStartResult(true, transactionId, status, "");
    }

    public static VectorStartResult rejected(
            UUID transactionId,
            String reason) {
        return new VectorStartResult(
                false,
                transactionId,
                VectorTransactionStatus.PREPARED,
                Objects.requireNonNull(reason, "reason"));
    }
}
