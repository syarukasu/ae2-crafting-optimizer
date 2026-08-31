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
import com.syaru.ae2craftingoptimizer.optimization.PlanningConfigurationRevisionTracker;
import com.syaru.ae2craftingoptimizer.optimization.ProviderPatternGenerationTracker;
import com.syaru.ae2craftingoptimizer.optimization.WeightedLruMap;
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
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class Ae2CompiledCraftingGraphCache {
    /** 連続した世代変更で部分Graphを公開しないための、有界な再取得回数。 */
    private static final int MAXIMUM_STALE_REBUILD_ATTEMPTS = 3;
    /** 一つの実行Snapshotへ保持できるCompiled Pattern数の固定上限。 */
    private static final int MAXIMUM_COMPILED_PATTERNS = 1_048_576;
    /** 一世代で保持するルート別Program数。通常の端末利用を覆いつつ、長期常駐量を固定する。 */
    private static final int MAXIMUM_ROOT_PROGRAMS_PER_SNAPSHOT = 256;
    /** 一世代のRoot Program配列へ保持する合計ノード数。約100万ノードで常駐量を制限する。 */
    private static final int MAXIMUM_ROOT_PROGRAM_NODES_PER_SNAPSHOT = 1_048_576;
    private static final Map<ICraftingService, Map<ResourceKey<Level>, Snapshot>> CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final AEKeyFilter ALL_KEYS = key -> true;

    private Ae2CompiledCraftingGraphCache() {
    }

    public static Snapshot getOrCompile(IGrid grid, Level level) {
        ICraftingService service = grid.getCraftingService();
        // 世代が静止した一回だけを公開し、上限後は古いGraphへfallbackせず明示失敗する。
        for (int attempt = 0; attempt < MAXIMUM_STALE_REBUILD_ATTEMPTS; attempt++) {
            long generation = ProviderPatternGenerationTracker.generation();
            long recipeGeneration = RecipeGenerationTracker.generation();
            long configurationRevision = PlanningConfigurationRevisionTracker.current();
            synchronized (CACHE) {
                Map<ResourceKey<Level>, Snapshot> byDimension = CACHE.get(service);
                Snapshot current = byDimension == null ? null : byDimension.get(level.dimension());
                if (current != null
                        && current.graph().generation() == generation
                        && current.recipeGeneration() == recipeGeneration
                        && current.configurationRevision() == configurationRevision) {
                    return current;
                }
            }

            Snapshot rebuilt = compile(
                    service,
                    level,
                    generation,
                    recipeGeneration,
                    configurationRevision);
            if (ProviderPatternGenerationTracker.generation() != generation
                    || RecipeGenerationTracker.generation() != recipeGeneration
                    || !PlanningConfigurationRevisionTracker.isCurrent(
                            configurationRevision)) {
                continue;
            }
            synchronized (CACHE) {
                Map<ResourceKey<Level>, Snapshot> byDimension =
                        CACHE.computeIfAbsent(service, ignored -> new LinkedHashMap<>());
                Snapshot raced = byDimension.get(level.dimension());
                if (raced != null
                        && raced.graph().generation() == generation
                        && raced.recipeGeneration() == recipeGeneration
                        && raced.configurationRevision() == configurationRevision) {
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
        Ae2ImmutablePlanningGraphCache.clear();
        SymbolicCraftingPlanner.clearTopologyCache();
        CompiledRootQualificationRegistry.clear();
    }

    private static Snapshot compile(
            ICraftingService service,
            Level level,
            long generation,
            long recipeGeneration,
            long configurationRevision) {
        long startedNanos = System.nanoTime();
        Set<AEKey> craftables = Set.copyOf(service.getCraftables(ALL_KEYS));
        Set<IPatternDetails> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        IdentityHashMap<IPatternDetails, String> idByPattern = new IdentityHashMap<>();
        Map<String, IPatternDetails> patternById = new LinkedHashMap<>();
        Map<AEKey, Integer> registeredPatternsByOutput = new LinkedHashMap<>();
        Set<AEKey> incompletelyCompiledOutputs = new LinkedHashSet<>();
        Set<AEKey> emittableKeys = new LinkedHashSet<>();
        Set<String> exactInputDomains = new LinkedHashSet<>();
        Map<String, CompiledPattern<AEKey>> compiledById = new LinkedHashMap<>();
        for (AEKey key : craftables) {
            // Full execution snapshotでもEmitter判定を固定し、後段workerからserviceを再読込しない。
            if (service.canEmitFor(key)) {
                emittableKeys.add(key);
            }
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
                Ae2CompiledPatternFactory.Captured captured =
                        Ae2CompiledPatternFactory.capture(details, level);
                if (captured == null) {
                    incompletelyCompiledOutputs.add(key);
                    continue;
                }
                String id = captured.fingerprint();
                idByPattern.put(details, id);
                if (!compiledById.containsKey(id)) {
                    compiledById.put(id, captured.compile(id));
                    patternById.put(id, details);
                    if (captured.exactInputDomain()) {
                        exactInputDomains.add(id);
                    }
                    if (compiledById.size() > MAXIMUM_COMPILED_PATTERNS) {
                        throw new IllegalStateException("compiled crafting graph exceeds its hard pattern bound");
                    }
                }
                if (!compiledById.containsKey(id)
                        || compiledById.get(id).outputAmount(key) <= 0L) {
                    incompletelyCompiledOutputs.add(key);
                }
            }
        }
        Snapshot snapshot = new Snapshot(
                CompiledCraftingGraph.compile(generation, compiledById.values()),
                idByPattern,
                patternById,
                compiledById,
                registeredPatternsByOutput,
                incompletelyCompiledOutputs,
                craftables,
                emittableKeys,
                exactInputDomains,
                recipeGeneration,
                configurationRevision);
        if (ACOConfig.logCraftingDecisionFlow()) {
            AE2CraftingOptimizer.LOGGER.debug(
                    "ACO-DIAG event=graph_compiled dimension={} patternGeneration={} recipeGeneration={} "
                            + "configurationRevision={} "
                            + "craftableKeys={} registeredPatterns={} compiledPatterns={} incompleteOutputs={} elapsedMs={}",
                    level.dimension().location(),
                    generation,
                    recipeGeneration,
                    configurationRevision,
                    craftables.size(),
                    seen.size(),
                    compiledById.size(),
                    incompletelyCompiledOutputs.size(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos));
        }
        return snapshot;
    }

    public static final class Snapshot implements Ae2PlanningGraphSnapshot {
        private final CompiledCraftingGraph<AEKey> graph;
        private final IdentityHashMap<IPatternDetails, String> idByPattern;
        private final Map<String, IPatternDetails> patternById;
        private final Map<String, CompiledPattern<AEKey>> compiledById;
        private final Map<AEKey, Integer> registeredPatternsByOutput;
        private final Set<AEKey> incompletelyCompiledOutputs;
        private final Set<AEKey> craftables;
        private final Set<AEKey> emittableKeys;
        private final Set<String> exactInputDomains;
        private final long recipeGeneration;
        private final long configurationRevision;
        private final WeightedLruMap<AEKey, CompiledRootProgram.Outcome<AEKey>> rootPrograms =
                new WeightedLruMap<>(
                        MAXIMUM_ROOT_PROGRAMS_PER_SNAPSHOT,
                        MAXIMUM_ROOT_PROGRAM_NODES_PER_SNAPSHOT,
                        Snapshot::rootProgramWeight);
        private final Map<AEKey, Optional<Ae2StrictCraftingTopology>> strictTopologies =
                new LinkedHashMap<>();

        private Snapshot(
                CompiledCraftingGraph<AEKey> graph,
                IdentityHashMap<IPatternDetails, String> idByPattern,
                Map<String, IPatternDetails> patternById,
                Map<String, CompiledPattern<AEKey>> compiledById,
                Map<AEKey, Integer> registeredPatternsByOutput,
                Set<AEKey> incompletelyCompiledOutputs,
                Set<AEKey> craftables,
                Set<AEKey> emittableKeys,
                Set<String> exactInputDomains,
                long recipeGeneration,
                long configurationRevision) {
            this.graph = graph;
            this.idByPattern = new IdentityHashMap<>(idByPattern);
            this.patternById = Map.copyOf(patternById);
            this.compiledById = Map.copyOf(compiledById);
            this.registeredPatternsByOutput = Map.copyOf(registeredPatternsByOutput);
            this.incompletelyCompiledOutputs = Set.copyOf(incompletelyCompiledOutputs);
            this.craftables = Set.copyOf(craftables);
            this.emittableKeys = Set.copyOf(emittableKeys);
            this.exactInputDomains = Set.copyOf(exactInputDomains);
            this.recipeGeneration = recipeGeneration;
            this.configurationRevision = configurationRevision;
        }

        @Override
        public CompiledCraftingGraph<AEKey> graph() {
            return graph;
        }

        @Override
        public String id(IPatternDetails pattern) {
            return idByPattern.get(pattern);
        }

        @Override
        public IPatternDetails pattern(String id) {
            return patternById.get(id);
        }

        /** 実行中Taskが選択済みのPattern IDから、世代内で固定した入出力式を直接返す。 */
        public CompiledPattern<AEKey> compiledPattern(String id) {
            return compiledById.get(id);
        }

        /** 標準AE2側にも高速Graph側にも、その出力のPatternが一つだけ存在することを証明する。 */
        @Override
        public boolean hasExactlyOneFullyCompiledPattern(AEKey output) {
            return registeredPatternsByOutput.getOrDefault(output, 0) == 1
                    && !incompletelyCompiledOutputs.contains(output)
                    && graph.patternsFor(output).size() == 1;
        }

        @Override
        public int registeredPatternCount(AEKey output) {
            return registeredPatternsByOutput.getOrDefault(output, 0);
        }

        @Override
        public boolean isIncompletelyCompiled(AEKey output) {
            return incompletelyCompiledOutputs.contains(output);
        }

        public Set<AEKey> craftables() {
            return craftables;
        }

        @Override
        public long recipeGeneration() {
            return recipeGeneration;
        }

        public long configurationRevision() {
            return configurationRevision;
        }

        @Override
        public boolean isEmittable(AEKey key) {
            return emittableKeys.contains(key);
        }

        @Override
        public boolean hasExactInputDomain(String patternId) {
            return exactInputDomains.contains(patternId);
        }

        /**
         * 同じProvider/recipe世代ではルートごとの数式Programを再利用する。
         * 世代変更時はSnapshotごと破棄されるため、古いPattern参照は残らない。
         */
        public Optional<CompiledRootProgram<AEKey>> rootProgram(AEKey root) {
            return rootProgramOutcome(root).program();
        }

        /** Root Programと、作れなかった場合の正確な理由を同じ世代でキャッシュする。 */
        @Override
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
                    emittableKeys::contains);
            if (compiled.program().isPresent()
                    && touchesIncompletePattern(compiled.program().get())) {
                // 未コンパイルPatternを終端素材と誤認したShadow計算も作らず、直ちにAE2へ戻す。
                compiled = compiled.withFailure(RootProgramFailure.INCOMPLETE_PATTERN_SNAPSHOT);
            }
            synchronized (rootPrograms) {
                CompiledRootProgram.Outcome<AEKey> raced = rootPrograms.get(root);
                // 別計算スレッドが先に登録した場合は、その同一世代Programを採用する。
                if (raced != null) {
                    return raced;
                }
                // LRU退避と同時に対応Topologyも除き、異なるProgramの証明を再利用しない。
                return rootPrograms.putIfAbsent(
                        root,
                        compiled,
                        strictTopologies::remove);
            }
        }

        /** Root Programを新規コンパイルせず、既存の世代内結果だけを返す。 */
        @Override
        public Optional<CompiledRootProgram.Outcome<AEKey>> cachedRootProgramOutcome(AEKey root) {
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

        private static int rootProgramWeight(CompiledRootProgram.Outcome<AEKey> outcome) {
            // 失敗結果も一件分として数え、成功Programは保持するprimitive配列のノード数で量る。
            return outcome.program()
                    .map(program -> Math.max(1, program.nodeCount()))
                    .orElse(1);
        }

        /** Pattern APIの静的証明も同じ世代中は再利用し、注文ごとは在庫候補だけを再検証する。 */
        Optional<Ae2StrictCraftingTopology> strictTopology(
                Level level,
                IGrid grid,
                CompiledRootProgram<AEKey> program) {
            return strictTopology(program);
        }

        @Override
        public Optional<Ae2StrictCraftingTopology> strictTopology(
                CompiledRootProgram<AEKey> program) {
            AEKey root = program.root();
            boolean cacheable;
            synchronized (rootPrograms) {
                CompiledRootProgram.Outcome<AEKey> current = rootPrograms.get(root);
                cacheable = current != null
                        && current.program().orElse(null) == program;
                // 保持中Programだけは、同じ世代の静的証明または証明不能結果を再利用する。
                if (cacheable) {
                    Optional<Ae2StrictCraftingTopology> cached = strictTopologies.get(root);
                    if (cached != null) {
                        return cached;
                    }
                }
            }
            Optional<Ae2StrictCraftingTopology> compiled = Optional.ofNullable(
                    Ae2StrictCraftingTopology.compile(this, program));
            // 大きすぎて非保持、または既に退避されたProgramのTopologyは常駐させない。
            if (!cacheable) {
                return compiled;
            }
            synchronized (rootPrograms) {
                CompiledRootProgram.Outcome<AEKey> current = rootPrograms.get(root);
                // 検証中にLRU退避された場合は結果を返すだけにし、孤立したTopologyを保存しない。
                if (current == null
                        || current.program().orElse(null) != program) {
                    return compiled;
                }
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
        @Override
        public Optional<Ae2StrictCraftingTopology> cachedStrictTopology(AEKey root) {
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
