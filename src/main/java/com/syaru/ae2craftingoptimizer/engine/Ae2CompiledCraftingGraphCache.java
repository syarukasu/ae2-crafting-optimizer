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
import java.util.LinkedHashSet;
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
            long recipeGeneration = RecipeGenerationTracker.generation();
            synchronized (CACHE) {
                Map<ResourceKey<Level>, Snapshot> byDimension = CACHE.get(service);
                Snapshot current = byDimension == null ? null : byDimension.get(level.dimension());
                if (current != null
                        && current.graph().generation() == generation
                        && current.recipeGeneration() == recipeGeneration) {
                    return current;
                }
            }

            Snapshot rebuilt = compile(service, level, generation, recipeGeneration);
            if (ProviderPatternGenerationTracker.generation() != generation
                    || RecipeGenerationTracker.generation() != recipeGeneration) {
                continue;
            }
            synchronized (CACHE) {
                Map<ResourceKey<Level>, Snapshot> byDimension =
                        CACHE.computeIfAbsent(service, ignored -> new LinkedHashMap<>());
                Snapshot raced = byDimension.get(level.dimension());
                if (raced != null
                        && raced.graph().generation() == generation
                        && raced.recipeGeneration() == recipeGeneration) {
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

    private static Snapshot compile(
            ICraftingService service,
            Level level,
            long generation,
            long recipeGeneration) {
        Set<AEKey> craftables = Set.copyOf(service.getCraftables(ALL_KEYS));
        Set<IPatternDetails> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        IdentityHashMap<IPatternDetails, String> idByPattern = new IdentityHashMap<>();
        Map<String, IPatternDetails> patternById = new LinkedHashMap<>();
        Map<AEKey, Integer> registeredPatternsByOutput = new LinkedHashMap<>();
        Set<AEKey> incompletelyCompiledOutputs = new LinkedHashSet<>();
        Map<String, CompiledPattern<AEKey>> compiledById = new LinkedHashMap<>();
        for (AEKey key : craftables) {
            var registered = service.getCraftingFor(key);
            registeredPatternsByOutput.put(key, registered.size());
            for (IPatternDetails details : registered) {
                if (!seen.add(details)) {
                    String existingId = idByPattern.get(details);
                    if (existingId == null || !compiledById.containsKey(existingId)) {
                        incompletelyCompiledOutputs.add(key);
                    }
                    continue;
                }
                String id = Ae2CompiledPatternFactory.fingerprint(details);
                idByPattern.put(details, id);
                if (!compiledById.containsKey(id)) {
                    CompiledPattern<AEKey> pattern = Ae2CompiledPatternFactory.compile(details, id, level);
                    if (pattern != null) {
                        compiledById.put(id, pattern);
                        patternById.put(id, details);
                        if (compiledById.size() > 1_048_576) {
                            throw new IllegalStateException("compiled crafting graph exceeds its hard pattern bound");
                        }
                    } else {
                        incompletelyCompiledOutputs.add(key);
                    }
                }
                if (!compiledById.containsKey(id)
                        || compiledById.get(id).outputAmount(key) <= 0L) {
                    incompletelyCompiledOutputs.add(key);
                }
            }
        }
        return new Snapshot(
                CompiledCraftingGraph.compile(generation, compiledById.values()),
                idByPattern,
                patternById,
                registeredPatternsByOutput,
                incompletelyCompiledOutputs,
                craftables,
                recipeGeneration);
    }

    public static final class Snapshot {
        private final CompiledCraftingGraph<AEKey> graph;
        private final IdentityHashMap<IPatternDetails, String> idByPattern;
        private final Map<String, IPatternDetails> patternById;
        private final Map<AEKey, Integer> registeredPatternsByOutput;
        private final Set<AEKey> incompletelyCompiledOutputs;
        private final Set<AEKey> craftables;
        private final long recipeGeneration;

        private Snapshot(
                CompiledCraftingGraph<AEKey> graph,
                IdentityHashMap<IPatternDetails, String> idByPattern,
                Map<String, IPatternDetails> patternById,
                Map<AEKey, Integer> registeredPatternsByOutput,
                Set<AEKey> incompletelyCompiledOutputs,
                Set<AEKey> craftables,
                long recipeGeneration) {
            this.graph = graph;
            this.idByPattern = new IdentityHashMap<>(idByPattern);
            this.patternById = Map.copyOf(patternById);
            this.registeredPatternsByOutput = Map.copyOf(registeredPatternsByOutput);
            this.incompletelyCompiledOutputs = Set.copyOf(incompletelyCompiledOutputs);
            this.craftables = Set.copyOf(craftables);
            this.recipeGeneration = recipeGeneration;
        }

        public CompiledCraftingGraph<AEKey> graph() {
            return graph;
        }

        public String id(IPatternDetails pattern) {
            return idByPattern.get(pattern);
        }

        public IPatternDetails pattern(String id) {
            return patternById.get(id);
        }

        /** 標準AE2側にも高速Graph側にも、その出力のPatternが一つだけ存在することを証明する。 */
        public boolean hasExactlyOneFullyCompiledPattern(AEKey output) {
            return registeredPatternsByOutput.getOrDefault(output, 0) == 1
                    && !incompletelyCompiledOutputs.contains(output)
                    && graph.patternsFor(output).size() == 1;
        }

        public int registeredPatternCount(AEKey output) {
            return registeredPatternsByOutput.getOrDefault(output, 0);
        }

        public boolean isIncompletelyCompiled(AEKey output) {
            return incompletelyCompiledOutputs.contains(output);
        }

        public Set<AEKey> craftables() {
            return craftables;
        }

        public long recipeGeneration() {
            return recipeGeneration;
        }
    }
}
