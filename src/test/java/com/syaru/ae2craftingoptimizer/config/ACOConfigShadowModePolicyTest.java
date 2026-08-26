package com.syaru.ae2craftingoptimizer.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ACOConfigShadowModePolicyTest {
    @Test
    void disablesShadowWorkWhenNoConsumerCanUseIt() {
        assertFalse(ACOConfig.shouldEnableCraftingEngineShadowMode(
                true,
                true,
                false,
                false));
    }

    @Test
    void enablesShadowWorkForExperimentalNormalReplacement() {
        assertTrue(ACOConfig.shouldEnableCraftingEngineShadowMode(
                true,
                true,
                true,
                false));
    }

    @Test
    void enablesShadowWorkWhenWidePlansRequireQualification() {
        assertTrue(ACOConfig.shouldEnableCraftingEngineShadowMode(
                true,
                true,
                false,
                true));
    }
}
