package com.syaru.ae2craftingoptimizer.gtceu;

import static org.junit.jupiter.api.Assertions.*;
import appeng.api.stacks.AEItemKey;
import com.syaru.ae2craftingoptimizer.intent.InputIntent;
import com.syaru.ae2craftingoptimizer.intent.RecipeIntent;
import com.syaru.ae2craftingoptimizer.intent.StackIntent;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GTCEuIntentSignatureTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void distinguishesDifferentInputsForTheSameOutput() {
        var recipe = recipe(Items.RAW_IRON.getDefaultInstance(), Items.IRON_INGOT.getDefaultInstance(), 10000);
        assertTrue(matches(recipe, Items.RAW_IRON.getDefaultInstance(), Items.IRON_INGOT.getDefaultInstance(), 1, 1));
        assertFalse(matches(recipe, Items.IRON_ORE.getDefaultInstance(), Items.IRON_INGOT.getDefaultInstance(), 1, 1));
        assertFalse(matches(recipe, Items.RAW_IRON.getDefaultInstance(), Items.IRON_INGOT.getDefaultInstance(), 2, 1));
        assertTrue(matches(recipe, Items.RAW_IRON.getDefaultInstance(), Items.IRON_INGOT.getDefaultInstance(),
                Long.MAX_VALUE, Long.MAX_VALUE));
    }

    @Test void preservesNbtAndRefusesChancedOrUnconsumedInputs() {
        var in = Items.RAW_IRON.getDefaultInstance();
        var out = Items.IRON_INGOT.getDefaultInstance();
        com.ae2vm.addon.PortStacks.putInt(out, "grade", 2);
        var recipe = recipe(in, out, 10000);
        assertTrue(matches(recipe, in, out, 1, 1));
        assertFalse(matches(recipe, in, Items.IRON_INGOT.getDefaultInstance(), 1, 1));
        assertFalse(matches(recipe(in, out, 5000), in, out, 1, 1));
        assertFalse(matches(recipe(in, out, 0), in, out, 1, 1));
    }

    @Test void unexpectedByproductsCannotBeIgnored() {
        var in = Items.RAW_IRON.getDefaultInstance();
        var out = Items.IRON_INGOT.getDefaultInstance();
        var recipe = new RecipeShape(Map.of("item", List.of(new Content(in, 10000))),
                Map.of("item", List.of(new Content(out, 10000), new Content(Items.FLINT.getDefaultInstance(), 10000))));
        assertFalse(matches(recipe, in, out, 1, 1));
    }

    @Test void neoSizedIngredientsKeepTheDeclaredCount() {
        var in = Items.RAW_IRON.getDefaultInstance();
        var out = Items.IRON_INGOT.getDefaultInstance();
        var recipe = new SizedRecipe(Map.of("item", List.of(new SizedContent(
                net.neoforged.neoforge.common.crafting.SizedIngredient.of(Items.RAW_IRON, 3)))),
                Map.of("item", List.of(new SizedContent(
                net.neoforged.neoforge.common.crafting.SizedIngredient.of(Items.IRON_INGOT, 2)))));
        assertTrue(matches(recipe, in, out, 3, 2));
        assertFalse(matches(recipe, in, out, 1, 1));
    }

    private record SizedRecipe(Map<String, List<SizedContent>> inputs, Map<String, List<SizedContent>> outputs) { }
    private record SizedContent(Object content) {
        private static final int chance = 10000, maxChance = 10000;
        public Object getContent() { return content; }
    }

    private static boolean matches(Object recipe, ItemStack in, ItemStack out, long input, long output) {
        var hint = new RecipeIntent(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"), BlockPos.ZERO,
                Direction.EAST, BlockPos.ZERO.east(), Direction.WEST, "test:pattern",
                List.of(new InputIntent(List.of(StackIntent.of(AEItemKey.of(in), input)), 1)),
                List.of(), List.of(StackIntent.of(AEItemKey.of(out), output)), 1, 0, 20);
        return GTCEuRecipeIntentFastPath.matchesIntent(recipe, List.of(hint));
    }

    private static RecipeShape recipe(ItemStack in, ItemStack out, int chance) {
        return new RecipeShape(Map.of("item", List.of(new Content(in, chance))),
                Map.of("item", List.of(new Content(out, 10000))));
    }

    private record RecipeShape(Map<String, List<Content>> inputs, Map<String, List<Content>> outputs) { }
    private record Content(ItemStack stack, int chance) {
        private static final int maxChance = 10000;
        public Ingredient getContent() { return Ingredient.of(stack); }
    }
}
