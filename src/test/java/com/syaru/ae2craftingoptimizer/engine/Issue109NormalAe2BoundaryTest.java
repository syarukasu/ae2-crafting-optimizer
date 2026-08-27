package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Issue #109/#156: BigInteger連携と、明示的な厳密long Plannerを分離する。 */
class Issue109NormalAe2BoundaryTest {
    @Test
    void bigIntegerOrLegacyFlagsDoNotEnableNormalReplacementByThemselves() {
        assertFalse(Ae2AuthoritativeCraftingPlanner.normalLongReplacementEnabled(
                false, false, true, true));
    }

    @Test
    void strictDeterministicPlannerMayRunWithoutTheExperimentalEngine() {
        assertTrue(Ae2AuthoritativeCraftingPlanner.normalLongReplacementEnabled(
                true, false, false, false));
    }

    @Test
    void explicitExperimentalEngineMayEnableEitherNormalReplacementPolicy() {
        assertTrue(Ae2AuthoritativeCraftingPlanner.normalLongReplacementEnabled(
                false, true, true, false));
        assertTrue(Ae2AuthoritativeCraftingPlanner.normalLongReplacementEnabled(
                false, true, false, true));
        assertFalse(Ae2AuthoritativeCraftingPlanner.normalLongReplacementEnabled(
                false, true, false, false));
    }
}
