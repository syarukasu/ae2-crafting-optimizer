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
import com.syaru.ae2craftingoptimizer.optimization.ServerPlanningThreadGuard;
import com.syaru.ae2craftingoptimizer.optimization.WeightedLruMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Issue #167/#179: live AE2 serviceをplanning workerへ渡さず、Provider更新後の
 * server tickで候補順を一度だけ固定する。発注時は公開済みSnapshotを参照するだけにする。
 */
public final class Ae2ImmutablePlanningGraphCache {
    /** 一つの公開Snapshotへ固定するPattern数の上限。 */
    private static final int MAXIMUM_COMPILED_PATTERNS = 1_048_576;
    /** 一世代で保持するroot別Program数。 */
    private static final int MAXIMUM_ROOT_PROGRAMS_PER_SNAPSHOT = 256;
    /** root別Programが保持する合計Node数の上限。 */
    private static final int MAXIMUM_ROOT_PROGRAM_NODES_PER_SNAPSHOT = 1_048_576;
    /** capture中に世代を再確認する間隔を表すbit mask。 */
    private static final int CAPTURE_REVISION_CHECK_INTERVAL_MASK = 63;
    private static final AEKeyFilter ALL_KEYS = key -> true;
    private static final Comparator<AEKey> STABLE_KEY_ORDER = Comparator.comparing(
            key -> key.toTagGeneric().toString());
    private static final Map<ICraftingService, PublishedServiceState> PUBLISHED =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final AtomicLong NEXT_RUNTIME_ID = new AtomicLong(1L);

    private Ae2ImmutablePlanningGraphCache() {
    }

