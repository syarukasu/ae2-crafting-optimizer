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
        if (!ACOConfig.throttleCraftingExecution()) {
            return originalCoProcessors;
        }

        int maxCoProcessors = ACOConfig.getMaxEffectiveCoprocessorsPerCpu();
        int cappedCoProcessors = Math.min(originalCoProcessors, maxCoProcessors);

        if (ACOConfig.adaptiveCraftingExecutionBudget()) {
            cappedCoProcessors = Math.min(cappedCoProcessors, getAdaptiveCap(executionOwner));
        }

        if (originalCoProcessors <= cappedCoProcessors) {
            return originalCoProcessors;
        }

        if (ACOConfig.logCraftingExecutionThrottling()) {
            AE2CraftingOptimizer.LOGGER.debug(
                    "Capped AE2 crafting execution for CPU {} from {} coprocessors to {} effective coprocessors",
                    cpu.getName().getString(),
                    originalCoProcessors,
                    cappedCoProcessors);
        }
        return cappedCoProcessors;
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
            if (completedOperations > 0) {
                long measuredNanosPerOperation = Math.max(1L, elapsedNanos / completedOperations);
                state.nanosPerOperation = state.nanosPerOperation == 0L
                        ? measuredNanosPerOperation
                        : (state.nanosPerOperation * 7L + measuredNanosPerOperation) / 8L;
            }

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

    public static void recordSharedExecution(CraftingService craftingService, long gameTick, long elapsedNanos) {
        if (!ACOConfig.sharedCraftingExecutionBudget() || craftingService == null || elapsedNanos <= 0L) {
            return;
        }
        synchronized (SHARED_STATES) {
            SharedBudgetState state = SHARED_STATES.computeIfAbsent(craftingService, unused -> new SharedBudgetState());
            state.beginTick(gameTick);
            state.consumedNanos = saturatingAdd(state.consumedNanos, elapsedNanos);
        }
    }

    public static void clearAdaptiveState(String reason) {
        synchronized (ADAPTIVE_STATES) {
            ADAPTIVE_STATES.clear();
        }
        synchronized (SHARED_STATES) {
            SHARED_STATES.clear();
        }
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

    private static long estimatedNanosPerOperation(Object executionOwner) {
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
