package com.syaru.ae2craftingoptimizer.optimization;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CraftingCalculationMemoPolicyTest {
    @Test
    void onlyKnownAe2InputsShareReadOnlyMetadataWithinOneCalculation() {
        assertTrue(CraftingCalculationMemo.isMemoizableInputType(
                "appeng.crafting.pattern.AECraftingPattern$Input"));
        assertTrue(CraftingCalculationMemo.isMemoizableInputType(
                "appeng.crafting.pattern.AEProcessingPattern$Input"));
        assertTrue(CraftingCalculationMemo.isMemoizableInputType(
                "appeng.crafting.pattern.AEStonecuttingPattern$Input"));
        assertTrue(CraftingCalculationMemo.isMemoizableInputType(
                "appeng.crafting.pattern.AESmithingTablePattern$Input"));
        assertFalse(CraftingCalculationMemo.isMemoizableInputType(
                "example.addon.DynamicInput"));
    }

    @Test
    void onlyProcessingInputSharesPureValidationResults() {
        assertTrue(CraftingCalculationMemo.isPureValidationInputType(
                "appeng.crafting.pattern.AEProcessingPattern$Input"));
        assertFalse(CraftingCalculationMemo.isPureValidationInputType(
                "appeng.crafting.pattern.AECraftingPattern$Input"));
        assertFalse(CraftingCalculationMemo.isPureValidationInputType(
                "example.addon.DynamicInput"));
    }
}
