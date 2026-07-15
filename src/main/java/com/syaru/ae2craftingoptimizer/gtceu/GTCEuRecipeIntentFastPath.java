package com.syaru.ae2craftingoptimizer.gtceu;

import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.intent.RecipeIntent;
import com.syaru.ae2craftingoptimizer.intent.RecipeIntentRegistry;
import com.syaru.ae2craftingoptimizer.intent.StackIntent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

public final class GTCEuRecipeIntentFastPath {
    private static final Object LOCK = new Object();
    private static final Map<Object, OutputRecipeIndex> INDEXES = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Set<String> REFLECTION_FAILURES_LOGGED = Collections.synchronizedSet(new LinkedHashSet<>());

    private GTCEuRecipeIntentFastPath() {
    }

    public static Iterator<?> wrapSearchIterator(Object recipeLogic, Iterator<?> original) {
        if (!ACOConfig.enableGtceuRecipeIntentFastPath()) {
            return original;
        }
        if (recipeLogic == null || original == null) {
            return original;
        }

        try {
            Context context = Context.from(recipeLogic);
            if (context == null) {
                return original;
            }

            List<RecipeIntent> intents = RecipeIntentRegistry.findForTarget(
                    context.dimension(),
                    context.machinePos(),
                    context.gameTime());
            if (intents.isEmpty()) {
                return original;
            }

            OutputRecipeIndex index = getOrCreateIndex(context.recipeType(), context.recipeManager());
            List<Object> candidates = new ArrayList<>();
            Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            int limit = ACOConfig.getGtceuRecipeIntentMaximumCandidates();

            for (int i = intents.size() - 1; i >= 0 && candidates.size() < limit; i--) {
                RecipeIntent intent = intents.get(i);
                for (StackIntent output : intent.outputs()) {
                    if (!isOutput(output)) {
                        continue;
                    }
                    for (Object recipe : index.recipesForOutput(output.keyId())) {
                        if (seen.add(recipe)) {
                            candidates.add(recipe);
                            if (candidates.size() >= limit) {
                                break;
                            }
                        }
                    }
                    if (candidates.size() >= limit) {
                        break;
                    }
                }
            }

            if (candidates.isEmpty()) {
                return original;
            }

            if (ACOConfig.logGtceuRecipeIntentFastPath()) {
                AE2CraftingOptimizer.LOGGER.info(
                        "ACO GTCEu intent fast path: {} candidate(s) for {} {}",
                        candidates.size(),
                        context.dimension(),
                        context.machinePos());
            }
            return new IntentFirstIterator(candidates, original);
        } catch (Throwable throwable) {
            logReflectionFailure("wrap", throwable);
            return original;
        }
    }

    public static void clearIndexes(String reason) {
        synchronized (LOCK) {
            INDEXES.clear();
        }
        if (ACOConfig.logGtceuRecipeIntentFastPath()) {
            AE2CraftingOptimizer.LOGGER.info("Cleared GTCEu recipe intent indexes: {}", reason);
        }
    }

    private static OutputRecipeIndex getOrCreateIndex(Object recipeType, RecipeManager recipeManager) {
        synchronized (LOCK) {
            OutputRecipeIndex existing = INDEXES.get(recipeType);
            if (existing != null) {
                return existing;
            }
            OutputRecipeIndex created = OutputRecipeIndex.build(recipeType, recipeManager);
            if (INDEXES.size() >= ACOConfig.getGtceuRecipeIntentIndexCacheSize()) {
                Iterator<Object> iterator = INDEXES.keySet().iterator();
                if (iterator.hasNext()) {
                    iterator.next();
                    iterator.remove();
                }
            }
            INDEXES.put(recipeType, created);
            return created;
        }
    }

    private static boolean isOutput(StackIntent output) {
        return output != null && output.amount() > 0 && output.keyId() != null && !output.keyId().isBlank();
    }

    private static void logReflectionFailure(String key, Throwable throwable) {
        if (!ACOConfig.logGtceuRecipeIntentFastPath() || !REFLECTION_FAILURES_LOGGED.add(key)) {
            return;
        }
        AE2CraftingOptimizer.LOGGER.warn("ACO GTCEu recipe intent fast path disabled for this call: {}", throwable.toString());
    }

    private record Context(ResourceLocation dimension, BlockPos machinePos, long gameTime, RecipeManager recipeManager, Object recipeType) {
        private static Context from(Object recipeLogic) throws ReflectiveOperationException {
            Object metaMachine = invoke(recipeLogic, "getMachine");
            if (metaMachine == null) {
                return null;
            }
            Object levelObject = invoke(metaMachine, "getLevel");
            if (!(levelObject instanceof ServerLevel level)) {
                return null;
            }
            Object posObject = invoke(metaMachine, "getPos");
            if (!(posObject instanceof BlockPos pos)) {
                return null;
            }
            Object recipeLogicMachine = getField(recipeLogic, "machine");
            if (recipeLogicMachine == null) {
                return null;
            }
            Object recipeType = invoke(recipeLogicMachine, "getRecipeType");
            if (!(recipeType instanceof RecipeType<?>)) {
                return null;
            }
            Object recipeManagerObject = invoke(recipeLogic, "getRecipeManager");
            if (!(recipeManagerObject instanceof RecipeManager recipeManager)) {
                return null;
            }
            return new Context(level.dimension().location(), pos.immutable(), level.getGameTime(), recipeManager, recipeType);
        }
    }

