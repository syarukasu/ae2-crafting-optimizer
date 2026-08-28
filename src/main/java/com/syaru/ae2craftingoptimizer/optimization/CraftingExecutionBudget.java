package com.syaru.ae2craftingoptimizer.optimization;

import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import appeng.api.networking.crafting.ICraftingCPU;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import appeng.me.service.CraftingService;

public final class CraftingExecutionBudget {
    private static final Map<Object, AdaptiveState> ADAPTIVE_STATES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<CraftingService, SharedBudgetState> SHARED_STATES = Collections.synchronizedMap(new WeakHashMap<>());

    private CraftingExecutionBudget() {
    }

    public static int limitCoProcessors(Object executionOwner, ICraftingCPU cpu, int originalCoProcessors) {
        long exactCoProcessors = StandardAe2CoprocessorCountResolver.resolve(cpu, originalCoProcessors);
        int cappedCoProcessors = limitOperations(executionOwner, exactCoProcessors);
        if (exactCoProcessors > cappedCoProcessors && ACOConfig.logCraftingExecutionThrottling()) {
            AE2CraftingOptimizer.LOGGER.debug(
                    "Capped AE2 crafting execution for CPU {} from {} coprocessors to {} effective coprocessors",
                    cpu.getName().getString(),
                    exactCoProcessors,
                    cappedCoProcessors);
        }
        return cappedCoProcessors;
    }

    public static int limitExternalOperations(Object executionOwner, int originalOperations, String integrationName) {
        int cappedOperations = limitOperations(executionOwner, originalOperations);
        if (originalOperations > cappedOperations && ACOConfig.logCraftingExecutionThrottling()) {
            AE2CraftingOptimizer.LOGGER.debug(
                    "Capped {} crafting execution from {} to {} pattern operations",
                    integrationName,
                    originalOperations,
                    cappedOperations);
        }
        return cappedOperations;
    }

    private static int limitOperations(Object executionOwner, long originalOperations) {
        // 負数を「処理なし」として黙って通すとCPUが永久停止するため、生成元の不整合として拒否する。
        if (originalOperations < 0L) {
            throw new IllegalArgumentException(
                    "Crafting execution operation count must be non-negative: " + originalOperations);
        }

        int safelyRepresentable = safelyRepresentableOperations(originalOperations);
        if (!ACOConfig.throttleCraftingExecution()) {
            return safelyRepresentable;
        }

        int maxCoProcessors = ACOConfig.getMaxEffectiveCoprocessorsPerCpu();
        int cappedOperations = Math.min(safelyRepresentable, maxCoProcessors);

        if (ACOConfig.adaptiveCraftingExecutionBudget()) {
            cappedOperations = Math.min(cappedOperations, getAdaptiveCap(executionOwner));
        }
        return cappedOperations;
    }

    static int safelyRepresentableOperations(long originalOperations) {
        // AE2は直後に+1するため、int経路へ渡せる最大コプロセッサ数はMAX_VALUE-1である。
        if (originalOperations < 0L) {
            throw new IllegalArgumentException(
                    "Crafting execution operation count must be non-negative: " + originalOperations);
        }
        return (int) Math.min(
                originalOperations,
                (long) ACOConfig.MAX_SAFE_EFFECTIVE_COPROCESSORS);
    }

    static long measuredNanosPerOperation(int completedOperations, long elapsedNanos) {
        // 完了0件は高価な失敗探索一回として扱い、次tickも未計測の大波を繰り返さない。
        if (completedOperations <= 0) {
            return Math.max(1L, elapsedNanos);
        }
        return Math.max(1L, elapsedNanos / completedOperations);
    }

    public static void recordExecution(Object executionOwner, int requestedOperations, int completedOperations, long elapsedNanos) {
        if (!ACOConfig.adaptiveCraftingExecutionBudget() || requestedOperations <= 0 || elapsedNanos <= 0) {
            return;
        }

        Object key = keyFor(executionOwner);
        int hardCap = ACOConfig.getMaxEffectiveCoprocessorsPerCpu();
        int minimumCap = ACOConfig.getMinimumAdaptiveCoprocessorsPerCpu();
        long targetNanos = ACOConfig.getTargetCraftingExecutionMillis() * 1_000_000L;

        synchronized (ADAPTIVE_STATES) {
            AdaptiveState state = ADAPTIVE_STATES.computeIfAbsent(key, unused -> new AdaptiveState(hardCap));
            state.currentCap = clamp(state.currentCap, minimumCap, hardCap);
            /*
             * 完了0件でもProvider探索には実時間が掛かる。未記録のままだと毎tickをcold startとして
             * 同じ重いwaveを繰り返すため、0件時は一回の失敗探索全体を一操作分として保守的に記録する。
             */
            long measuredNanosPerOperation = measuredNanosPerOperation(
                    completedOperations,
                    elapsedNanos);
            state.nanosPerOperation = state.nanosPerOperation == 0L
                    ? measuredNanosPerOperation
                    : (state.nanosPerOperation * 7L + measuredNanosPerOperation) / 8L;

            if (elapsedNanos > targetNanos && state.currentCap > minimumCap) {
                state.currentCap = reduceBudget(state.currentCap, requestedOperations, elapsedNanos, targetNanos, minimumCap);
            } else if (elapsedNanos < targetNanos / 2 && completedOperations >= requestedOperations && state.currentCap < hardCap) {
                state.currentCap = increaseBudget(state.currentCap, hardCap);
            }
        }
    }

