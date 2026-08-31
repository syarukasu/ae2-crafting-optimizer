package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.integration.AppliedECompatibility;
import com.syaru.ae2craftingoptimizer.optimization.OptimizationMetrics;
import com.syaru.ae2craftingoptimizer.optimization.PlanningConfigurationRevisionTracker;
import com.syaru.ae2craftingoptimizer.optimization.ProviderPatternGenerationTracker;
import com.syaru.ae2craftingoptimizer.optimization.ServerPlanningThreadGuard;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Issue #167: mutableなAE2 serviceをplanning workerへ渡さず、root到達範囲だけを固定する。
 * Pattern APIの読み取りは呼出thread、SHA/SCC/配列Program生成は不変Capture上で行う。
 */
public final class Ae2ImmutablePlanningGraphCache {
    /** 一dimension・一世代で保持するroot数。巨大rootの重複保持を抑える固定LRU上限。 */
    private static final int MAXIMUM_ROOTS_PER_DIMENSION = 256;
    /** 一dimension・一世代でcacheする到達キー総数。最大root一件分を上限とする。 */
    private static final int MAXIMUM_CACHED_KEYS_PER_DIMENSION = 1_048_576;
    /** 異常なPattern連鎖でserver threadを占有しないための、root captureの固定キー上限。 */
    private static final int MAXIMUM_CAPTURED_KEYS = 1_048_576;
    /** 64キーごとにcapture revisionを再検証するためのbit mask。 */
    private static final int CAPTURE_REVISION_CHECK_INTERVAL_MASK = 63;
    private static final Map<ICraftingService, Map<ResourceKey<Level>, DimensionCache>> CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private Ae2ImmutablePlanningGraphCache() {
    }

