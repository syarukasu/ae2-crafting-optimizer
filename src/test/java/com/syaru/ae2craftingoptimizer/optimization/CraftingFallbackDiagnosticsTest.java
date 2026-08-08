package com.syaru.ae2craftingoptimizer.optimization;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CraftingFallbackDiagnosticsTest {
    @AfterEach
    void clearThreadState() {
        CraftingFallbackDiagnostics.reset();
    }

    @Test
    void keepsTheFirstReasonAndGenerationForOneCalculation() {
        CraftingFallbackDiagnostics.record(null, 7L, 11L, FallbackReasonCode.INVENTORY_CHANGED);
        CraftingFallbackDiagnostics.record(null, 8L, 12L, FallbackReasonCode.UNKNOWN);

        CraftingFallbackDiagnostics.Observation observation = CraftingFallbackDiagnostics.take();
        assertEquals(FallbackReasonCode.INVENTORY_CHANGED, observation.code());
        assertEquals(7L, observation.patternGeneration());
        assertEquals(11L, observation.recipeGeneration());
        assertEquals(1L, observation.aggregateCount());
    }
}
