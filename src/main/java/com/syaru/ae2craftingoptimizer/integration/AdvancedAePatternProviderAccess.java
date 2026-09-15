package com.syaru.ae2craftingoptimizer.integration;

import appeng.api.crafting.IPatternDetails;
import net.pedroksl.advanced_ae.common.patterns.AdvPatternDetails;

/** Isolates direct Advanced AE references so common code remains safe without the optional mod. */
public final class AdvancedAePatternProviderAccess {
    private AdvancedAePatternProviderAccess() {
    }

    public static boolean hasDirectionalInputs(IPatternDetails pattern) {
        return pattern instanceof AdvPatternDetails advancedPattern
                && advancedPattern.directionalInputsSet();
    }
}
