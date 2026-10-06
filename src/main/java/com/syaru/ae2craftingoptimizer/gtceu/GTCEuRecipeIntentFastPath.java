package com.syaru.ae2craftingoptimizer.gtceu;

import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.intent.RecipeIntent;
import com.syaru.ae2craftingoptimizer.intent.RecipeIntentRegistry;
import com.syaru.ae2craftingoptimizer.intent.StackIntent;
import com.syaru.ae2craftingoptimizer.intent.IntentSearchIterator;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import java.math.BigInteger;
import java.util.function.Supplier;
import com.syaru.ae2craftingoptimizer.optimization.OptimizationMetrics;
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
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

public final class GTCEuRecipeIntentFastPath {
    private static final Object LOCK = new Object();
    private static final Map<Object, OutputRecipeIndex> INDEXES = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<TargetKey, CachedCandidates> RESOLVED_CANDIDATES = new LinkedHashMap<>();
    private static final Set<String> REFLECTION_FAILURES_LOGGED = Collections.synchronizedSet(new LinkedHashSet<>());
    private static long lastResolvedCleanupTick = Long.MIN_VALUE;

    private GTCEuRecipeIntentFastPath() {
    }

    public static Iterator<?> wrapSearchIterator(Object recipeLogic, Iterator<?> original) {
        if (original == null) return null;
        return search(recipeLogic, () -> original);
    }

    public static Iterator<?> search(Object recipeLogic, Supplier<? extends Iterator<?>> original) {
        if (!ACOConfig.enableGtceuRecipeIntentFastPath() || recipeLogic == null) return original.get();
        List<Object> candidates;
        try { candidates = candidates(recipeLogic); }
        catch (ReflectiveOperationException | RuntimeException | LinkageError throwable) {
            logReflectionFailure("lookup", throwable);
            candidates = List.of();
        }
        // Native failures must propagate once, not be swallowed and retried as hint failures.
        return candidates.isEmpty() ? original.get() : new IntentSearchIterator<>(candidates.iterator(), original);
    }

    private static List<Object> candidates(Object recipeLogic) throws ReflectiveOperationException {
            Context context = Context.from(recipeLogic);
            if (context == null) {
                return List.of();
            }

            List<RecipeIntent> intents = RecipeIntentRegistry.findForTarget(
                    context.dimension(),
                    context.machinePos(),
                    context.gameTime());
            if (intents.isEmpty() && ACOConfig.getGtceuRecipeIntentSearchRadius() > 0) {
                intents = RecipeIntentRegistry.findNearby(
                        context.dimension(),
                        context.machinePos(),
                        ACOConfig.getGtceuRecipeIntentSearchRadius(),
                        context.gameTime(),
                        ACOConfig.getGtceuRecipeIntentNearbyMaximumEntries());
            }
            if (intents.isEmpty()) {
                return List.of();
            }

            Set<String> outputKeys = outputKeys(intents);
            if (outputKeys.isEmpty()) {
                return List.of();
            }
            Set<String> inputKeys = inputKeys(intents);
            OutputRecipeIndex index = getOrCreateIndex(context.recipeType());
            List<Object> candidates = findResolvedCandidates(context, outputKeys, inputKeys, index);
            if (candidates == null) {
                candidates = collectCandidates(outputKeys, inputKeys, index);
                rememberResolvedCandidates(context, outputKeys, inputKeys, intents, candidates, index);
            }

            // #179: ID-only candidates are advisory, never a proof of the intended NBT output.
            var capturedIntents = intents;
            candidates = candidates.stream().filter(recipe -> matchesIntent(recipe, capturedIntents)).toList();
            if (candidates.size() != 1) {
                return List.of();
            }

            if (ACOConfig.logGtceuRecipeIntentFastPath()) {
                AE2CraftingOptimizer.LOGGER.info(
                        "ACO GTCEu intent fast path: {} candidate(s) for {} {}",
                        candidates.size(),
                        context.dimension(),
                        context.machinePos());
            }
            return candidates;
    }

