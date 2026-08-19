package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Issue #109: BigInteger連携だけで通常long計画を置換しないことを固定する。 */
class Issue109NormalAe2BoundaryTest {
    @Test
    void disabledExperimentalEngineKeepsNormalAe2EvenWhenReplacementFlagsRemainEnabled() {
        assertFalse(Ae2AuthoritativeCraftingPlanner.normalLongReplacementEnabled(
                false, true, true));
    }

    @Test
    void explicitExperimentalEngineMayEnableEitherNormalReplacementPolicy() {
        assertTrue(Ae2AuthoritativeCraftingPlanner.normalLongReplacementEnabled(
                true, true, false));
        assertTrue(Ae2AuthoritativeCraftingPlanner.normalLongReplacementEnabled(
                true, false, true));
        assertFalse(Ae2AuthoritativeCraftingPlanner.normalLongReplacementEnabled(
                true, false, false));
    }
}
