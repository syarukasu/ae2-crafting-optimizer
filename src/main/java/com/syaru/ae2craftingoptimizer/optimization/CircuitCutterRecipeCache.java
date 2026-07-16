package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.externalstorage.GenericStackInv;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

public final class CircuitCutterRecipeCache {
    private static final String CONTEXT_SUFFIX = "TileCircuitCutter$CutterRecipeContext";
    private static final Map<Signature, CachedResult> RECIPES = new LinkedHashMap<>(16, 0.75f, true);
    private static final Map<Class<?>, Accessor> ACCESSORS = new ConcurrentHashMap<>();
    private static final Accessor UNSUPPORTED = new Accessor(null, null, null);

    private CircuitCutterRecipeCache() {
    }

    public static boolean supports(Object context) {
        return ACOConfig.cacheCircuitCutterRecipes()
                && context != null
                && context.getClass().getName().endsWith(CONTEXT_SUFFIX);
    }

    public static Lookup find(Object context, Predicate<Recipe<?>> validator) {
        Signature signature = signature(context);
        if (signature == null) {
            return Lookup.MISS;
        }

        CachedResult cached;
        synchronized (RECIPES) {
            cached = RECIPES.get(signature);
        }
        if (cached == null) {
            return Lookup.MISS;
        }
        if (cached.recipe == null) {
            return ACOConfig.cacheCircuitCutterNegativeResults() ? new Lookup(true, null) : Lookup.MISS;
        }

        if (validator.test(cached.recipe)) {
            return new Lookup(true, cached.recipe);
        }

        synchronized (RECIPES) {
            RECIPES.remove(signature);
        }
        return Lookup.MISS;
    }

    public static void remember(Object context, Recipe<?> recipe) {
        if (recipe == null && !ACOConfig.cacheCircuitCutterNegativeResults()) {
            return;
        }

        Signature signature = signature(context);
        if (signature == null) {
            return;
        }

        synchronized (RECIPES) {
            RECIPES.put(signature, new CachedResult(recipe));
            int maximum = ACOConfig.getCircuitCutterRecipeCacheSize();
            while (RECIPES.size() > maximum) {
                var iterator = RECIPES.entrySet().iterator();
                iterator.next();
                iterator.remove();
            }
        }
    }

    public static void clear() {
        synchronized (RECIPES) {
            RECIPES.clear();
        }
    }

    private static Signature signature(Object context) {
        if (!supports(context)) {
            return null;
        }

        Accessor accessor = ACCESSORS.computeIfAbsent(context.getClass(), CircuitCutterRecipeCache::createAccessor);
        if (accessor == UNSUPPORTED) {
            return null;
        }

        try {
            Object host = accessor.hostField.get(context);
            InternalInventory input = (InternalInventory) accessor.getInput.invoke(host);
            GenericStackInv tank = (GenericStackInv) accessor.getTank.invoke(host);

            ItemStack stack = input.getStackInSlot(0);
            AEItemKey itemKey = stack.isEmpty() ? null : AEItemKey.of(stack);
            int itemAmount = stack.isEmpty() ? 0 : stack.getCount();

            GenericStack fluid = tank.getStack(0);
            AEKey fluidKey = fluid == null ? null : fluid.what();
            long fluidAmount = fluid == null ? 0 : fluid.amount();
            return new Signature(itemKey, itemAmount, fluidKey, fluidAmount);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            ACCESSORS.put(context.getClass(), UNSUPPORTED);
            AE2CraftingOptimizer.LOGGER.warn(
                    "Disabled ExtendedAE Circuit Cutter cache for incompatible context {}",
                    context.getClass().getName(),
                    exception);
            return null;
        }
    }

    private static Accessor createAccessor(Class<?> contextClass) {
        try {
            Field hostField = contextClass.getDeclaredField("host");
            hostField.setAccessible(true);
            Class<?> hostClass = hostField.getType();
            Method getInput = hostClass.getMethod("getInput");
            Method getTank = hostClass.getMethod("getTank");
            return new Accessor(hostField, getInput, getTank);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            AE2CraftingOptimizer.LOGGER.warn(
                    "ExtendedAE Circuit Cutter integration is unavailable for {}",
                    contextClass.getName(),
                    exception);
            return UNSUPPORTED;
        }
    }

    private record Signature(AEItemKey itemKey, int itemAmount, AEKey fluidKey, long fluidAmount) {
    }

    private record CachedResult(Recipe<?> recipe) {
    }

    public record Lookup(boolean hit, Recipe<?> recipe) {
        private static final Lookup MISS = new Lookup(false, null);
    }

    private record Accessor(Field hostField, Method getInput, Method getTank) {
    }
}
