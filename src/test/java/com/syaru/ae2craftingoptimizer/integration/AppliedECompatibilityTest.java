package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import gripe._90.appliede.me.misc.TransmutationPattern;
import java.lang.reflect.Proxy;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AppliedECompatibilityTest {
    @Test
    void recognizesSourceReviewedTransmutationPattern() {
        assertTrue(AppliedECompatibility.isTransmutationPatternClassName(
                "gripe._90.appliede.me.misc.TransmutationPattern"));
        assertFalse(AppliedECompatibility.isTransmutationPatternClassName(
                "example.appliede.TransmutationPattern"));
    }

    @Test
    void recognizesSourceReviewedEmcModuleProvider() {
        assertTrue(AppliedECompatibility.isEmcModuleProviderClassName(
                "gripe._90.appliede.part.EMCModulePart"));
        assertFalse(AppliedECompatibility.isEmcModuleProviderClassName(
                "gripe._90.appliede.part.EMCInterfacePart"));
    }

    @Test
    void temporaryPatternPlansRequireFreshCalculation() {
        assertTrue(AppliedECompatibility.containsTransmutationPattern(
                planWith(new TransmutationPattern())));

        IPatternDetails ordinary = (IPatternDetails) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {IPatternDetails.class},
                (proxy, method, arguments) -> null);
        assertFalse(AppliedECompatibility.containsTransmutationPattern(planWith(ordinary)));
    }

    private ICraftingPlan planWith(IPatternDetails pattern) {
        return (ICraftingPlan) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {ICraftingPlan.class},
                (proxy, method, arguments) -> "patternTimes".equals(method.getName())
                        ? Map.of(pattern, 1L)
                        : null);
    }
}
