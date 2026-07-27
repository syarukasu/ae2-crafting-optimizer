package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Ae2AuthoritativeCraftingPlannerPolicyTest {
    @Test
    void retainsLongFacadeWhenFullExpansionNeedsWideArithmetic() {
        assertTrue(Ae2AuthoritativeCraftingPlanner.shouldRetainLongFacade(
                false,
                true));
    }

    @Test
    void leavesOrdinaryLongPlanOnAe2WhenAuthoritativePlannerIsDisabled() {
        assertFalse(Ae2AuthoritativeCraftingPlanner.shouldRetainLongFacade(
                false,
                false));
    }

    @Test
    void retainsOrdinaryLongPlanWhenAuthoritativePlannerIsEnabled() {
        assertTrue(Ae2AuthoritativeCraftingPlanner.shouldRetainLongFacade(
                true,
                false));
    }
}
