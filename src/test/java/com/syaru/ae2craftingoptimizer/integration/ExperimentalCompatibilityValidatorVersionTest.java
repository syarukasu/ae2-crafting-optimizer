package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class ExperimentalCompatibilityValidatorVersionTest {
    @Test
    void validatorTargetsThePortedAe2Runtime() {
        assertEquals("19.2.17", ExperimentalCompatibilityValidator.SUPPORTED_AE2_VERSION);
        assertNotEquals("15.4.10", ExperimentalCompatibilityValidator.SUPPORTED_AE2_VERSION);
    }

    @Test
    void validatorTargetsThePortedAdvancedAeRuntime() {
        assertEquals(
                "1.6.11-1.21.1",
                ExperimentalCompatibilityValidator.SUPPORTED_ADVANCED_AE_VERSION);
        assertNotEquals(
                "1.3.5-1.20.1",
                ExperimentalCompatibilityValidator.SUPPORTED_ADVANCED_AE_VERSION);
    }
}
