package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.storage.AEKeyFilter;
import com.syaru.ae2craftingoptimizer.integration.AppliedECompatibility;
import com.syaru.ae2craftingoptimizer.optimization.OptimizationMetrics;
import com.syaru.ae2craftingoptimizer.optimization.ProviderPatternGenerationTracker;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class Ae2CompiledCraftingGraphCache {
    /** 一世代で保持するルート別Program数の上限。異常な連続要求でも無制限に増やさない。 */
    private static final int MAXIMUM_ROOT_PROGRAMS_PER_SNAPSHOT = 262_144;
    /** 一つのDimensionで保持する到達範囲Snapshot数。多数の単発注文でも常駐量を固定する。 */
    private static final int MAXIMUM_REACHABLE_SNAPSHOTS_PER_DIMENSION = 4_096;
    /** 一つのRootから追跡できるPattern数。壊れたProviderによる無制限探索を拒否する。 */
    private static final int MAXIMUM_PATTERNS_PER_REACHABLE_SNAPSHOT = 1_048_576;
    private static final Map<ICraftingService, Map<ResourceKey<Level>, Snapshot>> CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ICraftingService, Map<ResourceKey<Level>, ReachableSnapshotCache>> ROOT_CACHE =
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
            AEKey root,
            long generation,
            long recipeGeneration) {
        ICraftingService service = grid.getCraftingService();
        synchronized (ROOT_CACHE) {
            Map<ResourceKey<Level>, ReachableSnapshotCache> byDimension = ROOT_CACHE.get(service);
            ReachableSnapshotCache cache = byDimension == null ? null : byDimension.get(level.dimension());
            // Root専用Snapshotが現在世代に存在する場合は、全ネットワークSnapshotより先に使う。
            if (cache != null && cache.matches(generation, recipeGeneration)) {
                Snapshot rootSnapshot = cache.get(root);
                if (rootSnapshot != null) {
                    return Optional.of(rootSnapshot);
                }
            }
        }
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

    /** Rootから到達するPatternだけを収集し、巨大ネットワーク全体の初回走査を避ける。 */
    public static Snapshot getOrCompileRoot(IGrid grid, Level level, AEKey root) {
        return getOrCompileRoot(grid, level, root, PlanningGuard.none());
    }

    /** Root到達範囲をAE2の計算時間枠へ参加させながら収集する。 */
    public static Snapshot getOrCompileRoot(
            IGrid grid,
            Level level,
            AEKey root,
            PlanningGuard workBudget) {
        Objects.requireNonNull(workBudget, "workBudget");
        ICraftingService service = grid.getCraftingService();
        // Pattern更新と競合したSnapshotは最大3回だけ作り直し、古い世代を公開しない。
        for (int attempt = 0; attempt < 3; attempt++) {
            long generation = ProviderPatternGenerationTracker.generation();
            long recipeGeneration = RecipeGenerationTracker.generation();
            Snapshot current = currentSnapshot(
                            grid,
                            level,
                            root,
                            generation,
                            recipeGeneration)
                    .orElse(null);
            // Root専用または既存の全体Snapshotが現世代なら、重複コンパイルしない。
            if (current != null) {
                return current;
            }

            Snapshot rebuilt = compileReachable(
                    service,
                    level,
                    root,
                    generation,
                    recipeGeneration,
                    workBudget);
            // コンパイル中にPatternまたはrecipeが変化したSnapshotは公開せず再試行する。
            if (ProviderPatternGenerationTracker.generation() != generation
                    || RecipeGenerationTracker.generation() != recipeGeneration) {
                continue;
            }
            synchronized (ROOT_CACHE) {
                Map<ResourceKey<Level>, ReachableSnapshotCache> byDimension =
                        ROOT_CACHE.computeIfAbsent(service, ignored -> new LinkedHashMap<>());
                ReachableSnapshotCache cache = byDimension.get(level.dimension());
                // 世代が変わったDimension cacheは丸ごと交換し、旧Pattern参照を残さない。
                if (cache == null || !cache.matches(generation, recipeGeneration)) {
                    cache = new ReachableSnapshotCache(generation, recipeGeneration);
                    byDimension.put(level.dimension(), cache);
                }
                Snapshot raced = cache.get(root);
                if (raced != null) {
                    return raced;
                }
                cache.put(root, rebuilt);
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
        return rebuilt;
    }

    /** Root専用Snapshotの不完全状態だけを一度破棄し、全ネットワークcacheを巻き込まない。 */
    public static Snapshot recompileRoot(IGrid grid, Level level, AEKey root, Snapshot stale) {
        return recompileRoot(grid, level, root, stale, PlanningGuard.none());
    }

    public static Snapshot recompileRoot(
            IGrid grid,
            Level level,
            AEKey root,
            Snapshot stale,
            PlanningGuard workBudget) {
        Objects.requireNonNull(workBudget, "workBudget");
        // Root専用Snapshotだけ再試行権を共有する。全体Snapshotの権利を一注文で消費しない。
        if (stale.isRootScopedFor(root) && !stale.claimRetryRebuild()) {
            return stale;
        }
        ICraftingService service = grid.getCraftingService();
        synchronized (ROOT_CACHE) {
            Map<ResourceKey<Level>, ReachableSnapshotCache> byDimension = ROOT_CACHE.get(service);
            ReachableSnapshotCache cache = byDimension == null ? null : byDimension.get(level.dimension());
            // 別スレッドが新しいRoot Snapshotへ交換済みなら、その結果を破棄しない。
            if (cache != null && cache.get(root) == stale) {
                cache.remove(root);
            }
        }

        // Pattern更新との競合は最大3回だけ再試行し、同じ全体Snapshotを再取得しない。
        for (int attempt = 0; attempt < 3; attempt++) {
            long generation = ProviderPatternGenerationTracker.generation();
            long recipeGeneration = RecipeGenerationTracker.generation();
            Snapshot rebuilt = compileReachable(
                    service,
                    level,
                    root,
                    generation,
                    recipeGeneration,
                    workBudget);
            // コンパイル中に世代が変化した結果は公開せず、現在世代から作り直す。
            if (ProviderPatternGenerationTracker.generation() != generation
                    || RecipeGenerationTracker.generation() != recipeGeneration) {
                continue;
            }
            synchronized (ROOT_CACHE) {
                Map<ResourceKey<Level>, ReachableSnapshotCache> byDimension =
                        ROOT_CACHE.computeIfAbsent(service, ignored -> new LinkedHashMap<>());
                ReachableSnapshotCache cache = byDimension.get(level.dimension());
                // Dimension cacheが別世代なら交換し、旧Pattern参照を残さない。
                if (cache == null || !cache.matches(generation, recipeGeneration)) {
                    cache = new ReachableSnapshotCache(generation, recipeGeneration);
                    byDimension.put(level.dimension(), cache);
                }
                Snapshot raced = cache.get(root);
                // 別スレッドが先に同じRootを再構築した場合は、その結果を採用する。
                if (raced != null && raced != stale) {
                    return raced;
                }
                cache.put(root, rebuilt);
            }
            // 再構築後も不完全なら、同じ世代での無限再コンパイルを禁止する。
            rebuilt.claimRetryRebuild();
            return rebuilt;
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
        synchronized (ROOT_CACHE) {
            ROOT_CACHE.clear();
        }
        SymbolicCraftingPlanner.clearTopologyCache();
        CompiledRootQualificationRegistry.clear();
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
        return new Snapshot(
                service,
                CompiledCraftingGraph.compile(generation, compiledById.values()),
                idByPattern,
                patternById,
                compiledById,
                registeredPatternsByOutput,
                incompletelyCompiledOutputs,
                craftables,
                recipeGeneration,
                null);
    }

    private static Snapshot compileReachable(
            ICraftingService service,
            Level level,
            AEKey root,
            long generation,
            long recipeGeneration,
            PlanningGuard workBudget) {
        ArrayDeque<AEKey> pending = new ArrayDeque<>();
        Set<AEKey> visited = new LinkedHashSet<>();
        Set<AEKey> craftables = new LinkedHashSet<>();
        Set<IPatternDetails> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        IdentityHashMap<IPatternDetails, String> idByPattern = new IdentityHashMap<>();
        Map<String, IPatternDetails> patternById = new LinkedHashMap<>();
        Map<AEKey, Integer> registeredPatternsByOutput = new LinkedHashMap<>();
        Set<AEKey> incompletelyCompiledOutputs = new LinkedHashSet<>();
        Map<String, CompiledPattern<AEKey>> compiledById = new LinkedHashMap<>();
        int completedWorkUnits = 0;
        pending.add(root);

        // Rootから到達する各キーを一度だけ処理し、注文と無関係なProviderを走査しない。
        while (!pending.isEmpty()) {
            workBudget.checkpoint(++completedWorkUnits);
            AEKey key = pending.removeFirst();
            if (!visited.add(key)) {
                continue;
            }
            // AE2はEmitterをPatternより優先するため、同じ地点で探索を打ち切る。
            if (service.canEmitFor(key)) {
                craftables.add(key);
                continue;
            }

            List<IPatternDetails> registered = List.copyOf(service.getCraftingFor(key));
            registeredPatternsByOutput.put(key, registered.size());
            if (registered.isEmpty()) {
                continue;
            }
            craftables.add(key);
            // 既定高速経路は一意なProducerだけを扱うため、複数候補を深く展開しない。
            if (registered.size() != 1) {
                incompletelyCompiledOutputs.add(key);
                continue;
            }

            // AE2がこの出力へ登録した全候補を保持し、複数Producerを単一路線へ誤認しない。
            for (IPatternDetails details : registered) {
                workBudget.checkpoint(++completedWorkUnits);
                // 外部Pattern実装は動的規則を持ち得るため、AE2本体所有の静的表現だけを数式化する。
                if (!details.getClass().getName().startsWith("appeng.")) {
                    incompletelyCompiledOutputs.add(key);
                    continue;
                }
                if (AppliedECompatibility.requiresAe2Planner(details)) {
                    incompletelyCompiledOutputs.add(key);
                    OptimizationMetrics.recordAppliedEPatternFallback();
                    continue;
                }
                // 多候補、返却物、副産物を入口で拒否し、Fallback対象の全候補展開を避ける。
                if (!isStrictReachablePattern(details, level, workBudget)) {
                    incompletelyCompiledOutputs.add(key);
                    continue;
                }
                String id = idByPattern.get(details);
                // 未登録PatternだけFingerprintを作り、同一参照の再計算を避ける。
                if (id == null) {
                    id = Ae2CompiledPatternFactory.fingerprint(details, workBudget);
                    idByPattern.put(details, id);
                }
                if (seen.add(details)) {
                    CompiledPattern<AEKey> pattern = Ae2CompiledPatternFactory.compile(
                            details,
                            id,
                            level,
                            workBudget);
                    if (pattern == null) {
                        incompletelyCompiledOutputs.add(key);
                    } else {
                        compiledById.putIfAbsent(id, pattern);
                        patternById.putIfAbsent(id, details);
                        if (compiledById.size() > MAXIMUM_PATTERNS_PER_REACHABLE_SNAPSHOT) {
                            throw new IllegalStateException(
                                    "reachable crafting graph exceeds its hard pattern bound");
                        }
                    }
                }
                CompiledPattern<AEKey> compiled = compiledById.get(id);
                if (compiled == null || compiled.outputAmount(key) <= 0L) {
                    incompletelyCompiledOutputs.add(key);
                    continue;
                }
                // 全入力候補を追跡し、タグ代替候補を終端素材として落とさない。
                for (CompiledPattern.InputSlot<AEKey> input : compiled.inputs()) {
                    for (CompiledPattern.Stack<AEKey> alternative : input.alternatives()) {
                        pending.addLast(alternative.key());
                    }
                }
            }
        }
        return new Snapshot(
                service,
                CompiledCraftingGraph.compile(generation, compiledById.values(), workBudget),
                idByPattern,
                patternById,
                compiledById,
                registeredPatternsByOutput,
                incompletelyCompiledOutputs,
                craftables,
                recipeGeneration,
                root);
    }

    private static boolean isStrictReachablePattern(
            IPatternDetails details,
            Level level,
            PlanningGuard workBudget) {
        var outputs = details.getOutputs();
        // 副産物や空出力はAE2標準Plannerへ残し、数式経路へ入れない。
        if (outputs.size() != 1 || outputs.get(0).amount() <= 0L) {
            return false;
        }
        var inputs = details.getInputs();
        // 各slotを一度だけ検証し、選択順や返却物を必要とするPatternを早期辞退する。
        for (int slot = 0; slot < inputs.length; slot++) {
            workBudget.checkpoint(slot + 1);
            IPatternDetails.IInput input = inputs[slot];
            var candidates = input.getPossibleInputs();
            if (input.getMultiplier() <= 0L || candidates.length != 1) {
                return false;
            }
            var candidate = candidates[0];
            if (candidate.amount() <= 0L
                    || !input.isValid(candidate.what(), level)
                    || input.getRemainingKey(candidate.what()) != null) {
                return false;
            }
            try {
                Math.multiplyExact(candidate.amount(), input.getMultiplier());
            } catch (ArithmeticException overflow) {
                return false;
            }
        }
        return true;
    }

    private static final class ReachableSnapshotCache {
        private final long generation;
        private final long recipeGeneration;
        private final LinkedHashMap<AEKey, Snapshot> snapshots =
                new LinkedHashMap<>(16, 0.75f, true);

        private ReachableSnapshotCache(long generation, long recipeGeneration) {
            this.generation = generation;
            this.recipeGeneration = recipeGeneration;
        }

        private boolean matches(long expectedGeneration, long expectedRecipeGeneration) {
            return generation == expectedGeneration && recipeGeneration == expectedRecipeGeneration;
        }

        private Snapshot get(AEKey root) {
            return snapshots.get(root);
        }

        private void put(AEKey root, Snapshot snapshot) {
            // Dimension単位の固定上限を超える前に、最も長く未使用のRootを一件だけ除去する。
            if (!snapshots.containsKey(root)
                    && snapshots.size() >= MAXIMUM_REACHABLE_SNAPSHOTS_PER_DIMENSION) {
                var iterator = snapshots.keySet().iterator();
                if (iterator.hasNext()) {
                    iterator.next();
                    iterator.remove();
                }
            }
            snapshots.put(root, snapshot);
        }

        private void remove(AEKey root) {
            snapshots.remove(root);
        }
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
        @Nullable
        private final AEKey reachableRoot;
        private final Map<AEKey, CompiledRootProgram<AEKey>> rootPrograms =
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
                long recipeGeneration,
                @Nullable AEKey reachableRoot) {
            this.service = service;
            this.graph = graph;
            this.idByPattern = new IdentityHashMap<>(idByPattern);
            this.patternById = Map.copyOf(patternById);
            this.compiledById = Map.copyOf(compiledById);
            this.registeredPatternsByOutput = Map.copyOf(registeredPatternsByOutput);
            this.incompletelyCompiledOutputs = Set.copyOf(incompletelyCompiledOutputs);
            this.craftables = Set.copyOf(craftables);
            this.recipeGeneration = recipeGeneration;
            this.reachableRoot = reachableRoot;
        }

        public CompiledCraftingGraph<AEKey> graph() {
            return graph;
        }

        /** このSnapshotに対する一度限りの再構築権を取得する。 */
        private boolean claimRetryRebuild() {
            return retryRebuildClaimed.compareAndSet(false, true);
        }

        /** 全ネットワークSnapshotとRoot専用Snapshotの再試行所有権を混同しない。 */
        private boolean isRootScopedFor(AEKey root) {
            return root.equals(reachableRoot);
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
            return rootProgram(root, PlanningGuard.none());
        }

        public Optional<CompiledRootProgram<AEKey>> rootProgram(
                AEKey root,
                PlanningGuard workBudget) {
            Objects.requireNonNull(workBudget, "workBudget");
            requireCurrentGenerations();
            synchronized (rootPrograms) {
                CompiledRootProgram<AEKey> cached = rootPrograms.get(root);
                // 正常にコンパイルできたRootだけを同じ世代中に再利用する。
                if (cached != null) {
                    return Optional.of(cached);
                }
            }

            Optional<CompiledRootProgram<AEKey>> compiled = CompiledRootProgram.tryCompile(
                    graph,
                    root,
                    service::canEmitFor,
                    workBudget);
            requireCurrentGenerations();
            if (compiled.isPresent() && touchesIncompletePattern(compiled.get())) {
                // 未コンパイルPatternを終端素材と誤認したShadow計算も作らず、直ちにAE2へ戻す。
                return Optional.empty();
            }
            if (compiled.isPresent()
                    && graph.patternsFor(root).isEmpty()
                    && registeredPatternsByOutput.getOrDefault(root, 0) == 0
                    && !service.getCraftingFor(root).isEmpty()) {
                // AE2にPatternがあるのにSnapshotへ無い場合は、終端素材として誤採用しない。
                return Optional.empty();
            }
            // コンパイル不能な構造はキャッシュせず、次世代または次要求でAE2へ戻す。
            if (compiled.isEmpty()) {
                return Optional.empty();
            }
            CompiledRootProgram<AEKey> candidate = compiled.orElseThrow();
            synchronized (rootPrograms) {
                CompiledRootProgram<AEKey> raced = rootPrograms.get(root);
                // 別計算スレッドが先に登録した場合は、その同一世代Programを採用する。
                if (raced != null) {
                    return Optional.of(raced);
                }
                // 固定上限へ達した場合は古いルートを一括破棄し、無制限な常駐を防ぐ。
                if (rootPrograms.size() >= MAXIMUM_ROOT_PROGRAMS_PER_SNAPSHOT) {
                    rootPrograms.clear();
                    strictTopologies.clear();
                }
                rootPrograms.put(root, candidate);
                return Optional.of(candidate);
            }
        }

        /** Root Programを新規コンパイルせず、既存の世代内結果だけを返す。 */
        Optional<CompiledRootProgram<AEKey>> cachedRootProgram(AEKey root) {
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
            return strictTopology(level, grid, program, PlanningGuard.none());
        }

        Optional<Ae2StrictCraftingTopology> strictTopology(
                Level level,
                IGrid grid,
                CompiledRootProgram<AEKey> program,
                PlanningGuard workBudget) {
            Objects.requireNonNull(workBudget, "workBudget");
            requireCurrentGenerations();
            AEKey root = program.root();
            synchronized (rootPrograms) {
                Optional<Ae2StrictCraftingTopology> cached = strictTopologies.get(root);
                // 同じ世代で静的証明済みまたは証明不能なルートは再びPattern APIを走査しない。
                if (cached != null) {
                    return cached;
                }
            }
            Optional<Ae2StrictCraftingTopology> compiled = Optional.ofNullable(
                    Ae2StrictCraftingTopology.compile(level, grid, this, program, workBudget));
            requireCurrentGenerations();
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

        private void requireCurrentGenerations() {
            long currentPatternGeneration = ProviderPatternGenerationTracker.generation();
            long currentRecipeGeneration = RecipeGenerationTracker.generation();
            // 遅延コンパイル中に世代が変わった結果は、同じSnapshotへ公開しない。
            if (graph.generation() != currentPatternGeneration
                    || recipeGeneration != currentRecipeGeneration) {
                throw new StalePlanningSnapshotException(
                        new PlanningGenerationSnapshot(
                                graph.generation(),
                                0L,
                                recipeGeneration),
                        0);
            }
        }
    }
}
