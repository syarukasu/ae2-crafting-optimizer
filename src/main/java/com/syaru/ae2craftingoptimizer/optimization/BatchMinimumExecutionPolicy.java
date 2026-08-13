package com.syaru.ae2craftingoptimizer.optimization;

/** Native Batchを開始する最小実行回数の共通検証。 */
final class BatchMinimumExecutionPolicy {
    private BatchMinimumExecutionPolicy() {
    }

    static boolean isEligible(
            long finalExecutions,
            long minimumExecutions) {
        // Adapterの不正な契約値を通常経路への無言Fallbackとして隠さない。
        if (minimumExecutions <= 0L) {
            throw new IllegalArgumentException(
                    "minimum batch executions must be positive");
        }
        // 0は現在利用できる入力がない状態であり、Batchを開始しない。
        if (finalExecutions <= 0L) {
            return false;
        }
        return finalExecutions >= minimumExecutions;
    }
}
