package com.syaru.ae2craftingoptimizer.mekanism;

import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.intent.RecipeIntent;
import com.syaru.ae2craftingoptimizer.intent.RecipeIntentRegistry;
import com.syaru.ae2craftingoptimizer.intent.StackIntent;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

public final class MekanismRecipeIntentFastPath {
    private static final Object LOCK = new Object();
    private static final Map<Object, OutputRecipeIndex> INDEXES = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Set<String> REFLECTION_FAILURES_LOGGED = Collections.synchronizedSet(new LinkedHashSet<>());

    private MekanismRecipeIntentFastPath() {
    }

    public static Object findRecipe(Object tile, int cacheIndex) {
        if (!ACOConfig.enableMekanismRecipeIntentFastPath()) {
            return null;
        }
        if (!(tile instanceof BlockEntity blockEntity)) {
            return null;
        }
        Level rawLevel = blockEntity.getLevel();
        if (!(rawLevel instanceof ServerLevel level)) {
            return null;
        }

        try {
            BlockPos pos = blockEntity.getBlockPos();
            List<RecipeIntent> intents = RecipeIntentRegistry.findForTarget(
                    level.dimension().location(),
                    pos,
                    level.getGameTime());
            if (intents.isEmpty()) {
                return null;
            }

            Object recipeType = invoke(tile, "getRecipeType");
            if (recipeType == null) {
                return null;
            }
            List<Object> inputs = collectInputs(tile, cacheIndex);
            if (inputs.isEmpty()) {
                return null;
            }

            OutputRecipeIndex index = getOrCreateIndex(recipeType, level);
            List<Object> candidates = collectCandidates(intents, index);
            for (Object candidate : candidates) {
                if (recipeMatchesInputs(candidate, inputs)) {
                    if (ACOConfig.logMekanismRecipeIntentFastPath()) {
                        AE2CraftingOptimizer.LOGGER.info(
                                "ACO Mekanism intent fast path: selected {} for {} {} with {} input(s)",
                                recipeId(candidate),
                                level.dimension().location(),
                                pos.toShortString(),
                                inputs.size());
                    }
                    return candidate;
                }
            }

            if (ACOConfig.logMekanismRecipeIntentFastPath() && !candidates.isEmpty()) {
                AE2CraftingOptimizer.LOGGER.info(
                        "ACO Mekanism intent fast path: {} output candidate(s) did not match current inputs for {} {}",
                        candidates.size(),
                        level.dimension().location(),
                        pos.toShortString());
            }
            return null;
        } catch (Throwable throwable) {
            logReflectionFailure("findRecipe:" + tile.getClass().getName(), throwable);
            return null;
        }
    }

    public static void clearIndexes(String reason) {
        synchronized (LOCK) {
            INDEXES.clear();
        }
        if (ACOConfig.logMekanismRecipeIntentFastPath()) {
            AE2CraftingOptimizer.LOGGER.info("Cleared Mekanism recipe intent indexes: {}", reason);
        }
    }

    private static OutputRecipeIndex getOrCreateIndex(Object recipeType, ServerLevel level) {
        synchronized (LOCK) {
            OutputRecipeIndex existing = INDEXES.get(recipeType);
            if (existing != null) {
                return existing;
            }
            OutputRecipeIndex created = OutputRecipeIndex.build(recipeType, level);
            if (INDEXES.size() >= ACOConfig.getMekanismRecipeIntentIndexCacheSize()) {
                var iterator = INDEXES.keySet().iterator();
                if (iterator.hasNext()) {
                    iterator.next();
                    iterator.remove();
                }
            }
            INDEXES.put(recipeType, created);
            return created;
        }
    }

