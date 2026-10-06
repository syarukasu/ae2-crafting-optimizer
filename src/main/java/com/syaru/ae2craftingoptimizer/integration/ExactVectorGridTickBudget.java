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
public final class ExactVectorGridTickBudget {
    /** Configのミリ秒をSystem.nanoTimeのナノ秒へ変換する固定倍率。 */
    private static final long NANOSECONDS_PER_MILLISECOND = 1_000_000L;
    private static final Map<IGrid, ExactVectorGridTickBudget> BUDGETS =
            new WeakHashMap<>();

    private long tick = Long.MIN_VALUE;
    private long startedAtNanos;
    private int starts;
    private int activeStages;
    private boolean activeClaimed;

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

    /** Check between atomic steps; an existing operation must finish its receipt. */
    public static synchronized boolean hasTimeRemaining(IGrid grid) {
        return forGrid(grid).elapsedNanos() < Math.multiplyExact(
                ACOConfig.getExactVectorGridTimeBudgetMillis(),
                NANOSECONDS_PER_MILLISECOND);
    }

    /**
     * Optional Executor APIから、同じGridの論理段進行予算を数量非依存で取得する。
     */
    public static synchronized int claimActiveStages(
            IGrid grid,
            int requestedStages) {
        if (requestedStages <= 0) {
            throw new IllegalArgumentException(
                    "requestedStages must be positive");
        }
        ExactVectorGridTickBudget budget = forGrid(grid);
        long softBudgetNanos = Math.multiplyExact(
                ACOConfig.getExactVectorGridTimeBudgetMillis(),
                NANOSECONDS_PER_MILLISECOND);
        int maximum =
                ACOConfig.getExactVectorMaximumActiveStagesPerGridTick();
        long elapsedNanos = budget.elapsedNanos();
        // Returning unused slots does not make prior waiting/storage work free.
        if (budget.activeClaimed && elapsedNanos >= softBudgetNanos) {
            return 0;
        }
        int granted = claimOperations(
                budget.activeStages,
                maximum,
                requestedStages,
                elapsedNanos,
                softBudgetNanos);
        budget.activeStages += granted;
        budget.activeClaimed |= granted > 0;
        return granted;
    }

    /**
     * 先に予約した段数のうち、依存待ちなどで実処理しなかった枠を同じGridへ返す。
     */
    public static synchronized void settleActiveStageClaim(
            IGrid grid,
            int claimedStages,
            int consumedStages) {
        if (claimedStages < 0
                || consumedStages < 0
                || consumedStages > claimedStages) {
            throw new IllegalArgumentException(
                    "invalid Exact Vector active-stage settlement");
        }
        ExactVectorGridTickBudget budget = forGrid(grid);
        int unusedStages =
                claimedStages - consumedStages;
        // 同期的なserver tick内のClaimより多く返す場合は、会計破損として即座に検出する。
        if (unusedStages > budget.activeStages) {
            throw new IllegalStateException(
                    "Exact Vector active-stage settlement exceeds the current claim");
        }
        budget.activeStages -= unusedStages;
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

    /**
     * 数量ではなく固定済み論理段を一範囲として予約し、Grid上限を越えない件数を返す。
     */
    static int claimOperations(
            int completedOperations,
            int maximumOperations,
            int requestedOperations,
            long elapsedNanos,
            long timeBudgetNanos) {
        if (completedOperations < 0
                || maximumOperations < 0
                || requestedOperations <= 0) {
            throw new IllegalArgumentException(
                    "invalid Exact Vector operation budget");
        }
        // Grid件数上限は飢餓防止より優先し、残枠が無ければ一段も許可しない。
        if (completedOperations >= maximumOperations) {
            return 0;
        }
        int remainingOperations =
                maximumOperations - completedOperations;
        /*
         * Issue #218: 小さいJobも共有時間予算を守る。最初の一段だけ飢餓を防ぎ、
         * 既に進行したtickでは追加Jobを次tickへ送る。
         */
        if (elapsedNanos >= timeBudgetNanos) {
            return completedOperations == 0 ? 1 : 0;
        }
        return Math.min(
                requestedOperations,
                remainingOperations);
    }

    private void resetForTick(long currentTick) {
        // 同tick内の別CPUから呼ばれた場合は、既に消費した共有予算を維持する。
        if (tick == currentTick) {
            return;
        }
        tick = currentTick;
        /*
         * server tick STARTからではなく、このGridでExact Vector処理を初めて
         * 要求した時刻から測る。END phaseへ到達しただけで予算切れにしない。
         */
        startedAtNanos = System.nanoTime();
        starts = 0;
        activeStages = 0;
        activeClaimed = false;
    }

    private long elapsedNanos() {
        return Math.max(0L, System.nanoTime() - startedAtNanos);
    }
}
