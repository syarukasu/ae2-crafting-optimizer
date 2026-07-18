package com.syaru.ae2craftingoptimizer.gtceu;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntProviderFluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntProviderIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.syaru.ae2craftingoptimizer.api.batch.PatternBatchContext;
import com.syaru.ae2craftingoptimizer.batch.ExactMultisetMatcher;
import com.syaru.ae2craftingoptimizer.batch.ExactPatternSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

public final class GTCEuNativeBatchBridge {
    private static final int MAX_CACHE_ENTRIES = 4096;
    private static final Map<CacheKey, GTRecipe> VERIFIED_RECIPES = new ConcurrentHashMap<>();

    private GTCEuNativeBatchBridge() {
    }

    public static boolean supportsTarget(PatternBatchContext context) {
        if (!(context.target() instanceof MetaMachineBlockEntity blockEntity)
                || !(blockEntity.getMetaMachine() instanceof IRecipeLogicMachine machine)) {
            return false;
        }
        return machine.getRecipeLogic().isIdle() && context.deterministicTarget();
    }

    @Nullable
    public static Verification verify(PatternBatchContext context, long offeredExecutions) {
        if (offeredExecutions <= 0L
                || !(context.target() instanceof MetaMachineBlockEntity blockEntity)) {
            return null;
        }
        MetaMachine metaMachine = blockEntity.getMetaMachine();
        if (!(metaMachine instanceof IRecipeLogicMachine machine)
                || !machine.getRecipeLogic().isIdle()) {
            return null;
        }
        ExactPatternSnapshot snapshot = ExactPatternSnapshot.of(context);
        if (!snapshot.hasOnlyPositiveAmounts()) {
            return null;
        }

        GTRecipe recipe = findRecipe(machine, snapshot);
        if (recipe == null
                || !RecipeHelper.checkConditions(recipe, machine.getRecipeLogic()).isSuccess()) {
            return null;
        }
        int offered = (int) Math.min(Integer.MAX_VALUE, offeredExecutions);
        int outputLimited = ParallelLogic.limitByOutputMerging(
                machine,
                recipe,
                offered,
                machine::canVoidRecipeOutputs,
                Collections.emptyList());
        if (outputLimited <= 0) {
            return null;
        }
        return new Verification(recipe.getId(), outputLimited);
    }

    public static boolean revalidate(
            PatternBatchContext context,
            ResourceLocation expectedRecipe,
            long executions) {
        Verification verification = verify(context, executions);
        return verification != null
                && verification.recipeId().equals(expectedRecipe)
                && verification.maximumExecutions() >= executions;
    }

    public static void clearCache() {
        VERIFIED_RECIPES.clear();
    }

    @Nullable
    private static GTRecipe findRecipe(IRecipeLogicMachine machine, ExactPatternSnapshot snapshot) {
        CacheKey key = new CacheKey(machine.getRecipeType().registryName, snapshot.inputs(), snapshot.outputs());
        GTRecipe cached = VERIFIED_RECIPES.get(key);
        if (cached != null && matches(cached, snapshot)) {
            return cached;
        }
        Set<GTRecipe> recipes = new LinkedHashSet<>();
        for (var categoryRecipes : machine.getRecipeType().getCategoryMap().values()) {
            recipes.addAll(categoryRecipes);
        }
        for (GTRecipe recipe : recipes) {
            if (matches(recipe, snapshot)) {
                if (VERIFIED_RECIPES.size() >= MAX_CACHE_ENTRIES) {
                    VERIFIED_RECIPES.clear();
                }
                VERIFIED_RECIPES.put(key, recipe);
                return recipe;
            }
        }
        return null;
    }

    private static boolean matches(GTRecipe recipe, ExactPatternSnapshot snapshot) {
        if (recipe.getId() == null
                || !recipe.ingredientActions.isEmpty()
                || !supportedNormalCapabilities(recipe.inputs.keySet())
                || !supportedNormalCapabilities(recipe.outputs.keySet())
                || !supportedTickInputs(recipe.tickInputs.keySet())
                || !recipe.tickOutputs.isEmpty()) {
            return false;
        }
        List<ExactMultisetMatcher.Requirement<AEKey>> requirements = new ArrayList<>();
        for (var entry : recipe.inputs.entrySet()) {
            for (Content content : entry.getValue()) {
                if (!deterministicConsumable(content)) {
                    return false;
                }
                var requirement = inputRequirement(entry.getKey(), content);
                if (requirement == null) {
                    return false;
                }
                requirements.add(requirement);
            }
        }
        Map<AEKey, Long> outputs = new HashMap<>();
        for (var entry : recipe.outputs.entrySet()) {
            for (Content content : entry.getValue()) {
                if (!deterministicConsumable(content)
                        || !addOutput(outputs, entry.getKey(), content)) {
                    return false;
                }
            }
        }
        return outputs.equals(snapshot.outputs())
                && ExactMultisetMatcher.matchesExactly(snapshot.inputs(), requirements);
    }

