package com.syaru.ae2craftingoptimizer.mekanism;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.intent.RecipeIntent;
import com.syaru.ae2craftingoptimizer.intent.RecipeIntentRegistry;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import mekanism.api.recipes.ItemStackToItemStackRecipe;
import mekanism.api.recipes.inputs.IInputHandler;
import mekanism.common.tile.prefab.TileEntityElectricMachine;
import net.minecraft.world.item.ItemStack;

/** #179/#140: typed recipe lookup only; the native monitor owns all processing. */
public final class MekanismItemRecipeIntent {
    private static final Map<Object, Index> INDEXES = new IdentityHashMap<>();

    private MekanismItemRecipeIntent() { }

    public static ItemStackToItemStackRecipe find(TileEntityElectricMachine machine,
            IInputHandler<ItemStack> handler) {
        return resolve(machine, handler.getInput(), () -> machine.findFirstRecipe(handler));
    }

    private static ItemStackToItemStackRecipe resolve(TileEntityElectricMachine machine, ItemStack input,
            Supplier<ItemStackToItemStackRecipe> original) {
        var level = machine.getLevel();
        if (!ACOConfig.enableRecipeIntentBridge() || level == null || level.isClientSide || input.isEmpty())
            return original.get();
        var hints = RecipeIntentRegistry.findForTarget(level.dimension().location(), machine.getBlockPos(), level.getGameTime());
        if (hints.isEmpty()) return original.get();
        var type = machine.getRecipeType();
        var recipes = type.getRecipes(level);
        var index = INDEXES.get(type.getRecipeType());
        // Mekanism replaces its own list on recipe reload; never retain an old recipe across it.
        if (index == null || index.recipes != recipes) {
            index = new Index(recipes);
            INDEXES.put(type.getRecipeType(), index);
        }
        ItemStackToItemStackRecipe selected = null;
        for (var hint : hints) {
            if (hint.outputs().size() != 1 || hint.outputs().get(0).exactKey() == null) continue;
            for (var recipe : index.outputs.getOrDefault(hint.outputs().get(0).exactKey(), List.of())) {
                if (!matches(hint, recipe, input)) continue;
                if (selected != null && selected != recipe) return original.get();
                selected = recipe;
            }
        }
        if (selected == null) return original.get();
        com.syaru.ae2craftingoptimizer.optimization.OptimizationMetrics.recordMekanismIntentMatch();
        return selected;
    }

    static boolean matches(RecipeIntent hint, ItemStackToItemStackRecipe recipe, ItemStack actual) {
        if (hint.inputs().size() != 1 || hint.outputs().size() != 1 || actual.isEmpty()) return false;
        var slot = hint.inputs().get(0);
        if (slot.possibleInputs().size() != 1 || slot.multiplier() <= 0) return false;
        var input = slot.possibleInputs().get(0);
        var output = hint.outputs().get(0);
        if (input.amount() <= 0 || output.amount() <= 0 || input.exactKey() == null || output.exactKey() == null
                || !input.exactKey().equals(AEItemKey.of(actual)) || !recipe.test(actual)) return false;
        var produced = recipe.getOutput(actual);
        if (produced.isEmpty() || !output.exactKey().equals(AEItemKey.of(produced))) return false;
        long needed = recipe.getInput().getNeededAmount(actual);
        return needed > 0 && BigInteger.valueOf(input.amount()).multiply(BigInteger.valueOf(slot.multiplier()))
                .multiply(BigInteger.valueOf(produced.getCount()))
                .equals(BigInteger.valueOf(output.amount()).multiply(BigInteger.valueOf(needed)));
    }

    private static final class Index {
        final List<ItemStackToItemStackRecipe> recipes;
        final Map<AEKey, List<ItemStackToItemStackRecipe>> outputs = new HashMap<>();
        Index(List<ItemStackToItemStackRecipe> recipes) {
            this.recipes = recipes;
            for (var recipe : recipes) for (var output : recipe.getOutputDefinition()) {
                if (!output.isEmpty()) outputs.computeIfAbsent(AEItemKey.of(output), ignored -> new ArrayList<>()).add(recipe);
            }
        }
    }

    public static void clear() { INDEXES.clear(); }
}