    /** 現在revisionで公開済みの不変Pattern indexをrootへpinする。 */
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
        observeLevel(service, level);
        synchronized (PUBLISHED) {
            PublishedServiceState serviceState = PUBLISHED.get(service);
            PublishedDimensionState dimension = serviceState == null
                    ? null
                    : serviceState.dimensions.get(level.dimension());
            PublishedCapture published = dimension == null ? null : dimension.current;
            // 未公開または世代不一致のindexを注文の世代へ付け替えず、AE2側へ辞退する。
            if (published == null
                    || published.patternGeneration != patternGeneration
                    || published.recipeGeneration != recipeGeneration
                    || published.configurationRevision != configurationRevision) {
                if (dimension != null) {
                    dimension.current = null;
                    dimension.dirty = true;
                }
                OptimizationMetrics.recordPlanningGraphCaptureCache(false);
                return null;
            }
            OptimizationMetrics.recordPlanningGraphCaptureCache(true);
            return new RootCapture(root, published, serviceState.runtimeIdentity);
        }
    }

    /** Providerを持つdimensionを記録し、次のserver tickでindexを準備する。 */
    public static void observeLevel(ICraftingService service, Level level) {
        if (service == null || level == null || !ServerPlanningThreadGuard.canCapture(level)) {
            return;
        }
        synchronized (PUBLISHED) {
            PublishedServiceState serviceState = PUBLISHED.computeIfAbsent(
                    service,
                    ignored -> new PublishedServiceState(nextRuntimeIdentity()));
            PublishedDimensionState dimension = serviceState.dimensions.computeIfAbsent(
                    level.dimension(),
                    ignored -> new PublishedDimensionState(level));
            // Level instanceが交換されたdimensionでは旧bindingを残さない。
            if (dimension.level != level) {
                dimension.level = level;
                dimension.current = null;
                dimension.dirty = true;
            }
        }
    }

    /** AE2 Provider索引の変更後に、同じGridの公開Snapshotを失効させる。 */
    public static void invalidate(ICraftingService service, Level level) {
        observeLevel(service, level);
        synchronized (PUBLISHED) {
            PublishedServiceState serviceState = PUBLISHED.get(service);
            if (serviceState == null) {
                return;
            }
            // CraftingServiceの索引はGrid全体なので、観測済みdimensionを同じ世代で失効させる。
            for (PublishedDimensionState dimension : serviceState.dimensions.values()) {
                dimension.current = null;
                dimension.dirty = true;
            }
        }
    }

    /** 発注とは独立したserver tick境界で、AE2候補順をimmutable indexへ公開する。 */
    public static void refreshPublishedIndexes(ICraftingService service) {
        if (service == null) {
            return;
        }
        List<PublishedBuildTarget> targets = new ArrayList<>();
        synchronized (PUBLISHED) {
            PublishedServiceState serviceState = PUBLISHED.get(service);
            if (serviceState == null) {
                return;
            }
            long patternGeneration = ProviderPatternGenerationTracker.generation();
            long recipeGeneration = RecipeGenerationTracker.generation();
            long configurationRevision = PlanningConfigurationRevisionTracker.current();
            // dirtyまたはrevision不一致のdimensionだけを一tickにつき一度再取得する。
            for (Map.Entry<ResourceKey<Level>, PublishedDimensionState> entry
                    : serviceState.dimensions.entrySet()) {
                PublishedDimensionState dimension = entry.getValue();
                PublishedCapture current = dimension.current;
                if (dimension.dirty
                        || current == null
                        || current.patternGeneration != patternGeneration
                        || current.recipeGeneration != recipeGeneration
                        || current.configurationRevision != configurationRevision) {
                    targets.add(new PublishedBuildTarget(
                            serviceState,
                            entry.getKey(),
                            dimension,
                            dimension.level,
                            patternGeneration,
                            recipeGeneration,
                            configurationRevision));
                }
            }
        }
        // live Pattern APIはserver thread上でだけ読み、PUBLISHED lock中には呼ばない。
        for (PublishedBuildTarget target : targets) {
            publish(service, target);
        }
    }

    public static void clear() {
        synchronized (PUBLISHED) {
            PUBLISHED.clear();
        }
    }

    private static void publish(ICraftingService service, PublishedBuildTarget target) {
        Level level = target.level;
        if (level == null || !ServerPlanningThreadGuard.canCapture(level)) {
            return;
        }
        long startedNanos = System.nanoTime();
        PublishedCapture created = capturePublished(
                service,
                level,
                target.patternGeneration,
                target.recipeGeneration,
                target.configurationRevision);
        if (created == null) {
            OptimizationMetrics.recordPlanningGraphStaleRejection();
            return;
        }
        synchronized (PUBLISHED) {
            PublishedServiceState currentService = PUBLISHED.get(service);
            PublishedDimensionState currentDimension = currentService == null
                    ? null
                    : currentService.dimensions.get(target.dimensionKey);
            // capture中にService、Level、またはrevisionが変わった値は公開しない。
            if (currentService != target.serviceState
                    || currentDimension != target.dimensionState
                    || currentDimension.level != level
                    || !generationsMatch(
                            target.patternGeneration,
                            target.recipeGeneration,
                            target.configurationRevision)) {
                OptimizationMetrics.recordPlanningGraphStaleRejection();
                return;
            }
            currentDimension.current = created;
            currentDimension.dirty = false;
        }
        OptimizationMetrics.recordPlanningGraphCaptureCache(false);
        OptimizationMetrics.recordPlanningGraphCaptureNanos(System.nanoTime() - startedNanos);
        if (ACOConfig.logCraftingDecisionFlow()) {
            AE2CraftingOptimizer.LOGGER.debug(
                    "ACO-DIAG event=pattern_index_published dimension={} patternGeneration={} "
                            + "recipeGeneration={} configurationRevision={} keys={} patterns={} elapsedMicros={}",
                    level.dimension().location(),
                    target.patternGeneration,
                    target.recipeGeneration,
                    target.configurationRevision,
                    created.nodes.size(),
                    created.patterns.size(),
                    TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - startedNanos));
        }
    }

    @Nullable
    private static PublishedCapture capturePublished(
            ICraftingService service,
            Level level,
            long patternGeneration,
            long recipeGeneration,
            long configurationRevision) {
        List<AEKey> craftables = new ArrayList<>(service.getCraftables(ALL_KEYS));
        craftables.sort(STABLE_KEY_ORDER);
        IdentityHashMap<IPatternDetails, Ae2CompiledPatternFactory.Captured> capturedByPattern =
                new IdentityHashMap<>();
        List<Ae2CompiledPatternFactory.Captured> orderedPatterns = new ArrayList<>();
        Map<AEKey, NodeCapture> nodes = new LinkedHashMap<>();
        Set<AEKey> referencedKeys = new LinkedHashSet<>(craftables);

        // AE2の出力キーごとの候補順をそのまま固定する。
        for (int keyIndex = 0; keyIndex < craftables.size(); keyIndex++) {
            if ((keyIndex & CAPTURE_REVISION_CHECK_INTERVAL_MASK) == 0
                    && !generationsMatch(
                            patternGeneration,
                            recipeGeneration,
                            configurationRevision)) {
                return null;
            }
            AEKey key = craftables.get(keyIndex);
            if (service.canEmitFor(key)) {
                nodes.put(key, NodeCapture.emittableNode());
                continue;
            }
            List<IPatternDetails> candidates = List.copyOf(service.getCraftingFor(key));
            boolean incomplete = false;
            // 同じPattern identityは一度だけcaptureし、候補配列内の位置だけを保持する。
            for (IPatternDetails details : candidates) {
                Ae2CompiledPatternFactory.Captured captured = capturedByPattern.get(details);
                if (captured == null) {
                    if (AppliedECompatibility.requiresAe2Planner(details)) {
                        incomplete = true;
                        OptimizationMetrics.recordAppliedEPatternFallback();
                        continue;
                    }
                    try {
                        captured = Ae2CompiledPatternFactory.capture(details, level);
                    } catch (CountOverflowException invalidPatternAmount) {
                        captured = null;
                    }
                    if (captured == null) {
                        incomplete = true;
                        continue;
                    }
                    capturedByPattern.put(details, captured);
                    orderedPatterns.add(captured);
                    if (orderedPatterns.size() > MAXIMUM_COMPILED_PATTERNS) {
                        return null;
                    }
                }
                // Pattern入力キーも在庫Snapshotへ含め、workerがlive serviceを引かないようにする。
                for (CompiledPattern.InputSlot<AEKey> slot : captured.inputs()) {
                    for (CompiledPattern.Stack<AEKey> input : slot.alternatives()) {
                        referencedKeys.add(input.key());
                    }
                }
            }
            nodes.put(key, new NodeCapture(false, candidates, incomplete));
        }
        // Patternを持たない入力キーも不足・在庫終端としてindexへ固定する。
        for (AEKey key : referencedKeys) {
            nodes.putIfAbsent(key, NodeCapture.terminal());
        }
        if (!generationsMatch(
                patternGeneration,
                recipeGeneration,
                configurationRevision)) {
            return null;
        }
        return new PublishedCapture(
                patternGeneration,
                recipeGeneration,
                configurationRevision,
                Collections.unmodifiableMap(new LinkedHashMap<>(nodes)),
                List.copyOf(orderedPatterns));
    }

    private static boolean generationsMatch(
            long patternGeneration,
            long recipeGeneration,
            long configurationRevision) {
        return ProviderPatternGenerationTracker.generation() == patternGeneration
                && RecipeGenerationTracker.generation() == recipeGeneration
                && PlanningConfigurationRevisionTracker.isCurrent(configurationRevision);
    }

    private static long nextRuntimeIdentity() {
        return NEXT_RUNTIME_ID.getAndUpdate(current -> {
            if (current == Long.MAX_VALUE) {
                throw new IllegalStateException("planning runtime identity exhausted");
            }
            return current + 1L;
        });
    }

    private static final class PublishedServiceState {
        private final long runtimeIdentity;
        private final Map<ResourceKey<Level>, PublishedDimensionState> dimensions =
                new LinkedHashMap<>();

        private PublishedServiceState(long runtimeIdentity) {
            this.runtimeIdentity = runtimeIdentity;
        }
    }

    private static final class PublishedDimensionState {
        private Level level;
        private boolean dirty = true;
        private PublishedCapture current;

        private PublishedDimensionState(Level level) {
            this.level = level;
        }
    }

    private record PublishedBuildTarget(
            PublishedServiceState serviceState,
            ResourceKey<Level> dimensionKey,
            PublishedDimensionState dimensionState,
            Level level,
            long patternGeneration,
            long recipeGeneration,
            long configurationRevision) {
    }

    private static final class PublishedCapture {
        private final long patternGeneration;
        private final long recipeGeneration;
        private final long configurationRevision;
        private final Map<AEKey, NodeCapture> nodes;
        private final List<Ae2CompiledPatternFactory.Captured> patterns;
        private volatile Snapshot compiled;

        private PublishedCapture(
                long patternGeneration,
                long recipeGeneration,
                long configurationRevision,
                Map<AEKey, NodeCapture> nodes,
                List<Ae2CompiledPatternFactory.Captured> patterns) {
            this.patternGeneration = patternGeneration;
            this.recipeGeneration = recipeGeneration;
            this.configurationRevision = configurationRevision;
            this.nodes = nodes;
            this.patterns = patterns;
        }

        private Snapshot compile() {
            Snapshot current = compiled;
            if (current != null) {
                OptimizationMetrics.recordPlanningGraphCompileCache(true);
                return current;
            }
            synchronized (this) {
                current = compiled;
                if (current == null) {
                    long startedNanos = System.nanoTime();
                    current = Snapshot.compile(this);
                    compiled = current;
                    OptimizationMetrics.recordPlanningGraphCompileCache(false);
                    OptimizationMetrics.recordPlanningGraphCompileNanos(
                            System.nanoTime() - startedNanos);
                } else {
                    OptimizationMetrics.recordPlanningGraphCompileCache(true);
                }
                return current;
            }
        }
    }

    /** server threadで取得済みの不変値だけを保持し、worker側ではAE2 APIを呼ばない。 */
    public static final class RootCapture {
        private final AEKey root;
        private final PublishedCapture published;
        private final long runtimeIdentity;

        private RootCapture(AEKey root, PublishedCapture published, long runtimeIdentity) {
            this.root = root;
            this.published = published;
            this.runtimeIdentity = runtimeIdentity;
        }

        public long patternGeneration() {
            return published.patternGeneration;
        }

        public long recipeGeneration() {
            return published.recipeGeneration;
        }

        public long configurationRevision() {
            return published.configurationRevision;
        }

        long runtimeIdentity() {
            return runtimeIdentity;
        }

        /** 公開indexが参照する全キーの読取専用view。 */
        Iterable<AEKey> referencedKeys() {
            return published.nodes.keySet();
        }

        /** fingerprint、SCC、配列Programをworker側で初回だけ生成する。 */
        Ae2PlanningGraphSnapshot compile() {
            return published.compile();
        }

        Optional<Ae2PlanningGraphSnapshot> compiledSnapshot() {
            return Optional.ofNullable(published.compiled);
        }
    }

    private record NodeCapture(
            boolean emittable,
            List<IPatternDetails> candidates,
            boolean incomplete) {
        private NodeCapture {
            candidates = List.copyOf(candidates);
        }

        private static NodeCapture emittableNode() {
            return new NodeCapture(true, List.of(), false);
        }

        private static NodeCapture terminal() {
            return new NodeCapture(false, List.of(), false);
        }
    }

    private static final class Snapshot implements Ae2PlanningGraphSnapshot {
        private final CompiledCraftingGraph<AEKey> graph;
        private final IdentityHashMap<IPatternDetails, String> idByPattern;
        private final Map<String, IPatternDetails> patternById;
        private final Map<AEKey, Integer> registeredPatternCounts;
        private final Set<AEKey> incompleteOutputs;
        private final Set<AEKey> emittableKeys;
        private final Set<String> exactInputDomains;
        private final long recipeGeneration;
        private final WeightedLruMap<AEKey, CompiledRootProgram.Outcome<AEKey>> rootOutcomes =
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
                Map<AEKey, Integer> registeredPatternCounts,
                Set<AEKey> incompleteOutputs,
                Set<AEKey> emittableKeys,
                Set<String> exactInputDomains,
                long recipeGeneration) {
            this.graph = graph;
            this.idByPattern = new IdentityHashMap<>(idByPattern);
            this.patternById = Map.copyOf(patternById);
            this.registeredPatternCounts = Map.copyOf(registeredPatternCounts);
            this.incompleteOutputs = Set.copyOf(incompleteOutputs);
            this.emittableKeys = Set.copyOf(emittableKeys);
            this.exactInputDomains = Set.copyOf(exactInputDomains);
            this.recipeGeneration = recipeGeneration;
        }

        private static Snapshot compile(PublishedCapture capture) {
            IdentityHashMap<IPatternDetails, String> idByPattern = new IdentityHashMap<>();
            IdentityHashMap<IPatternDetails, CompiledPattern<AEKey>> compiledByPattern =
                    new IdentityHashMap<>();
            Map<String, IPatternDetails> patternById = new LinkedHashMap<>();
            List<CompiledPattern<AEKey>> compiledPatterns = new ArrayList<>();
            Set<String> exactInputDomains = new LinkedHashSet<>();
            // immutable Pattern captureごとにfingerprintをworker上で一度だけ作る。
            for (Ae2CompiledPatternFactory.Captured pattern : capture.patterns) {
                String id = pattern.fingerprint();
                CompiledPattern<AEKey> compiled = pattern.compile(id);
                idByPattern.put(pattern.details(), id);
                compiledByPattern.put(pattern.details(), compiled);
                patternById.putIfAbsent(id, pattern.details());
                compiledPatterns.add(compiled);
                if (pattern.exactInputDomain()) {
                    exactInputDomains.add(id);
                }
            }

            Map<AEKey, Integer> registeredPatternCounts = new LinkedHashMap<>();
            Map<AEKey, List<CompiledPattern<AEKey>>> candidatesByOutput = new LinkedHashMap<>();
            Set<AEKey> incompleteOutputs = new LinkedHashSet<>();
            Set<AEKey> emittableKeys = new LinkedHashSet<>();
            // nodeごとのAE2候補順を、対応するpure Pattern配列へ変換する。
            for (Map.Entry<AEKey, NodeCapture> entry : capture.nodes.entrySet()) {
                AEKey key = entry.getKey();
                NodeCapture node = entry.getValue();
                registeredPatternCounts.put(key, node.candidates().size());
                if (node.emittable()) {
                    emittableKeys.add(key);
                }
                boolean incomplete = node.incomplete();
                List<CompiledPattern<AEKey>> candidates = new ArrayList<>(node.candidates().size());
                for (IPatternDetails details : node.candidates()) {
                    CompiledPattern<AEKey> compiled = compiledByPattern.get(details);
                    if (compiled == null || compiled.outputAmount(key) <= 0L) {
                        incomplete = true;
                        continue;
                    }
                    candidates.add(compiled);
                }
                if (incomplete) {
                    incompleteOutputs.add(key);
                }
                candidatesByOutput.put(key, List.copyOf(candidates));
            }
            return new Snapshot(
                    CompiledCraftingGraph.compile(
                            capture.patternGeneration,
                            compiledPatterns),
                    idByPattern,
                    patternById,
                    registeredPatternCounts,
                    incompleteOutputs,
                    emittableKeys,
                    exactInputDomains,
                    capture.recipeGeneration);
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
        public CompiledRootProgram.Outcome<AEKey> rootProgramOutcome(AEKey root) {
            synchronized (rootOutcomes) {
                CompiledRootProgram.Outcome<AEKey> cached = rootOutcomes.get(root);
                if (cached != null) {
                    return cached;
                }
            }
            CompiledRootProgram.Outcome<AEKey> outcome = compileRootOutcome(root);
            synchronized (rootOutcomes) {
                CompiledRootProgram.Outcome<AEKey> raced = rootOutcomes.get(root);
                if (raced != null) {
                    return raced;
                }
                return rootOutcomes.putIfAbsent(root, outcome, strictTopologies::remove);
            }
        }

        private CompiledRootProgram.Outcome<AEKey> compileRootOutcome(AEKey root) {
            CompiledRootProgram.Outcome<AEKey> outcome =
                    CompiledRootProgram.compile(graph, root, emittableKeys::contains);
            if (outcome.program().isEmpty()) {
                return outcome;
            }
            CompiledRootProgram<AEKey> program = outcome.program().orElseThrow();
            // AE2候補数とpure graph候補数が一つでも違うrootをAuthoritativeにしない。
            for (int node = 0; node < program.nodeCount(); node++) {
                AEKey key = program.keyAt(node);
                int registered = registeredPatternCount(key);
                int compiled = graph.patternsFor(key).size();
                if (registered > 1) {
                    return CompiledRootProgram.Outcome.failed(
                            RootProgramFailure.MULTIPLE_PRODUCERS);
                }
                if (isIncompletelyCompiled(key) || registered != compiled) {
                    return CompiledRootProgram.Outcome.failed(
                            RootProgramFailure.INCOMPLETE_PATTERN_SNAPSHOT);
                }
            }
            return outcome;
        }

        @Override
        public Optional<CompiledRootProgram.Outcome<AEKey>> cachedRootProgramOutcome(AEKey root) {
            synchronized (rootOutcomes) {
                return Optional.ofNullable(rootOutcomes.get(root));
            }
        }

        @Override
        public Optional<Ae2StrictCraftingTopology> strictTopology(
                CompiledRootProgram<AEKey> program) {
            AEKey root = program.root();
            synchronized (rootOutcomes) {
                Optional<Ae2StrictCraftingTopology> cached = strictTopologies.get(root);
                if (cached != null) {
                    return cached;
                }
            }
            Optional<Ae2StrictCraftingTopology> compiled = Optional.ofNullable(
                    Ae2StrictCraftingTopology.compile(this, program));
            synchronized (rootOutcomes) {
                CompiledRootProgram.Outcome<AEKey> current = rootOutcomes.get(root);
                if (current == null || current.program().orElse(null) != program) {
                    return compiled;
                }
                Optional<Ae2StrictCraftingTopology> raced = strictTopologies.get(root);
                if (raced != null) {
                    return raced;
                }
                strictTopologies.put(root, compiled);
                return compiled;
            }
        }

        @Override
        public Optional<Ae2StrictCraftingTopology> cachedStrictTopology(AEKey root) {
            synchronized (rootOutcomes) {
                Optional<Ae2StrictCraftingTopology> cached = strictTopologies.get(root);
                return cached == null || cached.isEmpty() ? Optional.empty() : cached;
            }
        }

        private static int rootProgramWeight(CompiledRootProgram.Outcome<AEKey> outcome) {
            return outcome.program()
                    .map(program -> Math.max(1, program.nodeCount()))
                    .orElse(1);
        }
    }
}
