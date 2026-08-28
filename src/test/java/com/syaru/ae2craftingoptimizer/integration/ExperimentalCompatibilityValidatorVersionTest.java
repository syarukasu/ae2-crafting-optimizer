package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ExperimentalCompatibilityValidatorVersionTest {
    @Test
    void validatorTargetsThePortedAe2Runtime() {
        assertEquals("19.2.17", ExperimentalCompatibilityValidator.SUPPORTED_AE2_VERSION);
    }
}
