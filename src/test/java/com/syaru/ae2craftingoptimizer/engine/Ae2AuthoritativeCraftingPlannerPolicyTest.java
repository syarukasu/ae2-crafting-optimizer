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

    @Test
    void acceptsStrictlyProvenLongPlanWithoutShadowHistory() {
        assertTrue(Ae2AuthoritativeCraftingPlanner.isQualifiedForReplacement(
                false,
                true,
                false,
                false));
    }

    @Test
    void rejectsOrdinaryLongPlanWhenNeitherProofNorShadowQualifiesIt() {
        assertFalse(Ae2AuthoritativeCraftingPlanner.isQualifiedForReplacement(
                false,
                false,
                false,
                false));
    }

    @Test
    void honorsWidePlanShadowRequirement() {
        assertFalse(Ae2AuthoritativeCraftingPlanner.isQualifiedForReplacement(
                false,
                true,
                true,
                true));
        assertTrue(Ae2AuthoritativeCraftingPlanner.isQualifiedForReplacement(
                false,
                false,
                true,
                false));
    }
}