    /**
     * 現在revisionのroot captureを取得する。前後revisionが一致しない値は公開しない。
     */
    @Nullable
    public static RootCapture capture(IGrid grid, Level level, AEKey root) {
        if (grid == null
                || level == null
                || root == null
                || !ServerPlanningThreadGuard.canCapture(level)) {
            return null;
        }
        ICraftingService service = grid.getCraftingService();
        long patternGeneration = ProviderPatternGenerationTracker.generation();
        long recipeGeneration = RecipeGenerationTracker.generation();
        long configurationRevision = PlanningConfigurationRevisionTracker.current();
        RootCapture cached = cached(
                service,
                level.dimension(),
                root,
                patternGeneration,
                recipeGeneration,
                configurationRevision);
        // warm captureも返却直前にrevisionを検査し、旧値を新世代へ持ち越さない。
        if (cached != null) {
            if (generationsMatch(
                    patternGeneration,
                    recipeGeneration,
                    configurationRevision)) {
                OptimizationMetrics.recordPlanningGraphCaptureCache(true);
                return cached;
            }
            OptimizationMetrics.recordPlanningGraphStaleRejection();
            return null;
        }

        long startedNanos = System.nanoTime();
        RootCapture created = captureRoot(
                service,
                level,
                root,
                patternGeneration,
                recipeGeneration,
                configurationRevision);
        // Pattern API読取中にproviderまたはrecipeが変わったcaptureは公開しない。
        if (created == null || !generationsMatch(
                patternGeneration,
                recipeGeneration,
                configurationRevision)) {
            OptimizationMetrics.recordPlanningGraphStaleRejection();
            return null;
        }

        synchronized (CACHE) {
            // lock取得待ちの間に世代が変わった場合も、cacheへ旧値を登録しない。
            if (!generationsMatch(
                    patternGeneration,
                    recipeGeneration,
                    configurationRevision)) {
                OptimizationMetrics.recordPlanningGraphStaleRejection();
                return null;
            }
            Map<ResourceKey<Level>, DimensionCache> byDimension =
                    CACHE.computeIfAbsent(service, ignored -> new LinkedHashMap<>());
            DimensionCache dimension = byDimension.get(level.dimension());
            // 世代が異なるroot群は一括交換し、異なるrevisionを同じLRUへ混在させない。
            if (dimension == null
                    || dimension.patternGeneration != patternGeneration
                    || dimension.recipeGeneration != recipeGeneration
                    || dimension.configurationRevision != configurationRevision) {
                dimension = new DimensionCache(
                        patternGeneration,
                        recipeGeneration,
                        configurationRevision);
                byDimension.put(level.dimension(), dimension);
            }
            RootCapture raced = dimension.roots.get(root);
            // 同じserver thread内の再入または別captureが先に公開した場合は、その値を共有する。
            if (raced != null) {
                OptimizationMetrics.recordPlanningGraphCaptureCache(true);
                return raced;
            }
            dimension.put(root, created);
        }
        OptimizationMetrics.recordPlanningGraphCaptureCache(false);
        OptimizationMetrics.recordPlanningGraphCaptureNanos(System.nanoTime() - startedNanos);
        if (ACOConfig.logCraftingDecisionFlow()) {
            AE2CraftingOptimizer.LOGGER.debug(
                    "ACO-DIAG event=root_capture root={} patternGeneration={} recipeGeneration={} "
                            + "configurationRevision={} keys={} patterns={} failure={} elapsedMicros={}",
                    root.getId(),
                    patternGeneration,
                    recipeGeneration,
                    configurationRevision,
                    created.nodes.size(),
                    created.patterns.size(),
                    created.captureFailure,
                    TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - startedNanos));
        }
        return created;
    }

    public static void clear() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    @Nullable
    private static RootCapture cached(
            ICraftingService service,
            ResourceKey<Level> dimensionKey,
            AEKey root,
            long patternGeneration,
            long recipeGeneration,
            long configurationRevision) {
        synchronized (CACHE) {
            Map<ResourceKey<Level>, DimensionCache> byDimension = CACHE.get(service);
            DimensionCache dimension = byDimension == null ? null : byDimension.get(dimensionKey);
            if (dimension == null
                    || dimension.patternGeneration != patternGeneration
                    || dimension.recipeGeneration != recipeGeneration
                    || dimension.configurationRevision != configurationRevision) {
                return null;
            }
            return dimension.roots.get(root);
        }
    }

    @Nullable
    private static RootCapture captureRoot(
            ICraftingService service,
            Level level,
            AEKey root,
            long patternGeneration,
            long recipeGeneration,
            long configurationRevision) {
        Map<AEKey, NodeCapture> nodes = new LinkedHashMap<>();
        IdentityHashMap<IPatternDetails, Ae2CompiledPatternFactory.Captured> patterns =
                new IdentityHashMap<>();
        List<Ae2CompiledPatternFactory.Captured> orderedPatterns = new java.util.ArrayList<>();
        ArrayDeque<AEKey> pending = new ArrayDeque<>();
        pending.add(root);
        RootProgramFailure captureFailure = RootProgramFailure.NONE;

        // rootから到達するキーだけを一度ずつ読み、全networkのPattern列挙を避ける。
        while (!pending.isEmpty()) {
            // Issue #167: capture中に世代が変わった部分Graphはcacheへ公開しない。
            if ((nodes.size() & CAPTURE_REVISION_CHECK_INTERVAL_MASK) == 0
                    && !generationsMatch(
                            patternGeneration,
                            recipeGeneration,
                            configurationRevision)) {
                return null;
            }
            AEKey key = pending.removeFirst();
            if (nodes.containsKey(key)) {
                continue;
            }
            // 固定上限を越えるGraphは部分公開せず、明示理由でAE2へ戻す。
            if (nodes.size() >= MAXIMUM_CAPTURED_KEYS) {
                captureFailure = RootProgramFailure.PROGRAM_TOO_LARGE;
                break;
            }

            boolean emittable = service.canEmitFor(key);
            // EmitterはAE2と同じくPatternより先に終端化し、不要な下位探索を行わない。
            if (emittable) {
                nodes.put(key, NodeCapture.emittableNode());
                continue;
            }

            List<IPatternDetails> candidates = List.copyOf(service.getCraftingFor(key));
            // Patternなしは在庫またはmissingで解決する終端として固定する。
            if (candidates.isEmpty()) {
                nodes.put(key, NodeCapture.terminal());
                continue;
            }
            // AE2の候補集合と順序を保存し、複数候補をACO側で選択・枝刈りしない。
            if (candidates.size() != 1) {
                nodes.put(key, NodeCapture.ambiguous(candidates));
                continue;
            }

            IPatternDetails details = candidates.get(0);
            if (AppliedECompatibility.requiresAe2Planner(details)) {
                nodes.put(key, NodeCapture.incomplete(candidates));
                OptimizationMetrics.recordAppliedEPatternFallback();
                continue;
            }
            Ae2CompiledPatternFactory.Captured captured;
            try {
                captured = patterns.get(details);
                if (captured == null) {
                    captured = Ae2CompiledPatternFactory.capture(details, level);
                    if (captured != null) {
                        patterns.put(details, captured);
                        orderedPatterns.add(captured);
                    }
                }
            } catch (CountOverflowException invalidPatternAmount) {
                captured = null;
            }
            // 動的・返却物付き・不正数量などを固定できないPatternはAE2へ委譲する。
            if (captured == null) {
                nodes.put(key, NodeCapture.incomplete(candidates));
                continue;
            }
            nodes.put(key, NodeCapture.compiled(candidates, captured));
            // Pattern slotの候補順を維持したまま、到達する全入力キーをcapture対象へ追加する。
            for (CompiledPattern.InputSlot<AEKey> slot : captured.inputs()) {
                for (CompiledPattern.Stack<AEKey> input : slot.alternatives()) {
                    pending.addLast(input.key());
                }
            }
        }
        return new RootCapture(
                root,
                patternGeneration,
                recipeGeneration,
                configurationRevision,
                Collections.unmodifiableMap(new LinkedHashMap<>(nodes)),
                List.copyOf(orderedPatterns),
                captureFailure);
    }

    private static boolean generationsMatch(
            long patternGeneration,
            long recipeGeneration,
            long configurationRevision) {
        return ProviderPatternGenerationTracker.generation() == patternGeneration
                && RecipeGenerationTracker.generation() == recipeGeneration
                && PlanningConfigurationRevisionTracker.isCurrent(configurationRevision);
    }

    private static final class DimensionCache {
        private final long patternGeneration;
        private final long recipeGeneration;
        private final long configurationRevision;
        private final LinkedHashMap<AEKey, RootCapture> roots =
                new LinkedHashMap<>(16, 0.75F, true);
        private long cachedKeyCount;

        private DimensionCache(
                long patternGeneration,
                long recipeGeneration,
                long configurationRevision) {
            this.patternGeneration = patternGeneration;
            this.recipeGeneration = recipeGeneration;
            this.configurationRevision = configurationRevision;
        }

        private void put(AEKey root, RootCapture capture) {
            roots.put(root, capture);
            cachedKeyCount += capture.nodes.size();
            /*
             * root件数または到達キー総数を超えた間だけ、access-order最古から除く。
             * 一件の巨大captureも呼出側へは返すが、上限超過ならcacheへ残さない。
             */
            while (roots.size() > MAXIMUM_ROOTS_PER_DIMENSION
                    || cachedKeyCount > MAXIMUM_CACHED_KEYS_PER_DIMENSION) {
                Map.Entry<AEKey, RootCapture> eldest = roots.entrySet().iterator().next();
                cachedKeyCount -= eldest.getValue().nodes.size();
                roots.remove(eldest.getKey());
            }
        }
    }

    /** server threadで取得済みの不変値だけを保持し、worker側ではAE2 APIを呼ばない。 */
    public static final class RootCapture {
        private final AEKey root;
        private final long patternGeneration;
        private final long recipeGeneration;
        private final long configurationRevision;
        private final Map<AEKey, NodeCapture> nodes;
        private final List<Ae2CompiledPatternFactory.Captured> patterns;
        private final RootProgramFailure captureFailure;
        private volatile Snapshot compiled;

        private RootCapture(
                AEKey root,
                long patternGeneration,
                long recipeGeneration,
                long configurationRevision,
                Map<AEKey, NodeCapture> nodes,
                List<Ae2CompiledPatternFactory.Captured> patterns,
                RootProgramFailure captureFailure) {
            this.root = root;
            this.patternGeneration = patternGeneration;
            this.recipeGeneration = recipeGeneration;
            this.configurationRevision = configurationRevision;
            this.nodes = nodes;
            this.patterns = patterns;
            this.captureFailure = captureFailure;
        }

        public long patternGeneration() {
            return patternGeneration;
        }

        public long recipeGeneration() {
            return recipeGeneration;
        }

        public long configurationRevision() {
            return configurationRevision;
        }

        /** 不変captureが所有するroot到達キーの読取専用view。 */
        Iterable<AEKey> referencedKeys() {
            return nodes.keySet();
        }

        /** SHA、SCC、配列Programを初回workerだけで生成し、同じCapture内で再利用する。 */
        Ae2PlanningGraphSnapshot compile() {
            Snapshot current = compiled;
            if (current != null) {
                OptimizationMetrics.recordPlanningGraphCompileCache(true);
                return current;
            }
            synchronized (this) {
                current = compiled;
                if (current != null) {
                    OptimizationMetrics.recordPlanningGraphCompileCache(true);
                    return current;
                }
                long startedNanos = System.nanoTime();
                current = Snapshot.compile(this);
                compiled = current;
                OptimizationMetrics.recordPlanningGraphCompileCache(false);
                OptimizationMetrics.recordPlanningGraphCompileNanos(System.nanoTime() - startedNanos);
                if (ACOConfig.logCraftingDecisionFlow()) {
                    CompiledRootProgram.Outcome<AEKey> outcome = current.rootProgramOutcome(root);
                    int compiledNodes = outcome.program()
                            .map(CompiledRootProgram::nodeCount)
                            .orElse(0);
                    AE2CraftingOptimizer.LOGGER.debug(
                            "ACO-DIAG event=root_compile root={} patternGeneration={} recipeGeneration={} "
                                    + "configurationRevision={} nodes={} patterns={} failure={} elapsedMicros={}",
                            root.getId(),
                            patternGeneration,
                            recipeGeneration,
                            configurationRevision,
                            compiledNodes,
                            current.patternById.size(),
                            outcome.failure(),
                            TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - startedNanos));
                }
                return current;
            }
        }

        Optional<Ae2PlanningGraphSnapshot> compiledSnapshot() {
            return Optional.ofNullable(compiled);
        }
    }

    private record NodeCapture(
            boolean emittable,
            List<IPatternDetails> candidates,
            @Nullable Ae2CompiledPatternFactory.Captured pattern,
            boolean incomplete) {
        private NodeCapture {
            candidates = List.copyOf(candidates);
        }

        private static NodeCapture emittableNode() {
            return new NodeCapture(true, List.of(), null, false);
        }

        private static NodeCapture terminal() {
            return new NodeCapture(false, List.of(), null, false);
        }

        private static NodeCapture ambiguous(List<IPatternDetails> candidates) {
            return new NodeCapture(false, candidates, null, false);
        }

        private static NodeCapture incomplete(List<IPatternDetails> candidates) {
            return new NodeCapture(false, candidates, null, true);
        }

        private static NodeCapture compiled(
                List<IPatternDetails> candidates,
                Ae2CompiledPatternFactory.Captured pattern) {
            return new NodeCapture(false, candidates, pattern, false);
        }
    }

    private static final class Snapshot implements Ae2PlanningGraphSnapshot {
        private final AEKey root;
        private final CompiledCraftingGraph<AEKey> graph;
        private final IdentityHashMap<IPatternDetails, String> idByPattern;
        private final Map<String, IPatternDetails> patternById;
        private final Map<AEKey, Integer> registeredPatternCounts;
        private final Set<AEKey> incompleteOutputs;
        private final Set<AEKey> emittableKeys;
        private final Set<String> exactInputDomains;
        private final long recipeGeneration;
        private final RootProgramFailure captureFailure;
        private volatile CompiledRootProgram.Outcome<AEKey> rootOutcome;
        private volatile Optional<Ae2StrictCraftingTopology> strictTopology;

        private Snapshot(
                AEKey root,
                CompiledCraftingGraph<AEKey> graph,
                IdentityHashMap<IPatternDetails, String> idByPattern,
                Map<String, IPatternDetails> patternById,
                Map<AEKey, Integer> registeredPatternCounts,
                Set<AEKey> incompleteOutputs,
                Set<AEKey> emittableKeys,
                Set<String> exactInputDomains,
                long recipeGeneration,
                RootProgramFailure captureFailure) {
            this.root = root;
            this.graph = graph;
            this.idByPattern = new IdentityHashMap<>(idByPattern);
            this.patternById = Map.copyOf(patternById);
            this.registeredPatternCounts = Map.copyOf(registeredPatternCounts);
            this.incompleteOutputs = Set.copyOf(incompleteOutputs);
            this.emittableKeys = Set.copyOf(emittableKeys);
            this.exactInputDomains = Set.copyOf(exactInputDomains);
            this.recipeGeneration = recipeGeneration;
            this.captureFailure = captureFailure;
        }

        private static Snapshot compile(RootCapture capture) {
            IdentityHashMap<IPatternDetails, String> idByPattern = new IdentityHashMap<>();
            Map<String, IPatternDetails> patternById = new LinkedHashMap<>();
            Map<String, CompiledPattern<AEKey>> compiledById = new LinkedHashMap<>();
            Set<String> exactInputDomains = new LinkedHashSet<>();
            // Pattern identityごとに一度だけfingerprintを計算し、重複出力でも同じIDを再利用する。
            for (Ae2CompiledPatternFactory.Captured pattern : capture.patterns) {
                String id = pattern.fingerprint();
                idByPattern.put(pattern.details(), id);
                patternById.putIfAbsent(id, pattern.details());
                compiledById.putIfAbsent(id, pattern.compile(id));
                if (pattern.exactInputDomain()) {
                    exactInputDomains.add(id);
                }
            }

            Map<AEKey, Integer> registeredPatternCounts = new LinkedHashMap<>();
            Set<AEKey> incompleteOutputs = new LinkedHashSet<>();
            Set<AEKey> emittableKeys = new LinkedHashSet<>();
            // capture済みNodeのAE2候補件数・Emitter・不完全状態を不変索引へ変換する。
            for (Map.Entry<AEKey, NodeCapture> entry : capture.nodes.entrySet()) {
                AEKey key = entry.getKey();
                NodeCapture node = entry.getValue();
                registeredPatternCounts.put(key, node.candidates().size());
                if (node.emittable()) {
                    emittableKeys.add(key);
                }
                if (node.incomplete()) {
                    incompleteOutputs.add(key);
                }
            }
            return new Snapshot(
                    capture.root,
                    CompiledCraftingGraph.compile(
                            capture.patternGeneration,
                            compiledById.values()),
                    idByPattern,
                    patternById,
                    registeredPatternCounts,
                    incompleteOutputs,
                    emittableKeys,
                    exactInputDomains,
                    capture.recipeGeneration,
                    capture.captureFailure);
        }

        @Override
        public CompiledCraftingGraph<AEKey> graph() {
            return graph;
        }

        @Override
        public long recipeGeneration() {
            return recipeGeneration;
        }

        @Override
        public String id(IPatternDetails pattern) {
            return idByPattern.get(pattern);
        }

        @Override
        public IPatternDetails pattern(String id) {
            return patternById.get(id);
        }

        @Override
        public int registeredPatternCount(AEKey output) {
            return registeredPatternCounts.getOrDefault(output, 0);
        }

        @Override
        public boolean isIncompletelyCompiled(AEKey output) {
            return incompleteOutputs.contains(output);
        }

        @Override
        public boolean isEmittable(AEKey key) {
            return emittableKeys.contains(key);
        }

        @Override
        public boolean hasExactlyOneFullyCompiledPattern(AEKey output) {
            return registeredPatternCount(output) == 1
                    && !isIncompletelyCompiled(output)
                    && graph.patternsFor(output).size() == 1;
        }

        @Override
        public boolean hasExactInputDomain(String patternId) {
            return exactInputDomains.contains(patternId);
        }

        @Override
        public CompiledRootProgram.Outcome<AEKey> rootProgramOutcome(AEKey requestedRoot) {
            if (!root.equals(requestedRoot)) {
                return CompiledRootProgram.Outcome.failed(RootProgramFailure.MISSING_FROM_SNAPSHOT);
            }
            CompiledRootProgram.Outcome<AEKey> current = rootOutcome;
            if (current != null) {
                return current;
            }
            synchronized (this) {
                current = rootOutcome;
                if (current == null) {
                    current = compileRootOutcome();
                    rootOutcome = current;
                }
                return current;
            }
        }

        private CompiledRootProgram.Outcome<AEKey> compileRootOutcome() {
            if (captureFailure != RootProgramFailure.NONE) {
                return CompiledRootProgram.Outcome.failed(captureFailure);
            }
            CompiledRootProgram.Outcome<AEKey> outcome =
                    CompiledRootProgram.compile(graph, root, emittableKeys::contains);
            if (outcome.program().isEmpty()) {
                return outcome;
            }
            CompiledRootProgram<AEKey> program = outcome.program().orElseThrow();
            // 到達NodeのAE2候補件数を検査し、曖昧な候補を終端素材として誤採用しない。
            for (int node = 0; node < program.nodeCount(); node++) {
                AEKey key = program.keyAt(node);
                if (registeredPatternCount(key) > 1) {
                    return CompiledRootProgram.Outcome.failed(RootProgramFailure.MULTIPLE_PRODUCERS);
                }
                if (isIncompletelyCompiled(key)
                        || (registeredPatternCount(key) == 1 && graph.patternsFor(key).size() != 1)) {
                    return CompiledRootProgram.Outcome.failed(
                            RootProgramFailure.INCOMPLETE_PATTERN_SNAPSHOT);
                }
            }
            return outcome;
        }

        @Override
        public Optional<CompiledRootProgram.Outcome<AEKey>> cachedRootProgramOutcome(AEKey requestedRoot) {
            if (!root.equals(requestedRoot)) {
                return Optional.empty();
            }
            return Optional.ofNullable(rootOutcome);
        }

        @Override
        public Optional<Ae2StrictCraftingTopology> strictTopology(
                CompiledRootProgram<AEKey> program) {
            Optional<Ae2StrictCraftingTopology> current = strictTopology;
            if (current != null) {
                return current;
            }
            synchronized (this) {
                current = strictTopology;
                if (current == null) {
                    current = Optional.ofNullable(Ae2StrictCraftingTopology.compile(this, program));
                    strictTopology = current;
                }
                return current;
            }
        }

        @Override
        public Optional<Ae2StrictCraftingTopology> cachedStrictTopology(AEKey requestedRoot) {
            if (!root.equals(requestedRoot)) {
                return Optional.empty();
            }
            Optional<Ae2StrictCraftingTopology> current = strictTopology;
            return current == null ? Optional.empty() : current;
        }
    }
}
