package com.syaru.ae2craftingoptimizer.optimization;

import com.syaru.ae2craftingoptimizer.api.batch.v2.BatchCpuAccountingMode;
import java.util.Objects;

/**
 * 物理配送回数と、一つのNative Batchが所有する論理実行係数を分離する。
 */
final class BatchExecutionOffer {
    private BatchExecutionOffer() {
    }

    static long select(
            long remainingExecutions,
            int maximumPhysicalOperations,
            BatchCpuAccountingMode accountingMode,
            long maximumLogicalExecutions) {
        if (remainingExecutions <= 0L) {
            return 0L;
        }
        if (maximumPhysicalOperations <= 0
                || maximumLogicalExecutions <= 0L) {
            throw new IllegalArgumentException(
                    "batch execution limits must be positive");
        }
        Objects.requireNonNull(
                accountingMode,
                "accountingMode");

        /*
         * AACのように一つの実Worker仕事へ全係数を渡すAdapterでは、
         * intのCPU/Thread予算を成果物係数へ混ぜない。
         */
        if (accountingMode
                == BatchCpuAccountingMode.SINGLE_PHYSICAL_OPERATION) {
            return remainingExecutions;
        }

        /*
         * 従来Adapterは一論理実行を一CPU操作として数えるため、
         * intの物理予算とACOの論理上限の小さい方までに留める。
         */
        return Math.min(
                remainingExecutions,
                Math.min(
                        (long) maximumPhysicalOperations,
                        maximumLogicalExecutions));
    }
}
