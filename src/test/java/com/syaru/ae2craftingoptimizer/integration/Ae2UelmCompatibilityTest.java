package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Ae2UelmCompatibilityTest {
    @Test
    void recognizesThePublishedSharedIdVersionPair() {
        assertTrue(Ae2UelmCompatibility.isUelmVersion("15.5.0-uelm"));
        assertFalse(Ae2UelmCompatibility.isUelmVersion("15.5.0"));
        assertFalse(Ae2UelmCompatibility.isUelmVersion("ae2_uelm"));
    }

    @Test
    void acceptsBothSupportedDependencyProfiles() {
        assertTrue(Ae2UelmCompatibility.isSupportedAe2Version("15.4.10"));
        assertTrue(Ae2UelmCompatibility.isSupportedAe2Version("15.5.0-uelm"));
        assertFalse(Ae2UelmCompatibility.isSupportedAe2Version("15.5.0"));
    }

    @Test
    void delegatesOnlyTheVerifiedLongSurface() {
        assertTrue(Ae2UelmCompatibility.ownsAe2SurfaceMixin(
                "com.syaru.ae2craftingoptimizer.mixin.CraftAmountMenuLongAmountMixin"));
        assertTrue(Ae2UelmCompatibility.ownsAe2SurfaceMixin(
                "com.syaru.ae2craftingoptimizer.mixin.CraftConfirmMenuLongAmountMixin"));
        assertTrue(Ae2UelmCompatibility.ownsAe2SurfaceMixin(
                "com.syaru.ae2craftingoptimizer.mixin.CraftAmountScreenLongAmountMixin"));
        assertFalse(Ae2UelmCompatibility.ownsAe2SurfaceMixin(
                "NetworkStorageBigIntegerSnapshotMixin"));
        assertFalse(Ae2UelmCompatibility.ownsAe2SurfaceMixin(
                "com.syaru.ae2craftingoptimizer.mixin.NeoEcoCraftingCpuExecutionBudgetMixin"));
        assertFalse(Ae2UelmCompatibility.ownsAe2StorageSurface());
    }
}
