package com.syaru.ae2craftingoptimizer.mixin;

import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.api.execution.VectorBatchExecutionOwner;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.optimization.CraftingExecutionBudget;
import com.syaru.ae2craftingoptimizer.optimization.ServerTickClock;

/** Neo ECO 20.3/20.4の記述子差分から独立した、共通の実行予算計算。 */
final class NeoEcoExecutionBudgetSupport {
    private NeoEcoExecutionBudgetSupport() {
    }

    static LimitDecision limitOperations(
            Object owner,
            CraftingService craftingService,
            int originalOperations,
            boolean fastPath) {
        // 設定OFF時と実行不能時は、Neo ECOが算出した値を変更しない。
        if (!ACOConfig.throttleNeoEcoAeExecution() || originalOperations <= 0) {
            return new LimitDecision(originalOperations, 0, false);
        }

        // 原子的Vector Batchを宣言した設備だけは、論理個数ではなく一件の実処理として予算化する。
        if (fastPath
                && owner instanceof VectorBatchExecutionOwner vectorOwner
                && vectorOwner.acoSupportsVectorBatchExecution()) {
            long remainingNanos = CraftingExecutionBudget.remainingSharedBudgetNanos(
                    craftingService,
                    ServerTickClock.currentTick());
            // 共有時間を使い切ったtickでも一件だけ許可し、巨大Batchを細切れにしない。
            int limitedOperations = remainingNanos <= 0L ? 1 : originalOperations;
            return new LimitDecision(limitedOperations, 1, true);
        }

        int perCpuOperations = CraftingExecutionBudget.limitExternalOperations(
                owner,
                originalOperations,
                "Neo ECO AE");
        int limitedOperations = CraftingExecutionBudget.limitSharedOperations(
                craftingService,
                owner,
                perCpuOperations,
                ServerTickClock.currentTick());
        return new LimitDecision(limitedOperations, limitedOperations, false);
    }

    static long beginExecution() {
        // 設定OFF時は計測自体を避ける。
        if (!ACOConfig.throttleNeoEcoAeExecution()) {
            return 0L;
        }
        return System.nanoTime();
    }

    static void recordExecution(
            Object owner,
            CraftingService craftingService,
            int requestedOperations,
            boolean vectorBatch,
            long startedAt,
            int returnedOperations) {
        // 実行入口を通っていない場合や設定OFF時は、統計へ不完全な値を混ぜない。
        if (!ACOConfig.throttleNeoEcoAeExecution() || startedAt == 0L) {
            return;
        }

        long elapsedNanos = System.nanoTime() - startedAt;
        // Vector Batchは論理クラフト個数ではなく、受理された一件の原子的Batchとして数える。
        int completedOperations = vectorBatch
                ? (returnedOperations > 0 ? 1 : 0)
                : Math.max(0, returnedOperations);
        CraftingExecutionBudget.recordExecution(
                owner,
                requestedOperations,
                completedOperations,
                elapsedNanos);
        CraftingExecutionBudget.recordSharedExecution(
                craftingService,
                owner,
                ServerTickClock.currentTick(),
                elapsedNanos);
    }

    record LimitDecision(int limitedOperations, int requestedOperations, boolean vectorBatch) {
    }
}
