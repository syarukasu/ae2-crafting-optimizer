package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Issue #109: BigInteger APIの有効化だけで通常long計画を置換しないことを固定する。 */
class Issue109NormalAe2BoundaryTest {
    @Test
    void disabledReplacementPoliciesKeepNormalAe2() {
        assertFalse(Ae2AuthoritativeCraftingPlanner.normalLongReplacementEnabled(false, false));
    }

    @Test
    void eitherExplicitStrictPolicyMayEnableNormalReplacement() {
        assertTrue(Ae2AuthoritativeCraftingPlanner.normalLongReplacementEnabled(true, false));
        assertTrue(Ae2AuthoritativeCraftingPlanner.normalLongReplacementEnabled(false, true));
    }
}
