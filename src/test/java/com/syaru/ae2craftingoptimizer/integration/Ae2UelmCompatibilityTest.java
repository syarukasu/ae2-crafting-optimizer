package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Ae2UelmCompatibilityTest {
    @Test
    void recognizesKnownUelmIdsWithoutRequiringTheMod() {
        assertTrue(Ae2UelmCompatibility.isKnownUelmModId("ae2_uelm"));
        assertTrue(Ae2UelmCompatibility.isKnownUelmModId("ae2uelm"));
        assertTrue(Ae2UelmCompatibility.isKnownUelmModId("ae2_uel"));
        assertTrue(Ae2UelmCompatibility.isKnownUelmModId("ae2uel"));
        assertFalse(Ae2UelmCompatibility.isKnownUelmModId("ae2"));
    }

    @Test
    void delegatesOnlyTheAe2OwnedSurface() {
        assertTrue(Ae2UelmCompatibility.ownsAe2SurfaceMixin(
                "com.syaru.ae2craftingoptimizer.mixin.CraftAmountMenuLongAmountMixin"));
        assertTrue(Ae2UelmCompatibility.ownsAe2SurfaceMixin(
                "NetworkStorageBigIntegerSnapshotMixin"));
        assertFalse(Ae2UelmCompatibility.ownsAe2SurfaceMixin(
                "com.syaru.ae2craftingoptimizer.mixin.NeoEcoCraftingCpuExecutionBudgetMixin"));
    }
}