    public static int limitSharedOperations(
            CraftingService craftingService,
            Object executionOwner,
            int requestedOperations,
            long gameTick) {
        if (!ACOConfig.sharedCraftingExecutionBudget() || craftingService == null || requestedOperations <= 0) {
            return requestedOperations;
        }

        long targetNanos = ACOConfig.getSharedCraftingExecutionMillisPerGrid() * 1_000_000L;
        int minimumOperations = Math.min(requestedOperations, ACOConfig.getMinimumSharedOperationsPerCpu());
        long nanosPerOperation = estimatedNanosPerOperation(executionOwner);

        synchronized (SHARED_STATES) {
            SharedBudgetState state = SHARED_STATES.computeIfAbsent(craftingService, unused -> new SharedBudgetState());
            state.beginTick(gameTick);

            long remainingNanos = targetNanos - state.consumedNanos;
            if (remainingNanos <= 0L) {
                OptimizationMetrics.recordSharedBudgetLimit(requestedOperations, minimumOperations);
                return minimumOperations;
            }
            if (nanosPerOperation <= 0L) {
                return requestedOperations;
            }

            long predictedOperations = Math.max(minimumOperations, remainingNanos / nanosPerOperation);
            int limitedOperations = (int) Math.min(requestedOperations, Math.min(Integer.MAX_VALUE, predictedOperations));
            if (limitedOperations < requestedOperations) {
                OptimizationMetrics.recordSharedBudgetLimit(requestedOperations, limitedOperations);
            }
            return limitedOperations;
        }
    }

    public static void recordSharedExecution(
            CraftingService craftingService,
            Object executionOwner,
            long gameTick,
            long elapsedNanos) {
        if (!ACOConfig.sharedCraftingExecutionBudget() || craftingService == null || elapsedNanos <= 0L) {
            return;
        }
        synchronized (SHARED_STATES) {
            SharedBudgetState state = SHARED_STATES.computeIfAbsent(craftingService, unused -> new SharedBudgetState());
            state.beginTick(gameTick);
            state.consumedNanos = saturatingAdd(state.consumedNanos, elapsedNanos);
        }
    }

    /** Sequential Instantが次のAE2実行波を開始できるGrid共有残時間を返す。 */
    public static long remainingSharedBudgetNanos(CraftingService craftingService, long gameTick) {
        if (!ACOConfig.sharedCraftingExecutionBudget()
                || craftingService == null) {
            return Long.MAX_VALUE;
        }
        long targetNanos = ACOConfig.getSharedCraftingExecutionMillisPerGrid() * 1_000_000L;
        synchronized (SHARED_STATES) {
            SharedBudgetState state = SHARED_STATES.computeIfAbsent(craftingService, unused -> new SharedBudgetState());
            state.beginTick(gameTick);
            return Math.max(0L, targetNanos - state.consumedNanos);
        }
    }

    public static void clearAdaptiveState(String reason) {
        synchronized (ADAPTIVE_STATES) {
            ADAPTIVE_STATES.clear();
        }
        synchronized (SHARED_STATES) {
            SHARED_STATES.clear();
        }
        SequentialInstantDispatcher.clear();
        StandardAe2CoprocessorCountResolver.clear();
        if (ACOConfig.logCraftingExecutionThrottling()) {
            AE2CraftingOptimizer.LOGGER.debug("Cleared AE2 crafting execution adaptive state: {}", reason);
        }
    }

    private static int getAdaptiveCap(Object executionOwner) {
        Object key = keyFor(executionOwner);
        int hardCap = ACOConfig.getMaxEffectiveCoprocessorsPerCpu();
        int minimumCap = ACOConfig.getMinimumAdaptiveCoprocessorsPerCpu();

        synchronized (ADAPTIVE_STATES) {
            AdaptiveState state = ADAPTIVE_STATES.computeIfAbsent(key, unused -> new AdaptiveState(hardCap));
            state.currentCap = clamp(state.currentCap, minimumCap, hardCap);
            return state.currentCap;
        }
    }

    private static int reduceBudget(int currentCap, int requestedOperations, long elapsedNanos, long targetNanos, int minimumCap) {
        long scaled = Math.max(1L, (long) requestedOperations * targetNanos / elapsedNanos);
        int proportionalCap = (int) Math.min(Integer.MAX_VALUE, scaled);
        int fallbackCap = currentCap - Math.max(1, currentCap / 4);
        int nextCap = Math.min(proportionalCap, fallbackCap);
        return clamp(nextCap, minimumCap, currentCap);
    }

    private static int increaseBudget(int currentCap, int hardCap) {
        int increase = Math.max(1, currentCap / 8);
        long nextCap = (long) currentCap + increase;
        return (int) Math.min(hardCap, nextCap);
    }

    static long estimatedNanosPerOperation(Object executionOwner) {
        Object key = keyFor(executionOwner);
        synchronized (ADAPTIVE_STATES) {
            AdaptiveState state = ADAPTIVE_STATES.get(key);
            return state == null ? 0L : state.nanosPerOperation;
        }
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.min(maximum, Math.max(minimum, value));
    }

    private static Object keyFor(Object executionOwner) {
        return executionOwner != null ? executionOwner : CraftingExecutionBudget.class;
    }

    private static final class AdaptiveState {
        private int currentCap;
        private long nanosPerOperation;

        private AdaptiveState(int currentCap) {
            this.currentCap = currentCap;
        }
    }

    private static final class SharedBudgetState {
        private long gameTick = Long.MIN_VALUE;
        private long consumedNanos;

        private void beginTick(long currentGameTick) {
            if (gameTick == currentGameTick) {
                return;
            }
            gameTick = currentGameTick;
            consumedNanos = 0L;
        }
    }
}
