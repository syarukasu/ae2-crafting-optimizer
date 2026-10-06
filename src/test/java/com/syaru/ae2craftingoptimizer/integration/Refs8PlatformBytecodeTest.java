package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Checks actual pinned dependencies without starting a game or initializing optional mods. */
class Refs8PlatformBytecodeTest {
    @Test void ae2WorkerAndNativeEntryDescriptorsMatch() throws Exception {
        var type = read("appeng/me/service/CraftingService");
        assertTrue(type.fields.stream().anyMatch(f -> f.name.equals("CRAFTING_POOL")
                && f.desc.equals("Ljava/util/concurrent/ExecutorService;")));
        assertTrue(type.methods.stream().anyMatch(m -> m.name.equals("beginCraftingCalculation")
                && m.desc.equals("(Lnet/minecraft/world/level/Level;Lappeng/api/networking/crafting/ICraftingSimulationRequester;Lappeng/api/stacks/AEKey;JLappeng/api/networking/crafting/CalculationStrategy;)Ljava/util/concurrent/Future;")));
    }

    @Test void gtLookupAndSearchRedirectExist() throws Exception {
        var lookup = read("com/gregtechceu/gtceu/api/recipe/lookup/GTRecipeLookup");
        assertTrue(lookup.methods.stream().anyMatch(m -> m.name.equals("addRecipe")
                && m.desc.equals("(Lcom/gregtechceu/gtceu/api/recipe/kind/GTRecipe;)Z")));
        assertTrue(lookup.methods.stream().anyMatch(m -> m.name.equals("removeAllRecipes") && m.desc.equals("()V")));
        assertInvocation("com/gregtechceu/gtceu/api/machine/trait/RecipeLogic", "searchRecipe",
                "com/gregtechceu/gtceu/api/recipe/GTRecipeType", "searchRecipe",
                "(Lcom/gregtechceu/gtceu/api/capability/recipe/IRecipeCapabilityHolder;Ljava/util/function/Predicate;)Ljava/util/Iterator;");
    }

    @Test void mekanismItemLookupRedirectExists() throws Exception {
        assertInvocation("mekanism/common/tile/prefab/TileEntityElectricMachine", "getRecipe",
                "mekanism/common/tile/prefab/TileEntityElectricMachine", "findFirstRecipe",
                "(Lmekanism/api/recipes/inputs/IInputHandler;)Lmekanism/api/recipes/MekanismRecipe;");
    }

    private static void assertInvocation(String type, String method, String owner, String name, String desc) throws Exception {
        boolean found = false;
        for (var m : read(type).methods) if (m.name.equals(method))
            for (var insn : m.instructions) if (insn instanceof MethodInsnNode call
                    && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(desc)) found = true;
        assertTrue(found, type + "." + method + " invocation changed");
    }

    private static ClassNode read(String type) throws Exception {
        try (var stream = Refs8PlatformBytecodeTest.class.getClassLoader().getResourceAsStream(type + ".class")) {
            assertNotNull(stream, type);
            var node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }
}
