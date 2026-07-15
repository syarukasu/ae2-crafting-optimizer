package com.syaru.ae2craftingoptimizer.optimization;

import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import appeng.api.networking.crafting.ICraftingCPU;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class CraftingExecutionBudget {
    private static final Map<Object, AdaptiveState> ADAPTIVE_STATES = Collections.synchronizedMap(new WeakHashMap<>());

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

            if (elapsedNanos > targetNanos && state.currentCap > minimumCap) {
                state.currentCap = reduceBudget(state.currentCap, requestedOperations, elapsedNanos, targetNanos, minimumCap);
            } else if (elapsedNanos < targetNanos / 2 && completedOperations >= requestedOperations && state.currentCap < hardCap) {
                state.currentCap = increaseBudget(state.currentCap, hardCap);
            }
        }
    }

    public static void clearAdaptiveState(String reason) {
        synchronized (ADAPTIVE_STATES) {
            ADAPTIVE_STATES.clear();
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

    private static int clamp(int value, int minimum, int maximum) {
        return Math.min(maximum, Math.max(minimum, value));
    }

    private static Object keyFor(Object executionOwner) {
        return executionOwner != null ? executionOwner : CraftingExecutionBudget.class;
    }

    private static final class AdaptiveState {
        private int currentCap;

        private AdaptiveState(int currentCap) {
            this.currentCap = currentCap;
        }
    }
}
