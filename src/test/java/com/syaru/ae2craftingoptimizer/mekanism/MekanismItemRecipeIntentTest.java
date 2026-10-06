package com.syaru.ae2craftingoptimizer.mekanism;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import appeng.api.stacks.AEItemKey;
import com.syaru.ae2craftingoptimizer.intent.InputIntent;
import com.syaru.ae2craftingoptimizer.intent.RecipeIntent;
import com.syaru.ae2craftingoptimizer.intent.StackIntent;
import java.util.List;
import mekanism.api.recipes.ItemStackToItemStackRecipe;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MekanismItemRecipeIntentTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void fullInputAndOutputNbtAndStoichiometryAreRequired() {
        var input = new ItemStack(Items.RAW_IRON, 2);
        com.ae2vm.addon.PortStacks.putInt(input, "grade", 1);
        var output = new ItemStack(Items.IRON_INGOT, 3);
        com.ae2vm.addon.PortStacks.putInt(output, "grade", 2);
        var recipe = recipe(input, output, 2);
        var hint = hint(input, output, 2, 1, 3);
        assertTrue(MekanismItemRecipeIntent.matches(hint, recipe, input));
        assertFalse(MekanismItemRecipeIntent.matches(hint(input, output, 1, 1, 3), recipe, input));
        var otherInput = input.copy(); com.ae2vm.addon.PortStacks.putInt(otherInput, "grade", 2);
        assertFalse(MekanismItemRecipeIntent.matches(hint(otherInput, output, 2, 1, 3), recipe, input));
        var otherOutput = output.copy(); com.ae2vm.addon.PortStacks.putInt(otherOutput, "grade", 1);
        assertFalse(MekanismItemRecipeIntent.matches(hint(input, otherOutput, 2, 1, 3), recipe, input));
        when(recipe.test(input)).thenReturn(false);
        assertFalse(MekanismItemRecipeIntent.matches(hint, recipe, input), "native ingredient validation cannot be bypassed");
    }

    @Test void ratioArithmeticDoesNotOverflowLong() {
        var input = new ItemStack(Items.RAW_IRON);
        var output = new ItemStack(Items.IRON_INGOT);
        var recipe = recipe(input, output, 1);
        assertTrue(MekanismItemRecipeIntent.matches(hint(input, output, 1, Long.MAX_VALUE, Long.MAX_VALUE), recipe, input));
        assertFalse(MekanismItemRecipeIntent.matches(hint(input, output, 2, Long.MAX_VALUE, Long.MAX_VALUE), recipe, input));
    }

    private static ItemStackToItemStackRecipe recipe(ItemStack input, ItemStack output, long needed) {
        var recipe = mock(ItemStackToItemStackRecipe.class);
        var ingredient = mock(ItemStackIngredient.class);
        when(recipe.test(input)).thenReturn(true);
        when(recipe.getOutput(input)).thenReturn(output);
        when(recipe.getInput()).thenReturn(ingredient);
        when(ingredient.getNeededAmount(input)).thenReturn(needed);
        return recipe;
    }

    private static RecipeIntent hint(ItemStack in, ItemStack out, long unit, long multiplier, long produced) {
        return new RecipeIntent(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"), BlockPos.ZERO, Direction.EAST,
                BlockPos.ZERO.east(), Direction.WEST, "test:pattern",
                List.of(new InputIntent(List.of(StackIntent.of(AEItemKey.of(in), unit)), multiplier)),
                List.of(), List.of(StackIntent.of(AEItemKey.of(out), produced)), 1, 0, 20);
    }
}
