package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.InputTemplate;
import java.lang.reflect.Method;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Issue #167: 計算内memo用RedirectのAE2 15.4.x実メソッド契約を固定する。 */
class CraftingMemoMixinTargetTest {
    @Test
    void ingredientCheckpointsMatchRealAe2Bytecode() throws Exception {
        assertEquals(1, calls("appeng/crafting/inv/CraftingSimulationState", "cacheFuzzy",
                "appeng/crafting/inv/CraftingSimulationState", "simulateExtractParent",
                "(Lappeng/api/stacks/AEKey;J)J"));
        assertEquals(1, calls("appeng/crafting/execution/CraftingCpuHelper", "getValidItemTemplates",
                "java/util/Iterator", "next", "()Ljava/lang/Object;"));
        var subIndex = method(KeyCounter.class, "getSubIndex", AEKey.class);
        assertEquals("appeng.api.stacks.VariantCounter", subIndex.getReturnType().getName());
        assertEquals("it.unimi.dsi.fastutil.objects.Reference2ObjectMap",
                KeyCounter.class.getDeclaredField("lists").getType().getName());
    }

    private static int calls(String type, String method, String owner, String name, String descriptor)
            throws Exception {
        var node = new ClassNode();
        try (var source = CraftingMemoMixinTargetTest.class.getClassLoader().getResourceAsStream(type + ".class")) {
            assertNotNull(source);
            new ClassReader(source).accept(node, 0);
        }
        int matches = 0;
        for (var candidate : node.methods) {
            if (candidate.name.equals(method)) {
                for (var instruction : candidate.instructions) {
                    if (instruction instanceof MethodInsnNode call && call.owner.equals(owner)
                            && call.name.equals(name) && call.desc.equals(descriptor)) {
                        matches++;
                    }
                }
            }
        }
        return matches;
    }

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
