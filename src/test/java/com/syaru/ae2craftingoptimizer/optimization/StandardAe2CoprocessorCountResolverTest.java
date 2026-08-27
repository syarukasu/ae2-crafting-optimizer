package com.syaru.ae2craftingoptimizer.optimization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class StandardAe2CoprocessorCountResolverTest {
    @Test
    void negativeWrappedReportUsesWideClusterTotal() {
        long exact = (long) Integer.MAX_VALUE + 4_096L;
        assertEquals(exact, StandardAe2CoprocessorCountResolver.reconcile(-2_147_479_553, exact));
    }

    @Test
    void positiveRewrappedReportUsesWideClusterTotal() {
        long exact = (1L << 32) + 64L;
        assertEquals(exact, StandardAe2CoprocessorCountResolver.reconcile(64, exact));
    }

    @Test
    void healthyExternalIntContributionIsPreserved() {
        assertEquals(4_096L, StandardAe2CoprocessorCountResolver.reconcile(4_096, 1_024L));
    }

    @Test
    void unexplainedNegativeReportFailsClosed() {
        assertThrows(
                IllegalStateException.class,
                () -> StandardAe2CoprocessorCountResolver.reconcile(-1, 1_024L));
    }

    @Test
    void executionWindowLeavesRoomForAe2PlusOne() {
        assertEquals(
                Integer.MAX_VALUE - 1,
                CraftingExecutionBudget.safelyRepresentableOperations(Long.MAX_VALUE));
        assertEquals(4_096, CraftingExecutionBudget.safelyRepresentableOperations(4_096L));
        assertThrows(
                IllegalArgumentException.class,
                () -> CraftingExecutionBudget.safelyRepresentableOperations(-1L));
    }

    @Test
    void zeroProgressWaveStillPublishesItsObservedCost() {
        assertEquals(
                80_000_000L,
                CraftingExecutionBudget.measuredNanosPerOperation(0, 80_000_000L));
        assertEquals(
                2_000_000L,
                CraftingExecutionBudget.measuredNanosPerOperation(4, 8_000_000L));
    }
}
