package com.syaru.ae2craftingoptimizer.mixin;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class AcoMixinPluginTest {
    @Test
    void retiredNeoEcoBudgetMixinsCannotBeLoadedInAnyBootstrapState() {
        var plugin = new AcoMixinPlugin();
        for (String name : new String[] {
                "NeoEco20_3CraftingCpuExecutionBudgetMixin",
                "NeoEco20_4CraftingCpuExecutionBudgetMixin"}) {
            assertFalse(MixinFeatureCatalog.contains(name));
            assertFalse(plugin.shouldApplyMixin("cn.dancingsnow.neoecoae.api.me.ECOCraftingCPULogic", name));
            assertFalse(plugin.shouldApplyMixin("cn.dancingsnow.neoecoae.api.me.ECOCraftingCPULogic",
                    "com.syaru.ae2craftingoptimizer.mixin." + name));
        }
    }
}
