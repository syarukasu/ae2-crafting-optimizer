package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.InputTemplate;
import java.lang.reflect.Method;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

/** Issue #167: 計算内memo用RedirectのAE2 15.4.x実メソッド契約を固定する。 */
class CraftingMemoMixinTargetTest {
    @Test
    void ae2CoreMemoizationTargetsExist() throws ReflectiveOperationException {
        assertNotNull(method(CraftingTreeNode.class, "buildChildPatterns"));
        assertNotNull(method(CraftingTreeNode.class, "notRecursive", IPatternDetails.class));
        assertNotNull(method(
                CraftingTreeNode.class,
                "addContainerItems",
                AEKey.class,
                long.class,
                KeyCounter.class));
        assertNotNull(method(
                CraftingCpuHelper.class,
                "lambda$getValidItemTemplates$0",
                IPatternDetails.IInput.class,
                Level.class,
                InputTemplate.class));
    }

    private static Method method(
            Class<?> owner,
            String name,
            Class<?>... parameterTypes) throws NoSuchMethodException {
        return owner.getDeclaredMethod(name, parameterTypes);
    }
}
