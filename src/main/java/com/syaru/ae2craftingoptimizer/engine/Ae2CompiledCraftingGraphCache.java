package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.storage.AEKeyFilter;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.integration.AppliedECompatibility;
import com.syaru.ae2craftingoptimizer.optimization.OptimizationMetrics;
import com.syaru.ae2craftingoptimizer.optimization.ProviderPatternGenerationTracker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class Ae2CompiledCraftingGraphCache {
    /** 一世代で保持するルート別Program数の上限。異常な連続要求でも無制限に増やさない。 */
    private static final int MAXIMUM_ROOT_PROGRAMS_PER_SNAPSHOT = 262_144;
    private static final Map<ICraftingService, Map<ResourceKey<Level>, Snapshot>> CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final AEKeyFilter ALL_KEYS = key -> true;

    private Ae2CompiledCraftingGraphCache() {
    }

    /**
     * 現在世代のSnapshotが既に存在する場合だけ返す。
     * CraftingCalculation生成側ではコンパイルを開始せず、warm cacheの証明だけを参照する。
     */
    static Optional<Snapshot> currentSnapshot(
            IGrid grid,
            Level level,
            long generation,
            long recipeGeneration) {
        ICraftingService service = grid.getCraftingService();
        synchronized (CACHE) {
            Map<ResourceKey<Level>, Snapshot> byDimension = CACHE.get(service);
            Snapshot current = byDimension == null ? null : byDimension.get(level.dimension());
            // Snapshotが無い、または要求世代と一致しない場合はcold pathへ戻す。
            if (current == null
                    || current.graph().generation() != generation
                    || current.recipeGeneration() != recipeGeneration) {
                return Optional.empty();
            }
            return Optional.of(current);
        }
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

    /**
     * 不完全なSnapshotを同じ注文中に一度だけ破棄し、現在世代から再構築する。
     * 恒久的にコンパイルできないPatternがあっても、注文ごとの再構築ループは作らない。
     */
    public static Snapshot recompile(IGrid grid, Level level, Snapshot stale) {
        // 同じSnapshotに対する再構築権は一度だけ与える。
        if (!stale.claimRetryRebuild()) {
            return stale;
        }
        ICraftingService service = grid.getCraftingService();
        synchronized (CACHE) {
            Map<ResourceKey<Level>, Snapshot> byDimension = CACHE.get(service);
            Snapshot current = byDimension == null ? null : byDimension.get(level.dimension());
            // 別スレッドが新しいSnapshotへ交換済みなら、その結果を破棄しない。
            if (current == stale && byDimension != null) {
                byDimension.remove(level.dimension());
            }
        }
        Snapshot rebuilt = getOrCompile(grid, level);
        // 再構築後も不完全なら、世代変更まで追加の再構築を禁止する。
        rebuilt.claimRetryRebuild();
        if (ACOConfig.logCraftingDecisionFlow()) {
            AE2CraftingOptimizer.LOGGER.debug(
                    "ACO-DIAG event=graph_recompiled dimension={} oldPatternGeneration={} "
                            + "oldRecipeGeneration={} newPatternGeneration={} newRecipeGeneration={}",
                    level.dimension().location(),
                    stale.graph().generation(),
                    stale.recipeGeneration(),
                    rebuilt.graph().generation(),
                    rebuilt.recipeGeneration());
        }
        return rebuilt;
    }

    public static void clear() {
        synchronized (CACHE) {
            CACHE.clear();
        }
        SymbolicCraftingPlanner.clearTopologyCache();
        CompiledRootQualificationRegistry.clear();
    }

    private static Snapshot compile(
            ICraftingService service,
            Level level,
            long generation,
            long recipeGeneration) {
        long startedNanos = System.nanoTime();
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
                if (AppliedECompatibility.requiresAe2Planner(details)) {
                    // AppliedEはAE2 CraftingTreeNode内で注文量専用の一時Patternへ置き換える。
                    // 固定レシピとしてコンパイルすると、その生成・削除処理を迂回してしまう。
                    incompletelyCompiledOutputs.add(key);
                    OptimizationMetrics.recordAppliedEPatternFallback();
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
        Snapshot snapshot = new Snapshot(
                service,
                CompiledCraftingGraph.compile(generation, compiledById.values()),
                idByPattern,
                patternById,
                compiledById,
                registeredPatternsByOutput,
                incompletelyCompiledOutputs,
                craftables,
                recipeGeneration);
        if (ACOConfig.logCraftingDecisionFlow()) {
            AE2CraftingOptimizer.LOGGER.debug(
                    "ACO-DIAG event=graph_compiled dimension={} patternGeneration={} recipeGeneration={} "
                            + "craftableKeys={} registeredPatterns={} compiledPatterns={} incompleteOutputs={} elapsedMs={}",
                    level.dimension().location(),
                    generation,
                    recipeGeneration,
                    craftables.size(),
                    seen.size(),
                    compiledById.size(),
                    incompletelyCompiledOutputs.size(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos));
        }
        return snapshot;
    }

    public static final class Snapshot {
        private final ICraftingService service;
        private final CompiledCraftingGraph<AEKey> graph;
        private final IdentityHashMap<IPatternDetails, String> idByPattern;
        private final Map<String, IPatternDetails> patternById;
        private final Map<String, CompiledPattern<AEKey>> compiledById;
        private final Map<AEKey, Integer> registeredPatternsByOutput;
        private final Set<AEKey> incompletelyCompiledOutputs;
        private final Set<AEKey> craftables;
        private final long recipeGeneration;
        private final Map<AEKey, CompiledRootProgram.Outcome<AEKey>> rootPrograms =
                new LinkedHashMap<>();
        private final Map<AEKey, Optional<Ae2StrictCraftingTopology>> strictTopologies =
                new LinkedHashMap<>();
        private final AtomicBoolean retryRebuildClaimed = new AtomicBoolean();

        private Snapshot(
                ICraftingService service,
                CompiledCraftingGraph<AEKey> graph,
                IdentityHashMap<IPatternDetails, String> idByPattern,
                Map<String, IPatternDetails> patternById,
                Map<String, CompiledPattern<AEKey>> compiledById,
                Map<AEKey, Integer> registeredPatternsByOutput,
                Set<AEKey> incompletelyCompiledOutputs,
                Set<AEKey> craftables,
                long recipeGeneration) {
            this.service = service;
            this.graph = graph;
            this.idByPattern = new IdentityHashMap<>(idByPattern);
            this.patternById = Map.copyOf(patternById);
            this.compiledById = Map.copyOf(compiledById);
            this.registeredPatternsByOutput = Map.copyOf(registeredPatternsByOutput);
            this.incompletelyCompiledOutputs = Set.copyOf(incompletelyCompiledOutputs);
            this.craftables = Set.copyOf(craftables);
            this.recipeGeneration = recipeGeneration;
        }

        public CompiledCraftingGraph<AEKey> graph() {
            return graph;
        }

        /** このSnapshotに対する一度限りの再構築権を取得する。 */
        private boolean claimRetryRebuild() {
            return retryRebuildClaimed.compareAndSet(false, true);
        }

        public String id(IPatternDetails pattern) {
            return idByPattern.get(pattern);
        }

        public IPatternDetails pattern(String id) {
            return patternById.get(id);
        }

        /** 実行中Taskが選択済みのPattern IDから、世代内で固定した入出力式を直接返す。 */
        public CompiledPattern<AEKey> compiledPattern(String id) {
            return compiledById.get(id);
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

        /**
         * 同じProvider/recipe世代ではルートごとの数式Programを再利用する。
         * 世代変更時はSnapshotごと破棄されるため、古いPattern参照は残らない。
         */
        public Optional<CompiledRootProgram<AEKey>> rootProgram(AEKey root) {
            return rootProgramOutcome(root).program();
        }

        /** Root Programと、作れなかった場合の正確な理由を同じ世代でキャッシュする。 */
        public CompiledRootProgram.Outcome<AEKey> rootProgramOutcome(AEKey root) {
            synchronized (rootPrograms) {
                CompiledRootProgram.Outcome<AEKey> cached = rootPrograms.get(root);
                // 既に成功またはFallbackが確定したルートは、同じ世代中に再探索しない。
                if (cached != null) {
                    return cached;
                }
            }

            CompiledRootProgram.Outcome<AEKey> compiled = CompiledRootProgram.compile(
                    graph,
                    root,
                    service::canEmitFor);
            if (compiled.program().isPresent()
                    && touchesIncompletePattern(compiled.program().get())) {
                // 未コンパイルPatternを終端素材と誤認したShadow計算も作らず、直ちにAE2へ戻す。
                compiled = compiled.withFailure(RootProgramFailure.INCOMPLETE_PATTERN_SNAPSHOT);
            } else if (compiled.program().isPresent()
                    && graph.patternsFor(root).isEmpty()
                    && registeredPatternsByOutput.getOrDefault(root, 0) == 0
                    && !service.getCraftingFor(root).isEmpty()) {
                /*
                 * AE2にはPatternがあるのにGraphへ入っていない場合、終端Programとして
                 * 誤採用せず、Snapshotの取り遅れとして一度だけ再構築させる。
                 */
                compiled = CompiledRootProgram.Outcome.failed(
                        RootProgramFailure.MISSING_FROM_SNAPSHOT);
            }
            synchronized (rootPrograms) {
                CompiledRootProgram.Outcome<AEKey> raced = rootPrograms.get(root);
                // 別計算スレッドが先に登録した場合は、その同一世代Programを採用する。
                if (raced != null) {
                    return raced;
                }
                // 固定上限へ達した場合は古いルートを一括破棄し、無制限な常駐を防ぐ。
                if (rootPrograms.size() >= MAXIMUM_ROOT_PROGRAMS_PER_SNAPSHOT) {
                    rootPrograms.clear();
                    strictTopologies.clear();
                }
                rootPrograms.put(root, compiled);
                return compiled;
            }
        }

        /** Root Programを新規コンパイルせず、既存の世代内結果だけを返す。 */
        Optional<CompiledRootProgram.Outcome<AEKey>> cachedRootProgramOutcome(AEKey root) {
            synchronized (rootPrograms) {
                return Optional.ofNullable(rootPrograms.get(root));
            }
        }

        private boolean touchesIncompletePattern(CompiledRootProgram<AEKey> program) {
            // Rootから到達する全キーを一巡し、除外済みPattern出力への依存を検出する。
            for (int node = 0; node < program.nodeCount(); node++) {
                if (incompletelyCompiledOutputs.contains(program.keyAt(node))) {
                    return true;
                }
            }
            return false;
        }

        /** Pattern APIの静的証明も同じ世代中は再利用し、注文ごとは在庫候補だけを再検証する。 */
        Optional<Ae2StrictCraftingTopology> strictTopology(
                Level level,
                IGrid grid,
                CompiledRootProgram<AEKey> program) {
            AEKey root = program.root();
            synchronized (rootPrograms) {
                Optional<Ae2StrictCraftingTopology> cached = strictTopologies.get(root);
                // 同じ世代で静的証明済みまたは証明不能なルートは再びPattern APIを走査しない。
                if (cached != null) {
                    return cached;
                }
            }
            Optional<Ae2StrictCraftingTopology> compiled = Optional.ofNullable(
                    Ae2StrictCraftingTopology.compile(level, grid, this, program));
            synchronized (rootPrograms) {
                Optional<Ae2StrictCraftingTopology> raced = strictTopologies.get(root);
                // 別計算スレッドが先に証明を登録した場合は、その結果を使う。
                if (raced != null) {
                    return raced;
                }
                strictTopologies.put(root, compiled);
                return compiled;
            }
        }

        /** 厳密Topologyを新規検証せず、証明済みの世代内結果だけを返す。 */
        Optional<Ae2StrictCraftingTopology> cachedStrictTopology(AEKey root) {
            synchronized (rootPrograms) {
                Optional<Ae2StrictCraftingTopology> cached = strictTopologies.get(root);
                // 未検証または検証不能のRootは、軽量Captureから安全証明として利用しない。
                if (cached == null || cached.isEmpty()) {
                    return Optional.empty();
                }
                return cached;
            }
        }
    }
}