    private static boolean supportedNormalCapabilities(Set<RecipeCapability<?>> capabilities) {
        return capabilities.stream().allMatch(capability ->
                capability == ItemRecipeCapability.CAP || capability == FluidRecipeCapability.CAP);
    }

    private static boolean supportedTickInputs(Set<RecipeCapability<?>> capabilities) {
        return capabilities.stream().allMatch(capability -> capability == EURecipeCapability.CAP);
    }

    private static boolean deterministicConsumable(Content content) {
        return content.chance > 0 && content.chance == content.maxChance && !content.isChanced();
    }

    @Nullable
    private static ExactMultisetMatcher.Requirement<AEKey> inputRequirement(
            RecipeCapability<?> capability,
            Content content) {
        if (capability == ItemRecipeCapability.CAP) {
            Ingredient ingredient = ItemRecipeCapability.CAP.of(content.content);
            if (ingredient instanceof IntProviderIngredient) {
                return null;
            }
            int amount = ingredient instanceof SizedIngredient sized ? sized.getAmount() : 1;
            if (amount <= 0) {
                return null;
            }
            return key -> key instanceof AEItemKey itemKey && ingredient.test(itemKey.toStack()) ? amount : 0L;
        }
        if (capability == FluidRecipeCapability.CAP) {
            FluidIngredient ingredient = FluidRecipeCapability.CAP.of(content.content);
            if (ingredient instanceof IntProviderFluidIngredient || ingredient.getAmount() <= 0) {
                return null;
            }
            return key -> key instanceof AEFluidKey fluidKey
                            && ingredient.test(fluidKey.toStack(ingredient.getAmount()))
                    ? ingredient.getAmount()
                    : 0L;
        }
        return null;
    }

    private static boolean addOutput(
            Map<AEKey, Long> outputs,
            RecipeCapability<?> capability,
            Content content) {
        if (capability == ItemRecipeCapability.CAP) {
            Ingredient ingredient = ItemRecipeCapability.CAP.of(content.content);
            if (ingredient instanceof IntProviderIngredient) {
                return false;
            }
            ItemStack[] stacks = ingredient.getItems();
            if (stacks.length == 0) {
                return false;
            }
            AEItemKey key = null;
            long amount = -1L;
            for (ItemStack stack : stacks) {
                AEItemKey candidate = AEItemKey.of(stack);
                long candidateAmount = ingredient instanceof SizedIngredient sized
                        ? sized.getAmount()
                        : stack.getCount();
                if (candidate == null || candidateAmount <= 0L) {
                    return false;
                }
                if (key == null) {
                    key = candidate;
                    amount = candidateAmount;
                } else if (!key.equals(candidate) || amount != candidateAmount) {
                    return false;
                }
            }
            outputs.merge(key, amount, Math::addExact);
            return true;
        }
        if (capability == FluidRecipeCapability.CAP) {
            FluidIngredient ingredient = FluidRecipeCapability.CAP.of(content.content);
            if (ingredient instanceof IntProviderFluidIngredient || ingredient.getAmount() <= 0) {
                return false;
            }
            FluidStack[] stacks = ingredient.getStacks();
            if (stacks.length == 0) {
                return false;
            }
            AEFluidKey key = null;
            for (FluidStack stack : stacks) {
                AEFluidKey candidate = AEFluidKey.of(stack);
                if (candidate == null) {
                    return false;
                }
                if (key == null) {
                    key = candidate;
                } else if (!key.equals(candidate)) {
                    return false;
                }
            }
            outputs.merge(key, (long) ingredient.getAmount(), Math::addExact);
            return true;
        }
        return false;
    }

    public record Verification(ResourceLocation recipeId, int maximumExecutions) {
        public Verification {
            if (recipeId == null || maximumExecutions <= 0) {
                throw new IllegalArgumentException("invalid GTCEu native verification");
            }
        }
    }

    private record CacheKey(
            ResourceLocation recipeType,
            Map<AEKey, Long> inputs,
            Map<AEKey, Long> outputs) {
        private CacheKey {
            inputs = Map.copyOf(inputs);
            outputs = Map.copyOf(outputs);
        }
    }
}
