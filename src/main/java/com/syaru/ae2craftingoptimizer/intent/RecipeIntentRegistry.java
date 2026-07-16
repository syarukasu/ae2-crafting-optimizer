package com.syaru.ae2craftingoptimizer.intent;

import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

public final class RecipeIntentRegistry {
    private static final Map<IntentLocation, ArrayDeque<RecipeIntent>> INTENTS = new LinkedHashMap<>();
    private static final Map<SpatialBucket, Set<IntentLocation>> SPATIAL_INDEX = new LinkedHashMap<>();
    private static int entryCount;

    private RecipeIntentRegistry() {
    }

    public static synchronized void record(RecipeIntent intent) {
        cleanupExpired(intent.createdTick());
        IntentLocation location = intent.location();
        ArrayDeque<RecipeIntent> entries = INTENTS.get(location);
        if (entries == null) {
            entries = new ArrayDeque<>();
            INTENTS.put(location, entries);
            addSpatialLocation(location);
        }

        RecipeIntent latest = entries.peekLast();
        if (latest != null && canCoalesce(latest, intent)) {
            entries.removeLast();
            entries.addLast(latest.withPatternExecutions(
                    saturatingAdd(latest.patternExecutions(), intent.patternExecutions()),
                    intent.expiresTick()));
        } else {
            entries.addLast(intent);
            entryCount++;
        }
        enforceMaximumEntries();
        if (ACOConfig.logCapturedRecipeIntents()) {
            AE2CraftingOptimizer.LOGGER.info(
                    "Captured ACO recipe intent: pattern={}, executions={}, target={} {} {}, outputs={}",
                    intent.patternDefinitionId(),
                    intent.patternExecutions(),
                    intent.dimension(),
                    intent.targetPos(),
                    intent.targetSide(),
                    intent.outputs());
        }
    }

    public static synchronized List<RecipeIntent> find(
            ResourceLocation dimension,
            BlockPos targetPos,
            Direction targetSide,
            long now) {
        cleanupExpired(now);
        ArrayDeque<RecipeIntent> entries = INTENTS.get(new IntentLocation(dimension, targetPos, targetSide));
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        return List.copyOf(entries);
    }

    public static synchronized List<RecipeIntent> findForTarget(
            ResourceLocation dimension,
            BlockPos targetPos,
            long now) {
        cleanupExpired(now);
        if (INTENTS.isEmpty()) {
            return List.of();
        }
        List<RecipeIntent> matches = new ArrayList<>();
        for (Map.Entry<IntentLocation, ArrayDeque<RecipeIntent>> entry : INTENTS.entrySet()) {
            IntentLocation location = entry.getKey();
            if (location.dimension().equals(dimension) && location.targetPos().equals(targetPos)) {
                matches.addAll(entry.getValue());
            }
        }
        return matches;
    }

    public static synchronized List<RecipeIntent> findNearby(
            ResourceLocation dimension,
            BlockPos targetPos,
            int radius,
            long now,
            int limit) {
        cleanupExpired(now);
        if (INTENTS.isEmpty() || radius < 0 || limit <= 0) {
            return List.of();
        }

        int minimumChunkX = (targetPos.getX() - radius) >> 4;
        int maximumChunkX = (targetPos.getX() + radius) >> 4;
        int minimumChunkZ = (targetPos.getZ() - radius) >> 4;
        int maximumChunkZ = (targetPos.getZ() + radius) >> 4;
        long radiusSquared = (long) radius * radius;
        List<NearbyIntent> nearby = new ArrayList<>();

        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                Set<IntentLocation> locations = SPATIAL_INDEX.get(new SpatialBucket(dimension, chunkX, chunkZ));
                if (locations == null) {
                    continue;
                }
                for (IntentLocation location : locations) {
                    long distanceSquared = distanceSquared(targetPos, location.targetPos());
                    if (distanceSquared > radiusSquared) {
                        continue;
                    }
                    ArrayDeque<RecipeIntent> entries = INTENTS.get(location);
                    if (entries == null) {
                        continue;
                    }
                    for (RecipeIntent intent : entries) {
                        nearby.add(new NearbyIntent(distanceSquared, intent));
                    }
                }
            }
        }

        nearby.sort(Comparator
                .comparingLong(NearbyIntent::distanceSquared)
                .thenComparing(Comparator.comparingLong(
                        (NearbyIntent entry) -> entry.intent().createdTick()).reversed()));
        int resultSize = Math.min(limit, nearby.size());
        List<RecipeIntent> result = new ArrayList<>(resultSize);
        for (int index = 0; index < resultSize; index++) {
            result.add(nearby.get(index).intent());
        }
        return List.copyOf(result);
    }

    public static synchronized Optional<RecipeIntent> findFirst(
            ResourceLocation dimension,
            BlockPos targetPos,
            Direction targetSide,
            long now,
            Predicate<RecipeIntent> predicate) {
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
            Map.Entry<IntentLocation, ArrayDeque<RecipeIntent>> mapEntry = mapIterator.next();
            ArrayDeque<RecipeIntent> entries = mapEntry.getValue();
            while (!entries.isEmpty() && entries.peekFirst().isExpired(now)) {
                entries.removeFirst();
                removed++;
            }
            if (entries.isEmpty()) {
                mapIterator.remove();
                removeSpatialLocation(mapEntry.getKey());
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
        SPATIAL_INDEX.clear();
        entryCount = 0;
        if (ACOConfig.logRecipeIntentRegistryEvictions()) {
            AE2CraftingOptimizer.LOGGER.info("Cleared {} ACO recipe intent entries: {}", removed, reason);
        }
    }

    private static boolean canCoalesce(RecipeIntent left, RecipeIntent right) {
        return left.createdTick() == right.createdTick()
                && left.providerPos().equals(right.providerPos())
                && left.providerSide() == right.providerSide()
                && left.patternDefinitionId().equals(right.patternDefinitionId())
                && left.inputs().equals(right.inputs())
                && left.concreteInputs().equals(right.concreteInputs())
                && left.outputs().equals(right.outputs());
    }

    private static long saturatingAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static long distanceSquared(BlockPos left, BlockPos right) {
        long x = (long) left.getX() - right.getX();
        long y = (long) left.getY() - right.getY();
        long z = (long) left.getZ() - right.getZ();
        return x * x + y * y + z * z;
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
                removeSpatialLocation(eldest.getKey());
            }
        }
    }

    private static void addSpatialLocation(IntentLocation location) {
        SPATIAL_INDEX.computeIfAbsent(SpatialBucket.of(location), ignored -> new LinkedHashSet<>()).add(location);
    }

    private static void removeSpatialLocation(IntentLocation location) {
        SpatialBucket bucket = SpatialBucket.of(location);
        Set<IntentLocation> locations = SPATIAL_INDEX.get(bucket);
        if (locations == null) {
            return;
        }
        locations.remove(location);
        if (locations.isEmpty()) {
            SPATIAL_INDEX.remove(bucket);
        }
    }

    private record SpatialBucket(ResourceLocation dimension, int chunkX, int chunkZ) {
        static SpatialBucket of(IntentLocation location) {
            return new SpatialBucket(
                    location.dimension(),
                    location.targetPos().getX() >> 4,
                    location.targetPos().getZ() >> 4);
        }
    }

    private record NearbyIntent(long distanceSquared, RecipeIntent intent) {
    }
}
