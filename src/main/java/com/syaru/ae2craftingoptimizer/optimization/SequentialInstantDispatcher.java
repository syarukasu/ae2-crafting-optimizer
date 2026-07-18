package com.syaru.ae2craftingoptimizer.optimization;

import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * AE2本来のexecuteCraftingを小さな計測波へ分け、同じserver tick内で時間予算まで継続する。
 *
 * <p>入力抽出、ProviderへのpushPattern、電力消費、waitingFor、task進捗は一切再実装しない。
 * 一つの波をAE2へ委譲し、波と波の間だけでCPU/Grid予算を判定するため、GTCEuやMekanismの
 * Item・Fluid・Chemicalを巨大な一括Stackへ変換せずにInstant配送できる。</p>
 */
public final class SequentialInstantDispatcher {
    private static final Map<Object, CpuTickState> CPU_STATES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private SequentialInstantDispatcher() {
    }

    /** AE2またはAdvancedAEの元executeCraftingを呼ぶ関数。 */
    @FunctionalInterface
    public interface CraftingWave {
        int execute(int maximumPatterns);
    }

    public static int executeWave(
            Object executionOwner,
            int requestedOperations,
            CraftingService craftingService,
            long gameTick,
            CraftingWave wave) {
        if (requestedOperations <= 0 || wave == null) {
            return 0;
        }

        int sharedLimited = CraftingExecutionBudget.limitSharedOperations(
                craftingService, executionOwner, requestedOperations, gameTick);
        if (sharedLimited <= 0) {
            return 0;
        }

        if (!ACOConfig.enableInstantPatternDispatch()) {
            return executeAndRecord(
                    executionOwner, sharedLimited, craftingService, gameTick, false, wave);
        }

        CpuTickState state = stateFor(executionOwner);
        state.beginTick(gameTick);

        long cpuBudgetNanos = millisecondsToNanos(ACOConfig.getInstantPatternDispatchTimeBudgetMillis());
        long cpuRemainingNanos = Math.max(0L, cpuBudgetNanos - state.consumedNanos);
        long sharedRemainingNanos = CraftingExecutionBudget.remainingSharedBudgetNanos(
                craftingService, gameTick);
        long remainingNanos = Math.min(cpuRemainingNanos, sharedRemainingNanos);
        if (remainingNanos <= 0L) {
            recordBudgetStopOnce(state);
            return 0;
        }

        long nanosPerOperation = CraftingExecutionBudget.estimatedNanosPerOperation(executionOwner);
        // 一度以上実行済みなら、次の一操作すら予算へ収まらない予測時は次tickへ送る。
        // tick最初の一波だけは進行保証として許可し、極端に重いProviderでも永久停止させない。
        if (state.waveCount > 0 && nanosPerOperation > remainingNanos) {
            recordBudgetStopOnce(state);
            return 0;
        }

        int waveOperations = calculateWaveOperations(
                sharedLimited,
                remainingNanos,
                nanosPerOperation,
                ACOConfig.getInstantPatternDispatchProbeOperations(),
                ACOConfig.getInstantPatternDispatchMaximumWaveOperations());
        if (waveOperations <= 0) {
            recordBudgetStopOnce(state);
            return 0;
        }

        return executeAndRecord(
                executionOwner, waveOperations, craftingService, gameTick, true, wave);
    }

    static int calculateWaveOperations(
            int requestedOperations,
            long remainingNanos,
            long nanosPerOperation,
            int probeOperations,
            int maximumWaveOperations) {
        if (requestedOperations <= 0 || remainingNanos <= 0L) {
            return 0;
        }
        int hardLimit = Math.min(requestedOperations, Math.max(1, maximumWaveOperations));
        if (nanosPerOperation <= 0L) {
            return Math.min(hardLimit, Math.max(1, probeOperations));
        }

        // 実測の揺れを吸収するため、残り時間の75%だけを次の波へ割り当てる。
        long usableNanos = Math.max(1L, remainingNanos - remainingNanos / 4L);
        long predicted = Math.max(1L, usableNanos / nanosPerOperation);
        return (int) Math.min(hardLimit, Math.min(Integer.MAX_VALUE, predicted));
    }

    public static void clear() {
        synchronized (CPU_STATES) {
            CPU_STATES.clear();
        }
    }

    private static int executeAndRecord(
            Object executionOwner,
            int requestedOperations,
            CraftingService craftingService,
            long gameTick,
            boolean instant,
            CraftingWave wave) {
        long startedAt = System.nanoTime();
        int completedOperations = 0;
        try {
            completedOperations = wave.execute(requestedOperations);
            return completedOperations;
        } finally {
            long elapsedNanos = Math.max(1L, System.nanoTime() - startedAt);
            CraftingExecutionBudget.recordExecution(
                    executionOwner, requestedOperations, completedOperations, elapsedNanos);
            CraftingExecutionBudget.recordSharedExecution(
                    craftingService, executionOwner, gameTick, elapsedNanos);
            if (instant) {
                CpuTickState state = stateFor(executionOwner);
                state.beginTick(gameTick);
                state.consumedNanos = saturatingAdd(state.consumedNanos, elapsedNanos);
                state.waveCount++;
                OptimizationMetrics.recordSequentialInstantWave(
                        requestedOperations, completedOperations, elapsedNanos);
            }
        }
    }

    private static CpuTickState stateFor(Object executionOwner) {
        Object key = executionOwner != null ? executionOwner : SequentialInstantDispatcher.class;
        synchronized (CPU_STATES) {
            return CPU_STATES.computeIfAbsent(key, unused -> new CpuTickState());
        }
    }

    private static void recordBudgetStopOnce(CpuTickState state) {
        if (!state.budgetStopRecorded) {
            state.budgetStopRecorded = true;
            OptimizationMetrics.recordSequentialInstantBudgetStop();
        }
    }

    private static long millisecondsToNanos(int milliseconds) {
        return Math.multiplyExact((long) milliseconds, 1_000_000L);
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static final class CpuTickState {
        private long gameTick = Long.MIN_VALUE;
        private long consumedNanos;
        private int waveCount;
        private boolean budgetStopRecorded;

        private void beginTick(long currentGameTick) {
            if (gameTick == currentGameTick) {
                return;
            }
            gameTick = currentGameTick;
            consumedNanos = 0L;
            waveCount = 0;
            budgetStopRecorded = false;
        }
    }
}
