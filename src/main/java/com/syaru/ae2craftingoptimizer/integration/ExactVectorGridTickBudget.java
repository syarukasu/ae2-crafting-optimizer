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
        /*
         * 計画コンパイルも同じtick時間へ含まれるため、最初の一件まで時間だけで拒否すると
         * 重いが適格なPlanが永遠に開始できない。件数上限は常に守り、時間超過は二件目以降
         * だけを次tickへ送る。
         */
        if (shouldDeferOperation(
                starts,
                ACOConfig.getExactVectorMaximumStartsPerGridTick(),
                elapsedNanos(),
                softBudgetNanos)) {
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
        if (shouldDeferOperation(
                completions,
                ACOConfig.getExactVectorMaximumCompletionsPerGridTick(),
                elapsedNanos(),
                hardBudgetNanos)) {
            return false;
        }
        completions++;
        return true;
    }

    /**
     * 一tickの最初の一件を時間だけで飢餓させず、二件目以降へ時間予算を適用する。
     */
    static boolean shouldDeferOperation(
            int completedOperations,
            int maximumOperations,
            long elapsedNanos,
            long timeBudgetNanos) {
        // 件数上限は初回保証より優先し、設定された最大数を絶対に超えない。
        if (completedOperations >= maximumOperations) {
            return true;
        }
        // 初回は必ず許可し、その処理時間を見て同tickの追加作業だけを延期する。
        return completedOperations > 0
                && elapsedNanos >= timeBudgetNanos;
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
