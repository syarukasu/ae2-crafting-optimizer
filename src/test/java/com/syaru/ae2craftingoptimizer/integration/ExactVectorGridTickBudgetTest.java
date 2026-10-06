package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import appeng.api.networking.IGrid;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import org.junit.jupiter.api.Test;

class ExactVectorGridTickBudgetTest {
    @Test
    void returningUnusedClaimCannotRenewTheFirstOperationTimeException() {
        try (var config = mockStatic(ACOConfig.class)) {
            config.when(ACOConfig::getExactVectorGridTimeBudgetMillis).thenReturn(0);
            config.when(ACOConfig::getExactVectorMaximumActiveStagesPerGridTick).thenReturn(256);
            ExactVectorGridTickBudget.clearAll();
            IGrid grid = mock(IGrid.class);
            assertEquals(1, ExactVectorGridTickBudget.claimActiveStages(grid, 8));
            ExactVectorGridTickBudget.settleActiveStageClaim(grid, 1, 0);
            assertEquals(0, ExactVectorGridTickBudget.claimActiveStages(grid, 8));
        } finally {
            ExactVectorGridTickBudget.clearAll();
        }
    }

    @Test
    void smallJobsCannotBypassAnExhaustedSharedTimeBudget() {
        for (int requested = 1; requested <= 64; requested++) {
            assertEquals(0, ExactVectorGridTickBudget.claimOperations(
                    20, 256, requested, 2L, 2L));
        }
    }

    @Test
    void firstOperationSurvivesAnAlreadyExceededTimeBudget() {
        assertFalse(ExactVectorGridTickBudget.shouldDeferOperation(
                0,
                1,
                Long.MAX_VALUE,
                1L));
    }

    @Test
    void countLimitStillStopsTheFirstOperationWhenConfiguredToZero() {
        assertTrue(ExactVectorGridTickBudget.shouldDeferOperation(
                0,
                0,
                0L,
                1L));
    }

    @Test
    void elapsedBudgetDefersOnlyAdditionalOperations() {
        assertFalse(ExactVectorGridTickBudget.shouldDeferOperation(
                1,
                4,
                1L,
                2L));
        assertTrue(ExactVectorGridTickBudget.shouldDeferOperation(
                1,
                4,
                2L,
                2L));
    }

    @Test
    void claimsAWholeTwentyStageRangeWhenTheGridIsIdle() {
        assertEquals(
                20,
                ExactVectorGridTickBudget.claimOperations(
                        0,
                        256,
                        20,
                        0L,
                        2L));
    }

    @Test
    void aBusyGridKeepsOneStageOfProgressWithoutGrantingTheWholeTree() {
        assertEquals(
                1,
                ExactVectorGridTickBudget.claimOperations(
                        0,
                        256,
                        20,
                        2L,
                        2L));
    }

    @Test
    void defersAdditionalLargeRangesAfterTheSoftBudget() {
        assertEquals(
                0,
                ExactVectorGridTickBudget.claimOperations(
                        20,
                        256,
                        65,
                        2L,
                        2L));
    }

    @Test
    void aLargeFirstRangeKeepsOneStageStarvationProtection() {
        assertEquals(
                1,
                ExactVectorGridTickBudget.claimOperations(
                        0,
                        256,
                        65,
                        2L,
                        2L));
    }

    @Test
    void clampsAClaimToTheRemainingGridStageCapacity() {
        assertEquals(
                6,
                ExactVectorGridTickBudget.claimOperations(
                        250,
                        256,
                        20,
                        0L,
                        2L));
    }
}
