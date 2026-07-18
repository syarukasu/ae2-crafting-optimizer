package com.syaru.ae2craftingoptimizer.mekanism;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.api.batch.PatternBatchContext;
import com.syaru.ae2craftingoptimizer.batch.ExactMultisetMatcher;
import com.syaru.ae2craftingoptimizer.batch.ExactPatternSnapshot;
import com.syaru.ae2craftingoptimizer.mixin.MekanismCachedRecipeAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.merged.BoxedChemicalStack;
import mekanism.api.recipes.ChemicalCrystallizerRecipe;
import mekanism.api.recipes.ChemicalDissolutionRecipe;
import mekanism.api.recipes.CombinerRecipe;
import mekanism.api.recipes.ElectrolysisRecipe;
import mekanism.api.recipes.FluidToFluidRecipe;
import mekanism.api.recipes.ItemStackToFluidRecipe;
import mekanism.api.recipes.ItemStackToItemStackRecipe;
import mekanism.api.recipes.MekanismRecipe;
import mekanism.api.recipes.PressurizedReactionRecipe;
import mekanism.api.recipes.RotaryRecipe;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.api.recipes.chemical.ChemicalChemicalToChemicalRecipe;
import mekanism.api.recipes.chemical.ChemicalToChemicalRecipe;
import mekanism.api.recipes.chemical.FluidChemicalToChemicalRecipe;
import mekanism.api.recipes.chemical.ItemStackChemicalToItemStackRecipe;
import mekanism.api.recipes.chemical.ItemStackToChemicalRecipe;
import mekanism.api.recipes.ingredients.ChemicalStackIngredient;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import mekanism.api.recipes.ingredients.InputIngredient;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import mekanism.common.recipe.lookup.IRecipeLookupHandler;
import mekanism.common.tile.factory.TileEntityFactory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/** Exact, side-effect-free recipe validation for Mekanism's native recipe model. */
public final class MekanismNativeBatchBridge {
    private static final int MAX_CACHE_ENTRIES = 4096;
    private static final Map<CacheKey, MekanismRecipe> VERIFIED_RECIPES = new ConcurrentHashMap<>();

    private MekanismNativeBatchBridge() {
    }

    public static boolean supportsTarget(PatternBatchContext context) {
        return context.target() instanceof IRecipeLookupHandler<?> && context.deterministicTarget();
    }

