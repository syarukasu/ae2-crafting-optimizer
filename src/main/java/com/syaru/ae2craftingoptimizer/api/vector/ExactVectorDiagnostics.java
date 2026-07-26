package com.syaru.ae2craftingoptimizer.api.vector;

import com.syaru.ae2craftingoptimizer.optimization.OptimizationMetrics;
import java.util.Objects;

/**
 * 外部Exact Vector設備が、数量非依存の低コスト統計をACOへ通知するAPI。
 *
 * <p>数量やAEKey一覧は受け取らず、診断処理自身が巨大注文へ比例しないようにする。</p>
 */
public final class ExactVectorDiagnostics {
    private ExactVectorDiagnostics() {
    }

    public static void transactionStarted(VectorResourceMode mode) {
        OptimizationMetrics.recordExactVectorStart(
                Objects.requireNonNull(mode, "mode"));
    }

    public static void activeTick(long elapsedNanos) {
        OptimizationMetrics.recordExactVectorActiveTick(elapsedNanos);
    }

    public static void transactionCompleted() {
        OptimizationMetrics.recordExactVectorCompletion();
    }

    public static void transactionCancelled() {
        OptimizationMetrics.recordExactVectorCancellation();
    }

    public static void transactionQuarantined() {
        OptimizationMetrics.recordExactVectorQuarantine();
    }
}
