package com.syaru.ae2craftingoptimizer.intent;

import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
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
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Map<IntentLocation, ArrayDeque<StoredIntent>> INTENTS = new LinkedHashMap<>();
    private static final Map<SpatialBucket, Set<IntentLocation>> SPATIAL_INDEX = new LinkedHashMap<>();
    private static int entryCount;
    private static long nextExpirationTick = Long.MAX_VALUE;

    private RecipeIntentRegistry() {
    }

    public static synchronized void record(RecipeIntent intent) {
        recordCaptured(intent.location(), intent.providerPos(), intent.providerSide(), intent.patternDefinitionId(),
                intent.inputs(), intent.concreteInputs(), intent.outputs(), intent.patternExecutions(),
                intent.createdTick(), intent.expiresTick());
    }

    static synchronized void recordCaptured(
            IntentLocation location, BlockPos providerPos, Direction providerSide, String definitionId,
            List<InputIntent> inputs, List<StackIntent> concreteInputs, List<StackIntent> outputs,
            long executions, long now, long expires) {
        cleanupExpired(now);
        ArrayDeque<StoredIntent> entries = INTENTS.get(location);
        if (entries == null) {
            entries = new ArrayDeque<>();
            INTENTS.put(location, entries);
            addSpatialLocation(location);
        }

        executions = Math.max(1L, executions);
        StoredIntent latest = entries.peekLast();
        if (latest != null && canCoalesce(latest.snapshot, providerPos, providerSide, definitionId,
                inputs, concreteInputs, outputs, now)) {
            latest.add(executions, expires);
        } else {
            entries.addLast(new StoredIntent(new RecipeIntent(location.dimension(), providerPos, providerSide,
                    location.targetPos(), location.targetSide(), definitionId, inputs, concreteInputs, outputs,
                    executions, now, expires)));
            entryCount++;
        }
        nextExpirationTick = Math.min(nextExpirationTick, expires);
        enforceMaximumEntries();
        if (ACOConfig.logCapturedRecipeIntents()) {
            AE2CraftingOptimizer.LOGGER.info(
                    "Captured ACO recipe intent: pattern={}, executions={}, target={} {} {}, outputs={}",
                    definitionId, executions, location.dimension(), location.targetPos(), location.targetSide(),
                    outputs);
        }
    }

    public static synchronized List<RecipeIntent> find(
            ResourceLocation dimension,
            BlockPos targetPos,
            Direction targetSide,
            long now) {
        cleanupExpired(now);
        var entries = INTENTS.get(new IntentLocation(dimension, targetPos, targetSide));
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        if (entries.size() == 1) {
            return List.of(entries.getFirst().snapshot());
        }
        List<RecipeIntent> matches = new ArrayList<>(entries.size());
        appendSnapshots(entries, matches);
        return List.copyOf(matches);
    }

    public static synchronized List<RecipeIntent> findForTarget(
            ResourceLocation dimension,
            BlockPos targetPos,
            long now) {
        cleanupExpired(now);
        if (INTENTS.isEmpty()) {
            return List.of();
        }
        Set<IntentLocation> chunkLocations = SPATIAL_INDEX.get(
                new SpatialBucket(dimension, targetPos.getX() >> 4, targetPos.getZ() >> 4));
        if (chunkLocations == null || chunkLocations.isEmpty()) {
            return List.of();
        }
        List<RecipeIntent> matches = null;
        // #179: exact target lookup must not walk every machine's hints.
        for (Direction side : DIRECTIONS) {
            var entries = INTENTS.get(new IntentLocation(dimension, targetPos, side));
            if (entries != null && !entries.isEmpty()) {
                if (matches == null) {
                    matches = new ArrayList<>(entries.size());
                }
                appendSnapshots(entries, matches);
            }
        }
        return matches == null ? List.of() : matches;
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
                    var entries = INTENTS.get(location);
                    if (entries == null) {
                        continue;
                    }
                    for (StoredIntent intent : entries) {
                        nearby.add(new NearbyIntent(distanceSquared, intent));
                    }
                }
            }
        }

        nearby.sort(Comparator
                .comparingLong(NearbyIntent::distanceSquared)
                .thenComparing(Comparator.comparingLong(
                        (NearbyIntent entry) -> entry.intent().snapshot.createdTick()).reversed()));
        int resultSize = Math.min(limit, nearby.size());
        List<RecipeIntent> result = new ArrayList<>(resultSize);
        for (int index = 0; index < resultSize; index++) {
            result.add(nearby.get(index).intent().snapshot());
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
        for (var entries : INTENTS.values()) {
            appendSnapshots(entries, snapshot);
        }
        return snapshot;
    }

    public static synchronized int size() {
        return entryCount;
    }

    public static synchronized void cleanupExpired(long now) {
        // #161: native pushes must not rescan every provider before any hint can expire.
        if (INTENTS.isEmpty() || now < nextExpirationTick) {
            return;
        }
        int removed = 0;
        long nextExpiration = Long.MAX_VALUE;
        var mapIterator = INTENTS.entrySet().iterator();
        while (mapIterator.hasNext()) {
            var mapEntry = mapIterator.next();
            var entries = mapEntry.getValue();
            // A changed TTL may expire a later entry before the queue head.
            int previousSize = entries.size();
            entries.removeIf(intent -> now >= intent.expires);
            removed += previousSize - entries.size();
            for (StoredIntent intent : entries) {
                nextExpiration = Math.min(nextExpiration, intent.expires);
            }
            if (entries.isEmpty()) {
                mapIterator.remove();
                removeSpatialLocation(mapEntry.getKey());
            }
        }
        nextExpirationTick = nextExpiration;
        if (removed > 0) {
            entryCount -= removed;
            if (ACOConfig.logRecipeIntentRegistryEvictions()) {
                AE2CraftingOptimizer.LOGGER.info("Expired {} ACO recipe intent entries", removed);
            }
        }
    }

    public static synchronized void clear(String reason) {
        nextExpirationTick = Long.MAX_VALUE;
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

    private static boolean canCoalesce(RecipeIntent left, BlockPos providerPos, Direction providerSide,
            String definitionId, List<InputIntent> inputs, List<StackIntent> concreteInputs,
            List<StackIntent> outputs, long now) {
        return left.createdTick() == now
                && left.providerPos().equals(providerPos)
                && left.providerSide() == providerSide
                && left.patternDefinitionId().equals(definitionId)
                && (left.inputs() == inputs || left.inputs().equals(inputs))
                && sameStacks(left.concreteInputs(), concreteInputs)
                && sameStacks(left.outputs(), outputs);
    }

    private static boolean sameStacks(List<StackIntent> left, List<StackIntent> right) {
        if (left == right) return true;
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            if (!left.get(i).equals(right.get(i))) return false;
        }
        return true;
    }

    private static void appendSnapshots(ArrayDeque<StoredIntent> entries, List<RecipeIntent> result) {
        for (StoredIntent entry : entries) {
            result.add(entry.snapshot());
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
            var iterator = INTENTS.entrySet().iterator();
            var eldest = iterator.next();
            var entries = eldest.getValue();
            if (!entries.isEmpty()) {
                entries.removeFirst();
                entryCount--;
            }
            if (entries.isEmpty()) {
                iterator.remove();
                removeSpatialLocation(eldest.getKey());
            } else {
                IntentLocation key = eldest.getKey();
                iterator.remove();
                INTENTS.put(key, entries);
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

    // Same-tick pushes update counts in place; readers still receive immutable snapshots.
    private static final class StoredIntent {
        private RecipeIntent snapshot;
        private long executions;
        private long expires;

        private StoredIntent(RecipeIntent snapshot) {
            this.snapshot = snapshot;
            this.executions = snapshot.patternExecutions();
            this.expires = snapshot.expiresTick();
        }

        private void add(long count, long expiration) {
            executions += Math.min(count, Long.MAX_VALUE - executions);
            expires = Math.max(expires, expiration);
        }

        private RecipeIntent snapshot() {
            if (snapshot.patternExecutions() != executions || snapshot.expiresTick() != expires) {
                snapshot = snapshot.withPatternExecutions(executions, expires);
            }
            return snapshot;
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

    private record NearbyIntent(long distanceSquared, StoredIntent intent) {
    }
}
