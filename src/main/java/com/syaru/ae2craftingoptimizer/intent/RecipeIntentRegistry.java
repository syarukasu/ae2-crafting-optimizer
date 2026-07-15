package com.syaru.ae2craftingoptimizer.intent;

import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

public final class RecipeIntentRegistry {
    private static final Map<IntentLocation, ArrayDeque<RecipeIntent>> INTENTS = new LinkedHashMap<>();
    private static int entryCount;

    private RecipeIntentRegistry() {
    }

    public static synchronized void record(RecipeIntent intent) {
        cleanupExpired(intent.createdTick());
        INTENTS.computeIfAbsent(intent.location(), ignored -> new ArrayDeque<>()).addLast(intent);
        entryCount++;
        enforceMaximumEntries();
        if (ACOConfig.logCapturedRecipeIntents()) {
            AE2CraftingOptimizer.LOGGER.info(
                    "Captured ACO recipe intent: pattern={}, target={} {} {}, outputs={}",
                    intent.patternDefinitionId(),
                    intent.dimension(),
                    intent.targetPos(),
                    intent.targetSide(),
                    intent.outputs());
        }
    }

    public static synchronized List<RecipeIntent> find(ResourceLocation dimension, BlockPos targetPos, Direction targetSide, long now) {
        cleanupExpired(now);
        ArrayDeque<RecipeIntent> entries = INTENTS.get(new IntentLocation(dimension, targetPos, targetSide));
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        return List.copyOf(entries);
    }

    public static synchronized List<RecipeIntent> findForTarget(ResourceLocation dimension, BlockPos targetPos, long now) {
        cleanupExpired(now);
        if (INTENTS.isEmpty()) {
            return List.of();
        }
        List<RecipeIntent> matches = new ArrayList<>();
        for (Map.Entry<IntentLocation, ArrayDeque<RecipeIntent>> entry : INTENTS.entrySet()) {
            IntentLocation location = entry.getKey();
            if (!location.dimension().equals(dimension) || !location.targetPos().equals(targetPos)) {
                continue;
            }
            matches.addAll(entry.getValue());
        }
        return matches;
    }

    public static synchronized Optional<RecipeIntent> findFirst(
            ResourceLocation dimension,
            BlockPos targetPos,
            Direction targetSide,
            long now,
            Predicate<RecipeIntent> predicate
    ) {
        for (RecipeIntent intent : find(dimension, targetPos, targetSide, now)) {
            if (predicate.test(intent)) {
                return Optional.of(intent);
            }
        }
        return Optional.empty();
    }

    public static synchronized List<RecipeIntent> snapshot() {
        List<RecipeIntent> snapshot = new ArrayList<>(entryCount);
        for (ArrayDeque<RecipeIntent> entries : INTENTS.values()) {
            snapshot.addAll(entries);
        }
        return snapshot;
    }

    public static synchronized int size() {
        return entryCount;
    }

    public static synchronized void cleanupExpired(long now) {
        if (INTENTS.isEmpty()) {
            return;
        }
        int removed = 0;
        Iterator<Map.Entry<IntentLocation, ArrayDeque<RecipeIntent>>> mapIterator = INTENTS.entrySet().iterator();
        while (mapIterator.hasNext()) {
            ArrayDeque<RecipeIntent> entries = mapIterator.next().getValue();
            while (!entries.isEmpty() && entries.peekFirst().isExpired(now)) {
                entries.removeFirst();
                removed++;
            }
            if (entries.isEmpty()) {
                mapIterator.remove();
            }
        }
        if (removed > 0) {
            entryCount -= removed;
            if (ACOConfig.logRecipeIntentRegistryEvictions()) {
                AE2CraftingOptimizer.LOGGER.info("Expired {} ACO recipe intent entries", removed);
            }
        }
    }

    public static synchronized void clear(String reason) {
        if (INTENTS.isEmpty()) {
            return;
        }
        int removed = entryCount;
        INTENTS.clear();
        entryCount = 0;
        if (ACOConfig.logRecipeIntentRegistryEvictions()) {
            AE2CraftingOptimizer.LOGGER.info("Cleared {} ACO recipe intent entries: {}", removed, reason);
        }
    }

    private static void enforceMaximumEntries() {
        int maximum = ACOConfig.getMaximumRecipeIntentEntries();
        while (entryCount > maximum && !INTENTS.isEmpty()) {
            Iterator<Map.Entry<IntentLocation, ArrayDeque<RecipeIntent>>> iterator = INTENTS.entrySet().iterator();
            Map.Entry<IntentLocation, ArrayDeque<RecipeIntent>> eldest = iterator.next();
            ArrayDeque<RecipeIntent> entries = eldest.getValue();
            if (!entries.isEmpty()) {
                entries.removeFirst();
                entryCount--;
            }
            if (entries.isEmpty()) {
                iterator.remove();
            }
        }
    }
}
