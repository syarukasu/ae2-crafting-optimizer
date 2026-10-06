package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.access.ExactCraftingJobAccess;

/** Native count compatibility and exact-job ownership; no ordinary execution budget. */
public final class CraftingExecutionBudget {
    private CraftingExecutionBudget() {
    }

    /** Issue #125: receipt-owned tasks must not also run in a native batch. */
    public static int nativeTickOperations(Object job, int availableOperations) {
        if (job instanceof ExactCraftingJobAccess exact && exact.aco$isExactJob()) {
            return 0;
        }
        return availableOperations;
    }

    public static int limitCoProcessors(Object executionOwner, ICraftingCPU cpu, int originalCoProcessors) {
        return safelyRepresentableOperations(
                StandardAe2CoprocessorCountResolver.resolve(cpu, originalCoProcessors));
    }

    static int safelyRepresentableOperations(long originalOperations) {
        if (originalOperations < 0L) {
            throw new IllegalArgumentException(
                    "Crafting execution operation count must be non-negative: " + originalOperations);
        }
        // Native AE2 adds one to the int coprocessor count, not to the exact job quantity.
        return (int) Math.min(originalOperations, (long) Integer.MAX_VALUE - 1L);
    }

    // Retain legacy public descriptors without timers, state or throughput limits.
    public static int limitExternalOperations(Object executionOwner, int originalOperations, String integrationName) {
        return originalOperations;
    }

    public static void recordExecution(Object executionOwner, int requestedOperations, int completedOperations, long elapsedNanos) {
    }

    public static int limitSharedOperations(
            CraftingService craftingService,
            Object executionOwner,
            int requestedOperations,
            long gameTick) {
        return requestedOperations;
    }

    public static void recordSharedExecution(
            CraftingService craftingService,
            Object executionOwner,
            long gameTick,
            long elapsedNanos) {
    }

    public static long remainingSharedBudgetNanos(CraftingService craftingService, long gameTick) {
        return Long.MAX_VALUE;
    }

    public static void clearAdaptiveState(String reason) {
        StandardAe2CoprocessorCountResolver.clear();
    }
}
