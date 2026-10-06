package com.syaru.ae2craftingoptimizer.optimization;

import appeng.me.service.CraftingService;

/** Binary-compatible entry for former wave callers; native execution owns dispatch. */
public final class SequentialInstantDispatcher {
    private SequentialInstantDispatcher() {
    }

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
        return wave.execute(requestedOperations);
    }

    public static void clear() {
    }
}
