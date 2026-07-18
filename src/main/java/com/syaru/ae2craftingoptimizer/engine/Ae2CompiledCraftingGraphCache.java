package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.storage.AEKeyFilter;
import com.syaru.ae2craftingoptimizer.optimization.ProviderPatternGenerationTracker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class Ae2CompiledCraftingGraphCache {
    private static final Map<ICraftingService, Map<ResourceKey<Level>, Snapshot>> CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final AEKeyFilter ALL_KEYS = key -> true;

    private Ae2CompiledCraftingGraphCache() {
    }

    public static Snapshot getOrCompile(IGrid grid, Level level) {
        ICraftingService service = grid.getCraftingService();
        for (int attempt = 0; attempt < 3; attempt++) {
            long generation = ProviderPatternGenerationTracker.generation();
            synchronized (CACHE) {
                Map<ResourceKey<Level>, Snapshot> byDimension = CACHE.get(service);
                Snapshot current = byDimension == null ? null : byDimension.get(level.dimension());
                if (current != null && current.graph().generation() == generation) {
                    return current;
                }
            }

            Snapshot rebuilt = compile(service, level, generation);
            if (ProviderPatternGenerationTracker.generation() != generation) {
                continue;
            }
            synchronized (CACHE) {
                Map<ResourceKey<Level>, Snapshot> byDimension =
                        CACHE.computeIfAbsent(service, ignored -> new LinkedHashMap<>());
                Snapshot raced = byDimension.get(level.dimension());
                if (raced != null && raced.graph().generation() == generation) {
                    return raced;
                }
                byDimension.put(level.dimension(), rebuilt);
                return rebuilt;
            }
        }
        throw new StalePlanningSnapshotException(
                new PlanningGenerationSnapshot(
                        ProviderPatternGenerationTracker.generation(),
                        0L,
                        RecipeGenerationTracker.generation()),
                0);
    }

    public static void clear() {
        synchronized (CACHE) {
            CACHE.clear();
        }
        SymbolicCraftingPlanner.clearTopologyCache();
    }

    private static Snapshot compile(ICraftingService service, Level level, long generation) {
        Set<AEKey> craftables = Set.copyOf(service.getCraftables(ALL_KEYS));
        Set<IPatternDetails> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        IdentityHashMap<IPatternDetails, String> idByPattern = new IdentityHashMap<>();
        Map<String, CompiledPattern<AEKey>> compiledById = new LinkedHashMap<>();
        for (AEKey key : craftables) {
            for (IPatternDetails details : service.getCraftingFor(key)) {
                if (!seen.add(details)) {
                    continue;
                }
                String id = Ae2CompiledPatternFactory.fingerprint(details);
                idByPattern.put(details, id);
                if (!compiledById.containsKey(id)) {
                    CompiledPattern<AEKey> pattern = Ae2CompiledPatternFactory.compile(details, id, level);
                    if (pattern != null) {
                        compiledById.put(id, pattern);
                        if (compiledById.size() > 1_048_576) {
                            throw new IllegalStateException("compiled crafting graph exceeds its hard pattern bound");
                        }
                    }
                }
            }
        }
        return new Snapshot(
                CompiledCraftingGraph.compile(generation, compiledById.values()),
                idByPattern,
                craftables);
    }

    public static final class Snapshot {
        private final CompiledCraftingGraph<AEKey> graph;
        private final IdentityHashMap<IPatternDetails, String> idByPattern;
        private final Set<AEKey> craftables;

        private Snapshot(
                CompiledCraftingGraph<AEKey> graph,
                IdentityHashMap<IPatternDetails, String> idByPattern,
                Set<AEKey> craftables) {
            this.graph = graph;
            this.idByPattern = new IdentityHashMap<>(idByPattern);
            this.craftables = Set.copyOf(craftables);
        }

        public CompiledCraftingGraph<AEKey> graph() {
            return graph;
        }

        public String id(IPatternDetails pattern) {
            return idByPattern.get(pattern);
        }

        public Set<AEKey> craftables() {
            return craftables;
        }
    }
}
