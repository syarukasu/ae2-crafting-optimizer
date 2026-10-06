package com.syaru.ae2craftingoptimizer.api.big;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import org.junit.jupiter.api.Test;

class BigCraftingEngineApiCalculationProfileTest {
    @Test void retiredGraphSwitchDoesNotHideTheActiveExactApi() {
        try (var config = mockStatic(ACOConfig.class)) {
            config.when(ACOConfig::enableBigIntegerCraftingBackend).thenReturn(true);
            config.when(ACOConfig::enableCompiledCraftingGraph).thenReturn(false);
            assertTrue(BigCraftingEngineApi.isCalculationProfileActive());
        }
    }

    @Test void disablingTheExactBackendStillDisablesItsCapability() {
        try (var config = mockStatic(ACOConfig.class)) {
            config.when(ACOConfig::enableBigIntegerCraftingBackend).thenReturn(false);
            config.when(ACOConfig::enableCompiledCraftingGraph).thenReturn(true);
            assertFalse(BigCraftingEngineApi.isCalculationProfileActive());
        }
    }
}
