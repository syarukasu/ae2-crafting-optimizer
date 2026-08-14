package com.syaru.ae2craftingoptimizer.mixin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syaru.ae2craftingoptimizer.integration.NeoEcoVersionCompatibility.ExecutionProfile;
import org.junit.jupiter.api.Test;

class AcoMixinPluginTest {
    @Test
    void selectsOnlyTheNeoEco20_3MixinForTheOldDescriptor() {
        assertTrue(AcoMixinPlugin.shouldApplyNeoEcoExecutionMixin(
                "com.syaru.ae2craftingoptimizer.mixin.NeoEco20_3CraftingCpuExecutionBudgetMixin",
                ExecutionProfile.NEO_ECO_20_3));
        assertFalse(AcoMixinPlugin.shouldApplyNeoEcoExecutionMixin(
                "com.syaru.ae2craftingoptimizer.mixin.NeoEco20_4CraftingCpuExecutionBudgetMixin",
                ExecutionProfile.NEO_ECO_20_3));
    }

    @Test
    void selectsOnlyTheNeoEco20_4MixinForTheNewDescriptor() {
        assertFalse(AcoMixinPlugin.shouldApplyNeoEcoExecutionMixin(
                "NeoEco20_3CraftingCpuExecutionBudgetMixin",
                ExecutionProfile.NEO_ECO_20_4));
        assertTrue(AcoMixinPlugin.shouldApplyNeoEcoExecutionMixin(
                "NeoEco20_4CraftingCpuExecutionBudgetMixin",
                ExecutionProfile.NEO_ECO_20_4));
    }

    @Test
    void rejectsBothNeoEcoMixinsWhenTheVersionIsUnknown() {
        assertFalse(AcoMixinPlugin.shouldApplyNeoEcoExecutionMixin(
                "NeoEco20_3CraftingCpuExecutionBudgetMixin",
                ExecutionProfile.NONE));
        assertFalse(AcoMixinPlugin.shouldApplyNeoEcoExecutionMixin(
                "NeoEco20_4CraftingCpuExecutionBudgetMixin",
                ExecutionProfile.NONE));
    }
}