    private static Object invoke(Object target, String methodName) throws ReflectiveOperationException {
        Method method = findMethod(target.getClass(), methodName);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static Method findMethod(Class<?> type, String methodName) throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(methodName);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return type.getMethod(methodName);
    }

    private static Object getField(Object target, String fieldName) throws ReflectiveOperationException {
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static final class OutputRecipeIndex {
        private final Map<String, List<Object>> byOutputId;

        private OutputRecipeIndex(Map<String, List<Object>> byOutputId) {
            this.byOutputId = byOutputId;
        }

        static OutputRecipeIndex build(Object recipeType, RecipeManager recipeManager) {
            Map<String, List<Object>> byOutput = new LinkedHashMap<>();
            if (!(recipeType instanceof RecipeType<?> typedRecipeType)) {
                return new OutputRecipeIndex(byOutput);
            }

            @SuppressWarnings({ "rawtypes", "unchecked" })
            List<? extends Recipe<?>> recipes = recipeManager.getAllRecipesFor((RecipeType) typedRecipeType);
            for (Recipe<?> recipe : recipes) {
                for (String outputId : outputIds(recipe)) {
                    byOutput.computeIfAbsent(outputId, ignored -> new ArrayList<>()).add(recipe);
                }
            }

            if (ACOConfig.logGtceuRecipeIntentFastPath()) {
                AE2CraftingOptimizer.LOGGER.info(
                        "Built GTCEu recipe intent output index for {}: {} output key(s), {} recipe(s)",
                        recipeType,
                        byOutput.size(),
                        recipes.size());
            }
            return new OutputRecipeIndex(byOutput);
        }

        List<Object> recipesForOutput(String outputId) {
            List<Object> recipes = byOutputId.get(outputId);
            return recipes == null ? List.of() : recipes;
        }

        private static List<String> outputIds(Object recipe) {
            try {
                Object outputs = getField(recipe, "outputs");
                if (!(outputs instanceof Map<?, ?> outputMap)) {
                    return List.of();
                }
                List<String> ids = new ArrayList<>();
                for (Object value : outputMap.values()) {
                    if (!(value instanceof List<?> contents)) {
                        continue;
                    }
                    for (Object content : contents) {
                        collectContentOutputIds(content, ids);
                    }
                }
                return ids;
            } catch (Throwable throwable) {
                logReflectionFailure("outputIds", throwable);
                return List.of();
            }
        }

        private static void collectContentOutputIds(Object content, List<String> ids) throws ReflectiveOperationException {
            Object value = invoke(content, "getContent");
            if (value instanceof Ingredient ingredient) {
                for (ItemStack stack : ingredient.getItems()) {
                    if (stack.isEmpty()) {
                        continue;
                    }
                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                    if (id != null) {
                        ids.add(id.toString());
                    }
                }
                return;
            }

            if (value instanceof FluidStack fluidStack) {
                if (fluidStack.isEmpty()) {
                    return;
                }
                ResourceLocation id = ForgeRegistries.FLUIDS.getKey(fluidStack.getFluid());
                if (id != null) {
                    ids.add(id.toString());
                }
                return;
            }

            Method getStacks;
            try {
                getStacks = findMethod(value.getClass(), "getStacks");
            } catch (NoSuchMethodException ignored) {
                return;
            }
            getStacks.setAccessible(true);
            Object stacks = getStacks.invoke(value);
            if (!(stacks instanceof FluidStack[] fluidStacks)) {
                return;
            }
            for (FluidStack stack : fluidStacks) {
                if (stack.isEmpty()) {
                    continue;
                }
                ResourceLocation id = ForgeRegistries.FLUIDS.getKey(stack.getFluid());
                if (id != null) {
                    ids.add(id.toString());
                }
            }
        }
    }

    private static final class IntentFirstIterator implements Iterator<Object> {
        private final Iterator<?> intentCandidates;
        private final Iterator<?> original;
        private final Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        private Object nextOriginal;
        private boolean nextOriginalReady;

        private IntentFirstIterator(List<Object> intentCandidates, Iterator<?> original) {
            this.intentCandidates = intentCandidates.iterator();
            this.original = original;
        }

        @Override
        public boolean hasNext() {
            if (intentCandidates.hasNext()) {
                return true;
            }
            if (nextOriginalReady) {
                return true;
            }
            while (original.hasNext()) {
                Object candidate = original.next();
                if (seen.add(candidate)) {
                    nextOriginal = candidate;
                    nextOriginalReady = true;
                    return true;
                }
            }
            return false;
        }

        @Override
        public Object next() {
            if (intentCandidates.hasNext()) {
                Object candidate = intentCandidates.next();
                seen.add(candidate);
                return candidate;
            }
            if (nextOriginalReady) {
                Object result = nextOriginal;
                nextOriginal = null;
                nextOriginalReady = false;
                return result;
            }
            Object candidate = original.next();
            seen.add(candidate);
            return candidate;
        }
    }
}
