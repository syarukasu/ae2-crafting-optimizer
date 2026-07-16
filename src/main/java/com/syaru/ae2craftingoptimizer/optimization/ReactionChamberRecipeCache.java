package com.syaru.ae2craftingoptimizer.optimization;

import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ReactionChamberRecipeCache {
    private static final AtomicBoolean LOGGED_FAILURE = new AtomicBoolean();
    private static volatile boolean disabled;

    private ReactionChamberRecipeCache() {
    }

    public static void remember(Object chamber, Object recipe) {
        if (disabled || !ACOConfig.cacheReactionChamberRecipe() || chamber == null || recipe == null) {
            return;
        }
        try {
            Field cachedTask = ReflectionLookupCache.findFieldInHierarchy(chamber.getClass(), "cachedTask");
            cachedTask.setAccessible(true);
            cachedTask.set(chamber, recipe);
            OptimizationMetrics.recordReactionChamberRecipeReuse();
        } catch (ReflectiveOperationException | RuntimeException exception) {
            disabled = true;
            if (LOGGED_FAILURE.compareAndSet(false, true)) {
                AE2CraftingOptimizer.LOGGER.warn(
                        "ACO could not seed AdvancedAE Reaction Chamber recipe cache; disabling only this optimization path for the current class layout",
                        exception);
            }
        }
    }
}