    @Nullable
    public static Verification verify(PatternBatchContext context, long offeredExecutions) {
        if (offeredExecutions <= 0L || !(context.target() instanceof IRecipeLookupHandler<?> handler)) {
            return null;
        }
        ExactPatternSnapshot snapshot = ExactPatternSnapshot.of(context);
        if (!snapshot.hasOnlyPositiveAmounts()) {
            return null;
        }

        MekanismRecipe recipe = findRecipe(handler, snapshot);
        if (recipe == null) {
            return null;
        }
        int nativeLimit = nativeOperationLimit(handler, recipe);
        if (nativeLimit <= 0) {
            return null;
        }
        return new Verification(recipe.getId(), (int) Math.min(nativeLimit, offeredExecutions));
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
    private static MekanismRecipe findRecipe(
            IRecipeLookupHandler<?> handler,
            ExactPatternSnapshot snapshot) {
        ResourceLocation recipeType = handler.getRecipeType().getRegistryName();
        CacheKey key = new CacheKey(recipeType, snapshot.inputs(), snapshot.outputs());
        MekanismRecipe cached = VERIFIED_RECIPES.get(key);
        if (cached != null && matches(cached, snapshot)) {
            return cached;
        }
        for (MekanismRecipe recipe : handler.getRecipeType().getRecipes(handler.getHandlerWorld())) {
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static int nativeOperationLimit(IRecipeLookupHandler handler, MekanismRecipe recipe) {
        try {
            CachedRecipe<?> cachedRecipe = handler.createNewCachedRecipe(recipe, 0);
            int baseline = ((MekanismCachedRecipeAccessor) (Object) cachedRecipe)
                    .aco$getBaselineMaxOperations()
                    .getAsInt();
            if (baseline <= 0) {
                return 0;
            }
            int processes = handler instanceof TileEntityFactory<?> factory
                    ? Math.max(1, factory.tier.processes)
                    : 1;
            return (int) Math.min(Integer.MAX_VALUE, Math.multiplyExact((long) baseline, processes));
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    private static boolean matches(MekanismRecipe recipe, ExactPatternSnapshot snapshot) {
        for (Descriptor descriptor : descriptors(recipe)) {
            if (descriptor.outputs().equals(snapshot.outputs())
                    && ExactMultisetMatcher.matchesExactly(snapshot.inputs(), descriptor.inputs())) {
                return true;
            }
        }
        return false;
    }

    private static List<Descriptor> descriptors(MekanismRecipe recipe) {
        List<Descriptor> descriptors = new ArrayList<>(2);
        if (recipe instanceof ItemStackToItemStackRecipe value) {
            add(descriptors, List.of(item(value.getInput())), itemOutputs(value.getOutputDefinition()));
        } else if (recipe instanceof ItemStackToFluidRecipe value) {
            add(descriptors, List.of(item(value.getInput())), fluidOutputs(value.getOutputDefinition()));
        } else if (recipe instanceof ItemStackToChemicalRecipe<?, ?> value) {
            add(descriptors, List.of(item(value.getInput())), chemicalOutputs(value.getOutputDefinition()));
        } else if (recipe instanceof ItemStackChemicalToItemStackRecipe<?, ?, ?> value) {
            add(descriptors,
                    List.of(item(value.getItemInput()), chemical(value.getChemicalInput())),
                    itemOutputs(value.getOutputDefinition()));
        } else if (recipe instanceof ChemicalChemicalToChemicalRecipe<?, ?, ?> value) {
            add(descriptors,
                    List.of(chemical(value.getLeftInput()), chemical(value.getRightInput())),
                    chemicalOutputs(value.getOutputDefinition()));
        } else if (recipe instanceof ChemicalCrystallizerRecipe value) {
            add(descriptors, List.of(chemical(value.getInput())), itemOutputs(value.getOutputDefinition()));
        } else if (recipe instanceof ChemicalDissolutionRecipe value) {
            add(descriptors,
                    List.of(item(value.getItemInput()), chemical(value.getGasInput())),
                    boxedChemicalOutputs(value.getOutputDefinition()));
        } else if (recipe instanceof ElectrolysisRecipe value) {
            add(descriptors,
                    List.of(fluid(value.getInput())),
                    uniqueOutputs(value.getOutputDefinition(), output -> chemicalOutputs(List.of(output.left()), List.of(output.right()))));
        } else if (recipe instanceof FluidChemicalToChemicalRecipe<?, ?, ?> value) {
            add(descriptors,
                    List.of(fluid(value.getFluidInput()), chemical(value.getChemicalInput())),
                    chemicalOutputs(value.getOutputDefinition()));
        } else if (recipe instanceof FluidToFluidRecipe value) {
            add(descriptors, List.of(fluid(value.getInput())), fluidOutputs(value.getOutputDefinition()));
        } else if (recipe instanceof ChemicalToChemicalRecipe<?, ?, ?> value) {
            add(descriptors, List.of(chemical(value.getInput())), chemicalOutputs(value.getOutputDefinition()));
        } else if (recipe instanceof PressurizedReactionRecipe value) {
            add(descriptors,
                    List.of(item(value.getInputSolid()), fluid(value.getInputFluid()), chemical(value.getInputGas())),
                    uniqueOutputs(value.getOutputDefinition(), output -> itemAndChemicalOutputs(output.item(), output.gas())));
        } else if (recipe instanceof RotaryRecipe value) {
            if (value.hasFluidToGas()) {
                add(descriptors,
                        List.of(fluid(value.getFluidInput())),
                        chemicalOutputs(value.getGasOutputDefinition()));
            }
            if (value.hasGasToFluid()) {
                add(descriptors,
                        List.of(chemical(value.getGasInput())),
                        fluidOutputs(value.getFluidOutputDefinition()));
            }
        } else if (recipe instanceof CombinerRecipe value) {
            add(descriptors,
                    List.of(item(value.getMainInput()), item(value.getExtraInput())),
                    itemOutputs(value.getOutputDefinition()));
        }
        return descriptors;
    }

    private static void add(
            List<Descriptor> descriptors,
            List<ExactMultisetMatcher.Requirement<AEKey>> inputs,
            @Nullable Map<AEKey, Long> outputs) {
        if (outputs != null && !outputs.isEmpty()) {
            descriptors.add(new Descriptor(inputs, outputs));
        }
    }

    private static ExactMultisetMatcher.Requirement<AEKey> item(ItemStackIngredient ingredient) {
        return key -> {
            if (!(key instanceof AEItemKey itemKey)) {
                return 0L;
            }
            ItemStack stack = itemKey.toStack(Integer.MAX_VALUE);
            return ingredient.testType(stack) ? ingredient.getNeededAmount(stack) : 0L;
        };
    }

    private static ExactMultisetMatcher.Requirement<AEKey> fluid(FluidStackIngredient ingredient) {
        return key -> {
            if (!(key instanceof AEFluidKey fluidKey)) {
                return 0L;
            }
            FluidStack stack = fluidKey.toStack(Integer.MAX_VALUE);
            return ingredient.testType(stack) ? ingredient.getNeededAmount(stack) : 0L;
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ExactMultisetMatcher.Requirement<AEKey> chemical(ChemicalStackIngredient<?, ?> ingredient) {
        InputIngredient raw = ingredient;
        return key -> {
            if (!(key instanceof MekanismKey chemicalKey)) {
                return 0L;
            }
            ChemicalStack<?> stack = chemicalKey.withAmount(Long.MAX_VALUE);
            return raw.testType(stack) ? raw.getNeededAmount(stack) : 0L;
        };
    }

    @Nullable
    private static Map<AEKey, Long> itemOutputs(List<ItemStack> definitions) {
        return uniqueOutputs(definitions, stack -> singleton(AEItemKey.of(stack), stack.getCount()));
    }

    @Nullable
    private static Map<AEKey, Long> fluidOutputs(List<FluidStack> definitions) {
        return uniqueOutputs(definitions, stack -> singleton(AEFluidKey.of(stack), stack.getAmount()));
    }

    @Nullable
    private static Map<AEKey, Long> chemicalOutputs(List<? extends ChemicalStack<?>> definitions) {
        return uniqueOutputs(definitions, stack -> singleton(MekanismKey.of(stack), stack.getAmount()));
    }

    @Nullable
    private static Map<AEKey, Long> chemicalOutputs(
            List<? extends ChemicalStack<?>> first,
            List<? extends ChemicalStack<?>> second) {
        Map<AEKey, Long> left = chemicalOutputs(first);
        Map<AEKey, Long> right = chemicalOutputs(second);
        return merge(left, right);
    }

    @Nullable
    private static Map<AEKey, Long> boxedChemicalOutputs(List<BoxedChemicalStack> definitions) {
        return uniqueOutputs(definitions, stack -> stack == null || stack.isEmpty()
                ? null
                : singleton(MekanismKey.of(stack.getChemicalStack()), stack.getChemicalStack().getAmount()));
    }

    @Nullable
    private static Map<AEKey, Long> itemAndChemicalOutputs(ItemStack item, ChemicalStack<?> chemical) {
        Map<AEKey, Long> itemOutput = item.isEmpty() ? Map.of() : singleton(AEItemKey.of(item), item.getCount());
        Map<AEKey, Long> chemicalOutput = chemical.isEmpty()
                ? Map.of()
                : singleton(MekanismKey.of(chemical), chemical.getAmount());
        return merge(itemOutput, chemicalOutput);
    }

    @Nullable
    private static Map<AEKey, Long> singleton(@Nullable AEKey key, long amount) {
        return key == null || amount <= 0L ? null : Map.of(key, amount);
    }

    @Nullable
    private static Map<AEKey, Long> merge(
            @Nullable Map<AEKey, Long> first,
            @Nullable Map<AEKey, Long> second) {
        if (first == null || second == null) {
            return null;
        }
        Map<AEKey, Long> merged = new HashMap<>(first);
        try {
            second.forEach((key, value) -> merged.merge(key, value, Math::addExact));
        } catch (ArithmeticException exception) {
            return null;
        }
        return Map.copyOf(merged);
    }

    @Nullable
    private static <T> Map<AEKey, Long> uniqueOutputs(
            List<T> definitions,
            Function<T, Map<AEKey, Long>> mapper) {
        Map<AEKey, Long> expected = null;
        for (T definition : definitions) {
            Map<AEKey, Long> candidate = mapper.apply(definition);
            if (candidate == null || candidate.isEmpty()) {
                return null;
            }
            if (expected == null) {
                expected = Map.copyOf(candidate);
            } else if (!expected.equals(candidate)) {
                return null;
            }
        }
        return expected;
    }

    public record Verification(ResourceLocation recipeId, int maximumExecutions) {
        public Verification {
            if (recipeId == null || maximumExecutions <= 0) {
                throw new IllegalArgumentException("invalid Mekanism native verification");
            }
        }
    }

    private record Descriptor(
            List<ExactMultisetMatcher.Requirement<AEKey>> inputs,
            Map<AEKey, Long> outputs) {
        private Descriptor {
            inputs = List.copyOf(inputs);
            outputs = Map.copyOf(outputs);
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
