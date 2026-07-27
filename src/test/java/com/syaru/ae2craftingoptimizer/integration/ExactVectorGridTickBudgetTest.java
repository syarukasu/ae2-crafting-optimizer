package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExactVectorGridTickBudgetTest {
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
    void aBusyGridGuaranteesOnlyOneStageForItsFirstRange() {
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
    void defersAdditionalRangesAfterTheSoftBudget() {
        assertEquals(
                0,
                ExactVectorGridTickBudget.claimOperations(
                        20,
                        256,
                        20,
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