    private static List<Object> collectCandidates(List<RecipeIntent> intents, OutputRecipeIndex index) {
        List<Object> candidates = new ArrayList<>();
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        int limit = ACOConfig.getMekanismRecipeIntentMaximumCandidates();
        for (int i = intents.size() - 1; i >= 0 && candidates.size() < limit; i--) {
            RecipeIntent intent = intents.get(i);
            for (StackIntent output : intent.outputs()) {
                if (output == null || output.amount() <= 0 || output.keyId() == null || output.keyId().isBlank()) {
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
        return candidates;
    }

    private static List<Object> collectInputs(Object tile, int cacheIndex) throws ReflectiveOperationException {
        List<InputHandlerCandidate> handlers = new ArrayList<>();
        Set<Object> seenHandlers = Collections.newSetFromMap(new IdentityHashMap<>());
        int depth = 0;
        Class<?> current = tile.getClass();
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                String fieldName = field.getName();
                String lowerName = fieldName.toLowerCase();
                if (lowerName.contains("output") || lowerName.contains("energy")) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(tile);
                if (value == null) {
                    continue;
                }
                if (value.getClass().isArray()) {
                    if (!lowerName.contains("inputhandlers")) {
                        continue;
                    }
                    int length = Array.getLength(value);
                    if (cacheIndex < 0 || cacheIndex >= length) {
                        continue;
                    }
                    Object handler = Array.get(value, cacheIndex);
                    addInputHandler(handlers, seenHandlers, fieldName, handler, 0, depth);
                    continue;
                }
                if (!lowerName.contains("inputhandler")) {
                    continue;
                }
                addInputHandler(handlers, seenHandlers, fieldName, value, inputPriority(fieldName), depth);
            }
            current = current.getSuperclass();
            depth++;
        }

        handlers.sort(Comparator
                .comparingInt(InputHandlerCandidate::priority)
                .thenComparingInt(InputHandlerCandidate::depth)
                .thenComparing(InputHandlerCandidate::fieldName));

        List<Object> inputs = new ArrayList<>();
        for (InputHandlerCandidate handler : handlers) {
            Object input = invoke(handler.handler(), "getInput");
            if (input != null) {
                inputs.add(input);
            }
        }
        return inputs;
    }

    private static void addInputHandler(
            List<InputHandlerCandidate> handlers,
            Set<Object> seenHandlers,
            String fieldName,
            Object handler,
            int priority,
            int depth
    ) {
        if (handler == null || !seenHandlers.add(handler)) {
            return;
        }
        if (findNoArgMethod(handler.getClass(), "getInput") == null) {
            return;
        }
        handlers.add(new InputHandlerCandidate(fieldName, handler, priority, depth));
    }

    private static int inputPriority(String fieldName) {
        String name = fieldName.toLowerCase();
        if (name.equals("inputhandler") || name.contains("iteminput") || name.contains("maininput")) {
            return 10;
        }
        if (name.contains("leftinput")) {
            return 10;
        }
        if (name.contains("rightinput") || name.contains("extrainput")) {
            return 20;
        }
        if (name.contains("fluidinput")) {
            return 20;
        }
        if (name.contains("gasinput") || name.contains("infusioninput") || name.contains("slurryinput") || name.contains("pigmentinput")) {
            return 30;
        }
        return 40;
    }

    private static boolean recipeMatchesInputs(Object recipe, List<Object> inputs) {
        if (recipe == null || inputs.isEmpty()) {
            return false;
        }
        for (Method method : testMethods(recipe.getClass())) {
            int arity = method.getParameterCount();
            if (arity <= 0 || arity > inputs.size()) {
                continue;
            }
            if (arity == 1) {
                for (Object input : inputs) {
                    if (invokeBooleanMethod(recipe, method, input)) {
                        return true;
                    }
                }
                continue;
            }
            Object[] args = new Object[arity];
            for (int i = 0; i < arity; i++) {
                args[i] = inputs.get(i);
            }
            if (invokeBooleanMethod(recipe, method, args)) {
                return true;
            }
        }
        return false;
    }

    private static List<Method> testMethods(Class<?> type) {
        List<Method> methods = new ArrayList<>();
        for (Method method : type.getMethods()) {
            if (!"test".equals(method.getName())
                    || method.getReturnType() != boolean.class
                    || method.getParameterCount() < 1
                    || method.getParameterCount() > 3) {
                continue;
            }
            methods.add(method);
        }
        methods.sort(Comparator
                .comparingInt(MekanismRecipeIntentFastPath::objectParameterCount)
                .thenComparing((Method method) -> method.isBridge() ? 1 : 0)
                .thenComparing(Method::getParameterCount));
        return methods;
    }

    private static int objectParameterCount(Method method) {
        int count = 0;
        for (Class<?> parameterType : method.getParameterTypes()) {
            if (parameterType == Object.class) {
                count++;
            }
        }
        return count;
    }

    private static boolean invokeBooleanMethod(Object target, Method method, Object... args) {
        if (!parametersAccept(method.getParameterTypes(), args)) {
            return false;
        }
        try {
            method.setAccessible(true);
            return Boolean.TRUE.equals(method.invoke(target, args));
        } catch (ClassCastException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ignored) {
            return false;
        }
    }

    private static boolean parametersAccept(Class<?>[] parameterTypes, Object[] args) {
        if (parameterTypes.length != args.length) {
            return false;
        }
        for (int i = 0; i < parameterTypes.length; i++) {
            Object arg = args[i];
            if (arg == null) {
                continue;
            }
            Class<?> parameterType = wrap(parameterTypes[i]);
            if (!parameterType.isAssignableFrom(arg.getClass())) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }

    private static Object invoke(Object target, String methodName, Object... args) throws ReflectiveOperationException {
        Method method = findCompatibleMethod(target.getClass(), methodName, args);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static Method findCompatibleMethod(Class<?> type, String methodName, Object... args) throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(methodName) && parametersAccept(method.getParameterTypes(), args)) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        for (Method method : type.getMethods()) {
            if (method.getName().equals(methodName) && parametersAccept(method.getParameterTypes(), args)) {
                return method;
            }
        }
        throw new NoSuchMethodException(type.getName() + "#" + methodName);
    }

    private static Method findNoArgMethod(Class<?> type, String methodName) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(methodName);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        try {
            return type.getMethod(methodName);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static String recipeId(Object recipe) {
        try {
            Object id = invoke(recipe, "m_6423_");
            return String.valueOf(id);
        } catch (Throwable ignored) {
            return recipe.getClass().getName();
        }
    }

    private static void logReflectionFailure(String key, Throwable throwable) {
        if (!ACOConfig.logMekanismRecipeIntentFastPath() || !REFLECTION_FAILURES_LOGGED.add(key)) {
            return;
        }
        AE2CraftingOptimizer.LOGGER.warn("ACO Mekanism recipe intent fast path skipped for this call: {}", throwable.toString());
    }

    private record InputHandlerCandidate(String fieldName, Object handler, int priority, int depth) {
    }

    private static final class OutputRecipeIndex {
        private final Map<String, List<Object>> byOutputId;

        private OutputRecipeIndex(Map<String, List<Object>> byOutputId) {
            this.byOutputId = byOutputId;
        }

        static OutputRecipeIndex build(Object recipeType, ServerLevel level) {
            Map<String, List<Object>> byOutput = new LinkedHashMap<>();
            List<?> recipes = recipesFor(recipeType, level);
            for (Object recipe : recipes) {
                for (String outputId : outputIds(recipe)) {
                    byOutput.computeIfAbsent(outputId, ignored -> new ArrayList<>()).add(recipe);
                }
            }
            if (ACOConfig.logMekanismRecipeIntentFastPath()) {
                AE2CraftingOptimizer.LOGGER.info(
                        "Built Mekanism recipe intent output index for {}: {} output key(s), {} recipe(s)",
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

        private static List<?> recipesFor(Object recipeType, ServerLevel level) {
            try {
                Object recipes = invoke(recipeType, "getRecipes", level);
                if (recipes instanceof List<?> list) {
                    return list;
                }
            } catch (Throwable throwable) {
                logReflectionFailure("mekanismRecipesFor:" + recipeType.getClass().getName(), throwable);
            }
            return List.of();
        }

        private static Set<String> outputIds(Object recipe) {
            Set<String> ids = new LinkedHashSet<>();
            for (Method method : recipe.getClass().getMethods()) {
                if (method.getParameterCount() != 0 || method.getReturnType() == void.class || !isOutputDefinitionMethod(method.getName())) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    collectStackIds(method.invoke(recipe), ids, 0);
                } catch (Throwable throwable) {
                    logReflectionFailure("mekanismOutputIds:" + recipe.getClass().getName() + "#" + method.getName(), throwable);
                }
            }
            return ids;
        }

        private static boolean isOutputDefinitionMethod(String name) {
            return "getOutputDefinition".equals(name) || name.endsWith("OutputDefinition");
        }

        private static void collectStackIds(Object value, Set<String> ids, int depth) throws ReflectiveOperationException {
            if (value == null || depth > 5) {
                return;
            }
            if (value instanceof ItemStack stack) {
                if (!stack.isEmpty()) {
                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                    if (id != null) {
                        ids.add(id.toString());
                    }
                }
                return;
            }
            if (value instanceof FluidStack stack) {
                if (!stack.isEmpty()) {
                    ResourceLocation id = ForgeRegistries.FLUIDS.getKey(stack.getFluid());
                    if (id != null) {
                        ids.add(id.toString());
                    }
                }
                return;
            }
            if (value instanceof Iterable<?> iterable) {
                for (Object element : iterable) {
                    collectStackIds(element, ids, depth + 1);
                }
                return;
            }
            if (value.getClass().isArray()) {
                int length = Array.getLength(value);
                for (int i = 0; i < length; i++) {
                    collectStackIds(Array.get(value, i), ids, depth + 1);
                }
                return;
            }
            if (isEmptyStackLike(value)) {
                return;
            }
            Method typeRegistryName = findNoArgMethod(value.getClass(), "getTypeRegistryName");
            if (typeRegistryName != null) {
                typeRegistryName.setAccessible(true);
                Object id = typeRegistryName.invoke(value);
                if (id instanceof ResourceLocation resourceLocation) {
                    ids.add(resourceLocation.toString());
                }
                return;
            }
            Method chemicalStack = findNoArgMethod(value.getClass(), "getChemicalStack");
            if (chemicalStack != null) {
                chemicalStack.setAccessible(true);
                collectStackIds(chemicalStack.invoke(value), ids, depth + 1);
                return;
            }
            if (value.getClass().isRecord()) {
                for (RecordComponent component : value.getClass().getRecordComponents()) {
                    collectStackIds(component.getAccessor().invoke(value), ids, depth + 1);
                }
            }
        }

        private static boolean isEmptyStackLike(Object value) {
            Method isEmpty = findNoArgMethod(value.getClass(), "isEmpty");
            if (isEmpty == null || isEmpty.getReturnType() != boolean.class) {
                return false;
            }
            try {
                isEmpty.setAccessible(true);
                return Boolean.TRUE.equals(isEmpty.invoke(value));
            } catch (ReflectiveOperationException ignored) {
                return false;
            }
        }
    }
}
