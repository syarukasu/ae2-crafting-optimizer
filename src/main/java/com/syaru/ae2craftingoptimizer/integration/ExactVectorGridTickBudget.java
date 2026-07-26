package com.syaru.ae2craftingoptimizer.integration;

import appeng.api.networking.IGrid;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.optimization.ServerTickClock;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * BigInteger親Jobと標準AQE Jobが共有する、Grid単位のExact Vector tick予算。
 */
final class ExactVectorGridTickBudget {
    /** Configのミリ秒をSystem.nanoTimeのナノ秒へ変換する固定倍率。 */
    private static final long NANOSECONDS_PER_MILLISECOND = 1_000_000L;
    private static final Map<IGrid, ExactVectorGridTickBudget> BUDGETS =
            new WeakHashMap<>();

    private long tick = Long.MIN_VALUE;
    private long startedAtNanos;
    private int starts;
    private int completions;

    private ExactVectorGridTickBudget() {
    }

    static synchronized ExactVectorGridTickBudget forGrid(IGrid grid) {
        ExactVectorGridTickBudget budget = BUDGETS.computeIfAbsent(
                Objects.requireNonNull(grid, "grid"),
                ignored -> new ExactVectorGridTickBudget());
        budget.resetForTick(ServerTickClock.currentTick());
        return budget;
    }

    static synchronized void clearAll() {
        BUDGETS.clear();
    }

    synchronized boolean tryStart() {
        long softBudgetNanos = Math.multiplyExact(
                ACOConfig.getExactVectorGridTimeBudgetMillis(),
                NANOSECONDS_PER_MILLISECOND);
        // 件数またはsoft時間予算へ達したGridでは、新規Transactionを次tickへ送る。
        if (starts
                        >= ACOConfig.getExactVectorMaximumStartsPerGridTick()
                || elapsedNanos() >= softBudgetNanos) {
            return false;
        }
        starts++;
        return true;
    }

    synchronized boolean tryCompletion() {
        long hardBudgetNanos = Math.multiplyExact(
                ACOConfig.getExactVectorHardTimeBudgetMillis(),
                NANOSECONDS_PER_MILLISECOND);
        /*
         * 完了会計は出力待ちを長く保持しないためsoft予算後も許可するが、
         * hard予算と件数上限は越えない。
         */
        if (completions
                        >= ACOConfig.getExactVectorMaximumCompletionsPerGridTick()
                || elapsedNanos() >= hardBudgetNanos) {
            return false;
        }
        completions++;
        return true;
    }

    private void resetForTick(long currentTick) {
        // 同tick内の別CPUから呼ばれた場合は、既に消費した共有予算を維持する。
        if (tick == currentTick) {
            return;
        }
        tick = currentTick;
        startedAtNanos = System.nanoTime();
        starts = 0;
        completions = 0;
    }

    private long elapsedNanos() {
        return Math.max(0L, System.nanoTime() - startedAtNanos);
    }
}
