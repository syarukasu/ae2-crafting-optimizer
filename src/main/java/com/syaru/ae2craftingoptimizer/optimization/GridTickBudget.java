package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.TickRateModulation;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class GridTickBudget {
    private static final Map<Object, TickableState> STATES = Collections.synchronizedMap(new WeakHashMap<>());
    private static long budgetTick = Long.MIN_VALUE;
    private static long usedBudgetNanos;

    private GridTickBudget() {
    }

    public static TickRateModulation beforeTick(Object tickable, IGridNode node, int ticksSinceLastCall, long currentTick) {
        if (!ACOConfig.deferHeavyGridTickables() || !matchesHeavyTickable(tickable)) {
            return null;
        }

        resetBudgetIfNeeded(currentTick);
        TickableState state = getState(tickable);

        // Buses and processing machines carry persistent progress. Cancelling their scheduled
        // call can repeatedly move them to AE2's slower queue and starve devices that happen
        // to be visited late. Their work is bounded at the operation/recipe layer instead.
        if (isProgressSensitive(tickable)) {
            state.startedAtNanos = System.nanoTime();
            return null;
        }

        if (currentTick < state.deferUntilTick) {
            logDeferred(tickable, node, currentTick, state.deferReason);
            return TickRateModulation.SLOWER;
        }

        int minimumInterval = ACOConfig.getGridTickMinimumIntervalTicks();
        if (minimumInterval > 1 && state.lastRunTick != Long.MIN_VALUE
                && currentTick - state.lastRunTick < minimumInterval) {
            logDeferred(tickable, node, currentTick, "minimum-interval");
            return TickRateModulation.SLOWER;
        }

        long budgetNanos = ACOConfig.getGridTickBudgetMillisPerServerTick() * 1_000_000L;
        if (usedBudgetNanos >= budgetNanos) {
            logDeferred(tickable, node, currentTick, "tick-budget");
            return TickRateModulation.SLOWER;
        }

        state.startedAtNanos = System.nanoTime();
        return null;
    }

    public static void afterTick(Object tickable, IGridNode node, long currentTick, TickRateModulation modulation) {
        if (!ACOConfig.enableGridTickBudget() || !matchesHeavyTickable(tickable)) {
            return;
        }

        TickableState state = getState(tickable);
        long startedAtNanos = state.startedAtNanos;
        state.startedAtNanos = 0L;
        if (startedAtNanos <= 0L) {
            return;
        }

        long elapsedNanos = Math.max(0L, System.nanoTime() - startedAtNanos);
        state.lastRunTick = currentTick;
        resetBudgetIfNeeded(currentTick);
        usedBudgetNanos += elapsedNanos;

        if (isProgressSensitive(tickable)) {
            state.consecutiveIdleReturns = 0;
            state.deferUntilTick = Long.MIN_VALUE;
            return;
        }

        updateIdleBackoff(tickable, node, currentTick, modulation, state);

        long slowNanos = ACOConfig.getSlowGridTickableMicros() * 1_000L;
        if (elapsedNanos >= slowNanos) {
            int backoffTicks = ACOConfig.getSlowGridTickableBackoffTicks();
            if (backoffTicks > 0 && modulation != TickRateModulation.SLEEP) {
                state.deferUntilTick = Math.max(state.deferUntilTick, currentTick + backoffTicks);
                state.deferReason = "slow-backoff";
            }
            logSlow(tickable, node, currentTick, elapsedNanos, modulation);
        }
    }

    public static int limitIoBusOperations(Object bus, int originalOperations) {
        if (!ACOConfig.limitIoBusOperationsPerTick() || originalOperations <= 0) {
            return originalOperations;
        }

        int maximum = ACOConfig.getMaxIoBusOperationsPerTick();
        if (originalOperations <= maximum) {
            return originalOperations;
        }

        if (ACOConfig.logGridTickBudget()) {
            AE2CraftingOptimizer.LOGGER.debug(
                    "Capped AE2 IO bus operations for {} from {} to {}",
                    className(bus),
                    originalOperations,
                    maximum);
        }
        return maximum;
    }

    private static void updateIdleBackoff(
            Object tickable,
            IGridNode node,
            long currentTick,
            TickRateModulation modulation,
            TickableState state) {
        if (!ACOConfig.backoffIdleGridTickables()) {
            state.consecutiveIdleReturns = 0;
            return;
        }

        if (modulation == TickRateModulation.SLOWER || modulation == TickRateModulation.IDLE) {
            state.consecutiveIdleReturns++;
            if (state.consecutiveIdleReturns >= ACOConfig.getIdleGridTickableBackoffAfterFailures()) {
                state.deferUntilTick = Math.max(
                        state.deferUntilTick,
                        currentTick + ACOConfig.getIdleGridTickableBackoffTicks());
                state.deferReason = "idle-backoff";
                state.consecutiveIdleReturns = 0;
                logDeferred(tickable, node, currentTick, "idle-backoff");
            }
        } else {
            state.consecutiveIdleReturns = 0;
        }
    }

    public static void clear(String reason) {
        synchronized (STATES) {
            STATES.clear();
        }
        budgetTick = Long.MIN_VALUE;
        usedBudgetNanos = 0L;
        if (ACOConfig.logGridTickBudget()) {
            AE2CraftingOptimizer.LOGGER.debug("Cleared AE2 grid tick budget state: {}", reason);
        }
    }

    private static TickableState getState(Object tickable) {
        synchronized (STATES) {
            return STATES.computeIfAbsent(tickable, unused -> new TickableState());
        }
    }

    private static void resetBudgetIfNeeded(long currentTick) {
        if (budgetTick != currentTick) {
            budgetTick = currentTick;
            usedBudgetNanos = 0L;
        }
    }

    private static boolean matchesHeavyTickable(Object tickable) {
        if (tickable == null) {
            return false;
        }

        String name = className(tickable).toLowerCase();
        for (String hint : ACOConfig.getHeavyGridTickableClassHints()) {
            if ("*".equals(hint) || name.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isProgressSensitive(Object tickable) {
        String name = className(tickable).toLowerCase();
        return name.contains("importbus")
                || name.contains("exportbus")
                || name.contains("circuitcutter");
    }

    private static void logSlow(
            Object tickable,
            IGridNode node,
            long currentTick,
            long elapsedNanos,
            TickRateModulation modulation) {
        if (!ACOConfig.logGridTickBudget()) {
            return;
        }

        TickableState state = getState(tickable);
        if (currentTick - state.lastSlowLogTick < 100) {
            return;
        }
        state.lastSlowLogTick = currentTick;

        AE2CraftingOptimizer.LOGGER.debug(
                "Slow AE2 grid tickable {} took {} us, modulation {}, node {}",
                className(tickable),
                elapsedNanos / 1_000L,
                modulation,
                node);
    }

    private static void logDeferred(Object tickable, IGridNode node, long currentTick, String reason) {
        if (!ACOConfig.logGridTickBudget()) {
            return;
        }

        TickableState state = getState(tickable);
        if (currentTick - state.lastDeferredLogTick < 100) {
            return;
        }
        state.lastDeferredLogTick = currentTick;

        AE2CraftingOptimizer.LOGGER.debug(
                "Deferred AE2 grid tickable {} because of {}, node {}",
                className(tickable),
                reason,
                node);
    }

    private static String className(Object value) {
        return value == null ? "<null>" : value.getClass().getName();
    }

    private static final class TickableState {
        private long startedAtNanos;
        private long lastRunTick = Long.MIN_VALUE;
        private long deferUntilTick = Long.MIN_VALUE;
        private String deferReason = "backoff";
        private int consecutiveIdleReturns;
        private long lastSlowLogTick = Long.MIN_VALUE;
        private long lastDeferredLogTick = Long.MIN_VALUE;
    }
}