    public static void clearIndexes(String reason) {
        synchronized (LOCK) {
            INDEXES.clear();
            RESOLVED_CANDIDATES.clear();
            lastResolvedCleanupTick = Long.MIN_VALUE;
        }
        if (ACOConfig.logGtceuRecipeIntentFastPath()) {
            AE2CraftingOptimizer.LOGGER.info("Cleared GTCEu recipe intent indexes: {}", reason);
        }
    }

    static boolean matchesIntent(Object recipe, List<RecipeIntent> hints) {
        try {
            for (var hint : hints) {
                Map<AEKey, BigInteger> expected = new LinkedHashMap<>();
                Map<AEKey, BigInteger> inputs = new LinkedHashMap<>();
                boolean exact = true;
                for (var output : hint.outputs()) {
                    if (output.exactKey() == null || output.amount() <= 0) { exact = false; break; }
                    expected.merge(output.exactKey(), BigInteger.valueOf(output.amount()), BigInteger::add);
                }
                for (var slot : hint.inputs()) {
                    if (slot.possibleInputs().size() != 1 || slot.multiplier() <= 0) { exact = false; break; }
                    var input = slot.possibleInputs().get(0);
                    if (input.exactKey() == null || input.amount() <= 0) { exact = false; break; }
                    inputs.merge(input.exactKey(), BigInteger.valueOf(input.amount())
                            .multiply(BigInteger.valueOf(slot.multiplier())), BigInteger::add);
                }
                if (!exact || expected.isEmpty()) continue;
                var actual = exactContents(recipe, "outputs", expected.keySet());
                var consumed = exactContents(recipe, "inputs", inputs.keySet());
                if (actual == null || consumed == null || !actual.keySet().equals(expected.keySet())
                        || !consumed.keySet().equals(inputs.keySet())) continue;
                var first = actual.keySet().iterator().next();
                if (actual.entrySet().stream().allMatch(e -> e.getValue().multiply(expected.get(first))
                        .equals(expected.get(e.getKey()).multiply(actual.get(first))))
                        && consumed.entrySet().stream().allMatch(e -> e.getValue().multiply(expected.get(first))
                        .equals(inputs.get(e.getKey()).multiply(actual.get(first))))) return true;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unsupported) {
            logReflectionFailure("exactOutputs", unsupported);
        }
        return false;
    }

    private static Map<AEKey, BigInteger> exactContents(Object recipe, String field, Set<AEKey> expected)
            throws ReflectiveOperationException {
        if (!(getField(recipe, field) instanceof Map<?, ?> map)) return null;
        Map<AEKey, BigInteger> result = new LinkedHashMap<>();
        for (var contents : map.values()) {
            if (!(contents instanceof List<?> list)) return null;
            for (var content : list) {
                Object chance = getField(content, "chance"), maximum = getField(content, "maxChance");
                if (!(chance instanceof Number c) || !(maximum instanceof Number m) || c.intValue() != m.intValue())
                    return null;
                Object value = invoke(content, "getContent");
                List<StackIntent> choices = new ArrayList<>();
                if (value instanceof Ingredient ingredient) {
                    for (var stack : ingredient.getItems()) if (!stack.isEmpty())
                        choices.add(StackIntent.of(AEItemKey.of(stack), stack.getCount()));
                } else {
                    FluidStack[] fluids;
                    if (value instanceof FluidStack stack) fluids = new FluidStack[]{stack};
                    else {
                        var stacks = invoke(value, "getStacks");
                        if (!(stacks instanceof FluidStack[] array)) return null;
                        fluids = array;
                    }
                    for (var fluid : fluids) if (!fluid.isEmpty())
                        choices.add(StackIntent.of(AEFluidKey.of(fluid), fluid.getAmount()));
                }
                if (field.equals("outputs") && choices.size() != 1) return null;
                var matching = choices.stream().filter(s -> expected.contains(s.exactKey())).distinct().toList();
                if (matching.size() != 1) return null;
                result.merge(matching.get(0).exactKey(), BigInteger.valueOf(matching.get(0).amount()), BigInteger::add);
            }
        }
        return result;
    }

    private static List<Object> collectCandidates(
            Set<String> outputKeys,
            Set<String> inputKeys,
            OutputRecipeIndex index) {
        List<Object> inputMatched = new ArrayList<>();
        List<Object> fallback = new ArrayList<>();
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (String outputKey : outputKeys) {
            for (Object recipe : index.recipesForOutput(outputKey)) {
                if (seen.add(recipe)) {
                    if (!inputKeys.isEmpty() && index.inputIds(recipe).containsAll(inputKeys)) {
                        inputMatched.add(recipe);
                    } else {
                        fallback.add(recipe);
                    }
                }
            }
        }
        // Inspect all matches before deciding uniqueness; a configured prefix is not proof.
        List<Object> candidates = new ArrayList<>(inputMatched.size() + fallback.size());
        candidates.addAll(inputMatched);
        candidates.addAll(fallback);
        return List.copyOf(candidates);
    }

    private static List<Object> findResolvedCandidates(
            Context context,
            Set<String> outputKeys,
            Set<String> inputKeys,
            OutputRecipeIndex index) {
        if (!ACOConfig.cacheResolvedRecipeIntents()) {
            return null;
        }
        TargetKey key = new TargetKey(context.dimension(), context.machinePos());
        synchronized (LOCK) {
            cleanupResolvedCandidates(context.gameTime());
            CachedCandidates cached = RESOLVED_CANDIDATES.get(key);
            if (cached == null
                    || cached.recipeType != context.recipeType()
                    || cached.index != index
                    || !cached.outputKeys.equals(outputKeys)
                    || !cached.inputKeys.equals(inputKeys)
                    || cached.expiresTick <= context.gameTime()) {
                OptimizationMetrics.recordGtCandidateCache(false);
                return null;
            }
            OptimizationMetrics.recordGtCandidateCache(true);
            return cached.candidates;
        }
    }

    private static void rememberResolvedCandidates(
            Context context,
            Set<String> outputKeys,
            Set<String> inputKeys,
            List<RecipeIntent> intents,
            List<Object> candidates,
            OutputRecipeIndex index) {
        if (!ACOConfig.cacheResolvedRecipeIntents() || candidates.isEmpty()) {
            return;
        }
        synchronized (LOCK) {
            cleanupResolvedCandidates(context.gameTime());
            while (RESOLVED_CANDIDATES.size() >= ACOConfig.getResolvedRecipeIntentCacheSize()) {
                Iterator<TargetKey> iterator = RESOLVED_CANDIDATES.keySet().iterator();
                if (!iterator.hasNext()) {
                    break;
                }
                iterator.next();
                iterator.remove();
            }
            RESOLVED_CANDIDATES.put(
                    new TargetKey(context.dimension(), context.machinePos()),
                    new CachedCandidates(
                            context.recipeType(),
                            outputKeys,
                            inputKeys,
                            candidates,
                            earliestExpiration(intents), index));
        }
    }

    private static void cleanupResolvedCandidates(long now) {
        if (lastResolvedCleanupTick != Long.MIN_VALUE
                && now >= lastResolvedCleanupTick
                && now - lastResolvedCleanupTick < 20L) {
            return;
        }
        RESOLVED_CANDIDATES.values().removeIf(entry -> entry.expiresTick <= now);
        lastResolvedCleanupTick = now;
    }

    private static Set<String> outputKeys(List<RecipeIntent> intents) {
        Set<String> outputKeys = new LinkedHashSet<>();
        for (int i = intents.size() - 1; i >= 0; i--) {
            RecipeIntent intent = intents.get(i);
            for (StackIntent output : intent.outputs()) {
                if (isOutput(output)) {
                    outputKeys.add(output.keyId());
                }
            }
        }
        return Collections.unmodifiableSet(outputKeys);
    }

    private static Set<String> inputKeys(List<RecipeIntent> intents) {
        Set<String> inputKeys = new LinkedHashSet<>();
        for (int i = intents.size() - 1; i >= 0; i--) {
            for (StackIntent input : intents.get(i).concreteInputs()) {
                if (isOutput(input)) {
                    inputKeys.add(input.keyId());
                }
            }
        }
        return Collections.unmodifiableSet(inputKeys);
    }

    private static long earliestExpiration(List<RecipeIntent> intents) {
        long expiration = Long.MAX_VALUE;
        for (RecipeIntent intent : intents) {
            expiration = Math.min(expiration, intent.expiresTick());
        }
        return expiration;
    }

    private static OutputRecipeIndex getOrCreateIndex(Object recipeType) throws ReflectiveOperationException {
        if (!(invoke(recipeType, "db") instanceof GTRecipeDbView view))
            throw new IllegalStateException("Native GT database view is not installed");
        synchronized (LOCK) {
            OutputRecipeIndex existing = INDEXES.get(recipeType);
            if (existing != null && existing.revision == view.aco$recipeRevision()) {
                return existing;
            }
            OutputRecipeIndex created = OutputRecipeIndex.build(recipeType, view);
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

    private record Context(ResourceLocation dimension, BlockPos machinePos, long gameTime, Object recipeType) {
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
            return new Context(level.dimension().location(), pos.immutable(), level.getGameTime(), recipeType);
        }
    }

    private record TargetKey(ResourceLocation dimension, BlockPos machinePos) {
        private TargetKey {
            machinePos = machinePos.immutable();
        }
    }

    private record CachedCandidates(
            Object recipeType,
            Set<String> outputKeys,
            Set<String> inputKeys,
            List<Object> candidates,
            long expiresTick,
            OutputRecipeIndex index) {
        private CachedCandidates {
            outputKeys = Collections.unmodifiableSet(new LinkedHashSet<>(outputKeys));
            inputKeys = Collections.unmodifiableSet(new LinkedHashSet<>(inputKeys));
            candidates = List.copyOf(candidates);
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
        private final Map<Object, Set<String>> inputIdsByRecipe;
        private final long revision;

        private OutputRecipeIndex(Map<String, List<Object>> byOutputId, Map<Object, Set<String>> inputIdsByRecipe, long revision) {
            this.byOutputId = byOutputId;
            this.inputIdsByRecipe = inputIdsByRecipe;
            this.revision = revision;
        }

        static OutputRecipeIndex build(Object recipeType, GTRecipeDbView view) {
            Map<String, List<Object>> byOutput = new LinkedHashMap<>();
            Map<Object, Set<String>> inputIdsByRecipe = new IdentityHashMap<>();
            var recipes = view.aco$acceptedRecipes();
            for (Object recipe : recipes) {
                for (String outputId : outputIds(recipe)) {
                    byOutput.computeIfAbsent(outputId, ignored -> new ArrayList<>()).add(recipe);
                }
                inputIdsByRecipe.put(recipe, Collections.unmodifiableSet(new LinkedHashSet<>(contentIds(recipe, "inputs"))));
            }

            if (ACOConfig.logGtceuRecipeIntentFastPath()) {
                AE2CraftingOptimizer.LOGGER.info(
                        "Built GTCEu recipe intent output index for {}: {} output key(s), {} recipe(s)",
                        recipeType,
                        byOutput.size(),
                        recipes.size());
            }
            return new OutputRecipeIndex(byOutput, inputIdsByRecipe, view.aco$recipeRevision());
        }

        List<Object> recipesForOutput(String outputId) {
            List<Object> recipes = byOutputId.get(outputId);
            return recipes == null ? List.of() : recipes;
        }

        Set<String> inputIds(Object recipe) {
            Set<String> inputIds = inputIdsByRecipe.get(recipe);
            return inputIds == null ? Set.of() : inputIds;
        }

        private static List<String> outputIds(Object recipe) {
            return contentIds(recipe, "outputs");
        }

        private static List<String> contentIds(Object recipe, String fieldName) {
            try {
                Object contentsByCapability = getField(recipe, fieldName);
                if (!(contentsByCapability instanceof Map<?, ?> contentMap)) {
                    return List.of();
                }
                List<String> ids = new ArrayList<>();
                for (Object value : contentMap.values()) {
                    if (!(value instanceof List<?> contents)) {
                        continue;
                    }
                    for (Object content : contents) {
                        collectContentIds(content, ids);
                    }
                }
                return ids;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError throwable) {
                logReflectionFailure(fieldName + "Ids", throwable);
                return List.of();
            }
        }

        private static void collectContentIds(Object content, List<String> ids) throws ReflectiveOperationException {
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

}
