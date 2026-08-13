package com.syaru.ae2craftingoptimizer.mixin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AcoMixinPluginTest {
    @Test
    void keepsTransactionStateApiWhenInsaneAeIsInstalled() {
        assertFalse(AcoMixinPlugin.isInsaneAeOwnedExecutionMixin(
                "com.syaru.ae2craftingoptimizer.mixin.CraftingCpuLogicBatchSourceReceiptMixin"));
        assertFalse(AcoMixinPlugin.isInsaneAeOwnedExecutionMixin(
                "com.syaru.ae2craftingoptimizer.mixin.AdvancedAeCraftingCpuLogicBatchSourceReceiptMixin"));
    }

    @Test
    void delegatesOnlyExecutionHooksToInsaneAe() {
        assertTrue(AcoMixinPlugin.isInsaneAeOwnedExecutionMixin(
                "com.syaru.ae2craftingoptimizer.mixin.CraftingCpuLogicExecutionBudgetMixin"));
        assertTrue(AcoMixinPlugin.isInsaneAeOwnedExecutionMixin(
                "com.syaru.ae2craftingoptimizer.mixin.CraftingCpuLogicTransactionalBatchV2Mixin"));
        assertTrue(AcoMixinPlugin.isInsaneAeOwnedExecutionMixin(
                "com.syaru.ae2craftingoptimizer.mixin.AdvancedAeCraftingCpuLogicExecutionBudgetMixin"));
        assertTrue(AcoMixinPlugin.isInsaneAeOwnedExecutionMixin(
                "com.syaru.ae2craftingoptimizer.mixin.AdvancedAeCraftingCpuLogicTransactionalBatchV2Mixin"));
    }
}
