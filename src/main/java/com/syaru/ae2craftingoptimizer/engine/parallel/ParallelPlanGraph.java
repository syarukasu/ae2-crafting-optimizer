package com.syaru.ae2craftingoptimizer.engine.parallel;

import com.syaru.ae2craftingoptimizer.engine.CompiledPattern;
import com.syaru.ae2craftingoptimizer.engine.PlanningCancellationToken;
import com.syaru.ae2craftingoptimizer.engine.PlanningCancelledException;
import com.syaru.ae2craftingoptimizer.engine.RootProgramFailure;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.LockSupport;

/** 並列探索後にcanonicalなトポロジカル順へ固定した、一件のimmutable Pattern DAG。 */
public final class ParallelPlanGraph<K> {
    /** 1.5.33のCompiledRootProgramと同じ、一root当たりの固定Node上限。 */
    private static final int MAXIMUM_NODES = 1_048_576;
    /** 1.5.33のCompiledRootProgramと同じ、一root当たりの固定入力辺上限。 */
    private static final int MAXIMUM_INPUT_EDGES = 4_194_304;
    /** 空queueを待つ間の50 microseconds。busy spinを避けつつ短い枝分岐をすぐstealする。 */
    private static final long IDLE_PARK_NANOS = 50_000L;

    private final long generation;
    private final K root;
    private final int rootIndex;
    private final List<K> keys;
    private final Map<K, Integer> indexByKey;
    private final Object[] patterns;
    private final long[] outputAmounts;
    private final boolean[] emittable;
    private final int[] inputOffsets;
    private final int[] alternativeOffsets;
    private final int[] childByEdge;
    private final long[] amountByEdge;
    private final int[][] incomingEdges;
    private final int[][] frontiers;
    private final Map<K, CompiledPattern<K>> patternsByOutput;

    private ParallelPlanGraph(
            long generation,
            K root,
            int rootIndex,
            List<K> keys,
            Map<K, Integer> indexByKey,
            Object[] patterns,
            long[] outputAmounts,
            boolean[] emittable,
            int[] inputOffsets,
            int[] alternativeOffsets,
            int[] childByEdge,
            long[] amountByEdge,
            int[][] incomingEdges,
            int[][] frontiers,
            Map<K, CompiledPattern<K>> patternsByOutput) {
        this.generation = generation;
        this.root = root;
        this.rootIndex = rootIndex;
        this.keys = List.copyOf(keys);
        this.indexByKey = Collections.unmodifiableMap(new LinkedHashMap<>(indexByKey));
        this.patterns = patterns;
        this.outputAmounts = outputAmounts;
        this.emittable = emittable;
        this.inputOffsets = inputOffsets;
        this.alternativeOffsets = alternativeOffsets;
        this.childByEdge = childByEdge;
        this.amountByEdge = amountByEdge;
        this.incomingEdges = incomingEdges;
        this.frontiers = frontiers;
        this.patternsByOutput = Collections.unmodifiableMap(new LinkedHashMap<>(patternsByOutput));
    }

    public static <K> CompletableFuture<BuildOutcome<K>> buildAsync(
            ParallelPlannerPool pool,
            ParallelPatternIndex<K> index,
            K root,
            PlanningCancellationToken cancellation) {
        Objects.requireNonNull(pool, "pool");
        Objects.requireNonNull(index, "index");
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(cancellation, "cancellation");
        BuildState<K> state = new BuildState<>(index, root, cancellation);
        pool.executeSessionWorkers(state::runWorker);
        return state.result;
    }

    public long generation() {
        return generation;
    }

    public K root() {
        return root;
    }

    public int rootIndex() {
        return rootIndex;
    }

    public int nodeCount() {
        return keys.size();
    }

    public K keyAt(int node) {
        return keys.get(node);
    }

    public int indexOf(K key) {
        return indexByKey.getOrDefault(key, -1);
    }

    @SuppressWarnings("unchecked")
    public CompiledPattern<K> patternAt(int node) {
        Objects.checkIndex(node, keys.size());
        return (CompiledPattern<K>) patterns[node];
    }

    public long outputAmountAt(int node) {
        Objects.checkIndex(node, keys.size());
        return outputAmounts[node];
    }

    public boolean isEmittableAt(int node) {
        Objects.checkIndex(node, keys.size());
        return emittable[node];
    }

    int firstInput(int node) {
        return inputOffsets[node];
    }

    int inputLimit(int node) {
        return inputOffsets[node + 1];
    }

    int firstAlternative(int slot) {
        return alternativeOffsets[slot];
    }

    int alternativeLimit(int slot) {
        return alternativeOffsets[slot + 1];
    }

    int childAtEdge(int edge) {
        return childByEdge[edge];
    }

    long amountAtEdge(int edge) {
        return amountByEdge[edge];
    }

    int[] incomingEdgesAt(int node) {
        return incomingEdges[node];
    }

    int frontierCount() {
        return frontiers.length;
    }

    int[] frontierAt(int frontier) {
        return frontiers[frontier];
    }

    int edgeCount() {
        return childByEdge.length;
    }

    int inputCountAt(int node) {
        return inputOffsets[node + 1] - inputOffsets[node];
    }

    boolean hasTreeExactByteShape() {
        for (int node = 0; node < keys.size(); node++) {
            if (node != rootIndex && incomingEdges[node].length != 1) {
                return false;
            }
            for (int slot = inputOffsets[node]; slot < inputOffsets[node + 1]; slot++) {
                if (alternativeOffsets[slot + 1] - alternativeOffsets[slot] != 1) {
                    return false;
                }
            }
        }
        return true;
    }

    Map<K, CompiledPattern<K>> patternsByOutput() {
        return patternsByOutput;
    }

    public record BuildMetrics(
            int expandedNodes,
            long duplicateDiscoveries,
            int maximumActiveWorkers,
            int[] nodesByWorker,
            long elapsedNanos) {
        public BuildMetrics {
            nodesByWorker = nodesByWorker.clone();
        }

        @Override
        public int[] nodesByWorker() {
            return nodesByWorker.clone();
        }

        public int workersUsed() {
            int used = 0;
            for (int count : nodesByWorker) {
                if (count > 0) {
                    used++;
                }
            }
            return used;
        }
    }

    public record BuildOutcome<K>(
            Optional<ParallelPlanGraph<K>> graph,
            RootProgramFailure failure,
            List<K> cyclePath,
            BuildMetrics metrics) {
        public BuildOutcome {
            Objects.requireNonNull(graph, "graph");
            Objects.requireNonNull(failure, "failure");
            cyclePath = List.copyOf(cyclePath);
            Objects.requireNonNull(metrics, "metrics");
            if (graph.isPresent() != (failure == RootProgramFailure.NONE)) {
                throw new IllegalArgumentException("build outcome must contain a graph or a failure");
            }
        }

        static <K> BuildOutcome<K> compiled(
                ParallelPlanGraph<K> graph,
                BuildMetrics metrics) {
            return new BuildOutcome<>(Optional.of(graph), RootProgramFailure.NONE, List.of(), metrics);
        }

        static <K> BuildOutcome<K> failed(
                RootProgramFailure failure,
                List<K> cyclePath,
                BuildMetrics metrics) {
            if (failure == RootProgramFailure.NONE) {
                throw new IllegalArgumentException("failure reason is required");
            }
            return new BuildOutcome<>(Optional.empty(), failure, cyclePath, metrics);
        }
    }

    public enum NodeState {
        NEW,
        EXPANDING,
        EXPANDED,
        FAILED
    }

    private static final class BuildState<K> {
        private final ParallelPatternIndex<K> index;
        private final K root;
        private final PlanningCancellationToken cancellation;
        private final ConcurrentHashMap<K, NodeRecord<K>> nodes = new ConcurrentHashMap<>();
        private final List<ConcurrentLinkedDeque<K>> queues;
        private final AtomicInteger outstanding = new AtomicInteger();
        private final AtomicInteger uniqueNodes = new AtomicInteger();
        private final AtomicInteger dispatchCursor = new AtomicInteger();
        private final AtomicInteger remainingWorkers = new AtomicInteger(ParallelPlannerPool.PARALLELISM);
        private final AtomicInteger activeWorkers = new AtomicInteger();
        private final AtomicInteger maximumActiveWorkers = new AtomicInteger();
        private final AtomicIntegerArray nodesByWorker =
                new AtomicIntegerArray(ParallelPlannerPool.PARALLELISM);
        private final AtomicReferenceArray<Thread> workerThreads =
                new AtomicReferenceArray<>(ParallelPlannerPool.PARALLELISM);
        private final AtomicBoolean done = new AtomicBoolean();
        private final AtomicReference<BuildFailure> expectedFailure = new AtomicReference<>();
        private final AtomicReference<Throwable> unexpectedFailure = new AtomicReference<>();
        private final LongAdder duplicateDiscoveries = new LongAdder();
        private final long startedNanos = System.nanoTime();
        private final CompletableFuture<BuildOutcome<K>> result = new CompletableFuture<>();

        private BuildState(
                ParallelPatternIndex<K> index,
                K root,
                PlanningCancellationToken cancellation) {
            this.index = index;
            this.root = root;
            this.cancellation = cancellation;
            List<ConcurrentLinkedDeque<K>> mutableQueues = new ArrayList<>(
                    ParallelPlannerPool.PARALLELISM);
            for (int worker = 0; worker < ParallelPlannerPool.PARALLELISM; worker++) {
                mutableQueues.add(new ConcurrentLinkedDeque<>());
            }
            this.queues = List.copyOf(mutableQueues);
            discover(root);
        }

        private void runWorker(int workerId) {
            workerThreads.set(workerId, Thread.currentThread());
            try {
                while (!done.get()) {
                    cancellation.checkpoint(uniqueNodes.get());
                    K key = pollWork(workerId);
                    if (key == null) {
                        if (outstanding.get() == 0) {
                            done.set(true);
                            break;
                        }
                        LockSupport.parkNanos(IDLE_PARK_NANOS);
                        continue;
                    }
                    expandClaimed(key, workerId);
                }
            } catch (PlanningCancelledException cancelled) {
                unexpectedFailure.compareAndSet(null, cancelled);
                done.set(true);
            } catch (BuildFailure failure) {
                expectedFailure.compareAndSet(null, failure);
                done.set(true);
            } catch (RuntimeException | Error failure) {
                unexpectedFailure.compareAndSet(null, failure);
                done.set(true);
            } finally {
                workerThreads.set(workerId, null);
                if (remainingWorkers.decrementAndGet() == 0) {
                    finish();
                }
            }
        }

        private K pollWork(int workerId) {
            K own = queues.get(workerId).pollLast();
            if (own != null) {
                return own;
            }
            for (int offset = 1; offset < ParallelPlannerPool.PARALLELISM; offset++) {
                int victim = (workerId + offset) % ParallelPlannerPool.PARALLELISM;
                K stolen = queues.get(victim).pollFirst();
                if (stolen != null) {
                    return stolen;
                }
            }
            return null;
        }

        private void expandClaimed(K key, int workerId) {
            NodeRecord<K> node = nodes.get(key);
            if (node == null || !node.state.compareAndSet(NodeState.NEW, NodeState.EXPANDING)) {
                throw new IllegalStateException("parallel graph work queue lost single-flight ownership");
            }
            int active = activeWorkers.incrementAndGet();
            maximumActiveWorkers.accumulateAndGet(active, Math::max);
            try {
                expand(node);
                node.state.set(NodeState.EXPANDED);
                nodesByWorker.incrementAndGet(workerId);
            } catch (RuntimeException failure) {
                node.state.set(NodeState.FAILED);
                throw failure;
            } finally {
                activeWorkers.decrementAndGet();
                if (outstanding.decrementAndGet() == 0) {
                    done.set(true);
                }
            }
        }

        private void expand(NodeRecord<K> node) {
            if (index.isIncomplete(node.key)) {
                throw new BuildFailure(RootProgramFailure.INCOMPLETE_PATTERN_SNAPSHOT);
            }
            if (index.isEmittable(node.key)) {
                node.expansion = NodeExpansion.emittable();
                return;
            }
            List<CompiledPattern<K>> candidates = index.candidatesFor(node.key);
            if (candidates.isEmpty()) {
                node.expansion = NodeExpansion.terminal();
                return;
            }
            if (candidates.size() != 1) {
                throw new BuildFailure(RootProgramFailure.MULTIPLE_PRODUCERS);
            }
            CompiledPattern<K> pattern = candidates.get(0);
            if (pattern.outputs().size() != 1 || pattern.outputAmount(node.key) <= 0L) {
                throw new BuildFailure(RootProgramFailure.MULTIPLE_OUTPUTS);
            }
            List<K> children = new ArrayList<>();
            for (CompiledPattern.InputSlot<K> slot : pattern.inputs()) {
                for (CompiledPattern.Stack<K> alternative : slot.alternatives()) {
                    K child = alternative.key();
                    children.add(child);
                    discover(child);
                }
            }
            node.expansion = NodeExpansion.pattern(pattern, children);
        }

        private void discover(K key) {
            NodeRecord<K> created = new NodeRecord<>(key);
            NodeRecord<K> previous = nodes.putIfAbsent(key, created);
            if (previous != null) {
                duplicateDiscoveries.increment();
                return;
            }
            int count = uniqueNodes.incrementAndGet();
            if (count > MAXIMUM_NODES) {
                throw new BuildFailure(RootProgramFailure.PROGRAM_TOO_LARGE);
            }
            outstanding.incrementAndGet();
            int targetWorker = Math.floorMod(
                    dispatchCursor.getAndIncrement(),
                    ParallelPlannerPool.PARALLELISM);
            queues.get(targetWorker).addLast(key);
            Thread targetThread = workerThreads.get(targetWorker);
            if (targetThread != null) {
                LockSupport.unpark(targetThread);
            }
        }

        private void finish() {
            BuildMetrics metrics = metrics();
            Throwable unexpected = unexpectedFailure.get();
            if (unexpected != null) {
                result.completeExceptionally(unexpected);
                return;
            }
            BuildFailure failure = expectedFailure.get();
            if (failure != null) {
                result.complete(BuildOutcome.failed(failure.reason, List.of(), metrics));
                return;
            }
            try {
                result.complete(canonicalize(index, root, nodes, metrics));
            } catch (BuildFailure canonicalFailure) {
                result.complete(BuildOutcome.failed(
                        canonicalFailure.reason,
                        List.of(),
                        metrics));
            } catch (RuntimeException | Error canonicalFailure) {
                result.completeExceptionally(canonicalFailure);
            }
        }

        private BuildMetrics metrics() {
            int[] counts = new int[ParallelPlannerPool.PARALLELISM];
            for (int worker = 0; worker < counts.length; worker++) {
                counts[worker] = nodesByWorker.get(worker);
            }
            return new BuildMetrics(
                    Arrays.stream(counts).sum(),
                    duplicateDiscoveries.sum(),
                    maximumActiveWorkers.get(),
                    counts,
                    System.nanoTime() - startedNanos);
        }
    }

    private static <K> BuildOutcome<K> canonicalize(
            ParallelPatternIndex<K> index,
            K root,
            Map<K, NodeRecord<K>> records,
            BuildMetrics metrics) {
        Map<K, Integer> discoveryOrder = canonicalDiscoveryOrder(root, records);
        Comparator<K> canonicalComparator = Comparator.comparingInt(
                key -> discoveryOrder.getOrDefault(key, Integer.MAX_VALUE));

        Map<K, Integer> indegree = new HashMap<>();
        Map<K, List<K>> uniqueChildrenByKey = new HashMap<>();
        for (K key : records.keySet()) {
            indegree.put(key, 0);
        }
        for (NodeRecord<K> record : records.values()) {
            requireExpanded(record);
            LinkedHashSet<K> unique = new LinkedHashSet<>(record.expansion.children);
            List<K> children = List.copyOf(unique);
            uniqueChildrenByKey.put(record.key, children);
            for (K child : children) {
                indegree.compute(child, (ignored, count) -> Math.addExact(count, 1));
            }
        }

        PriorityQueue<K> ready = new PriorityQueue<>(canonicalComparator);
        for (Map.Entry<K, Integer> entry : indegree.entrySet()) {
            if (entry.getValue() == 0) {
                ready.add(entry.getKey());
            }
        }
        List<K> order = new ArrayList<>(records.size());
        while (!ready.isEmpty()) {
            K key = ready.remove();
            order.add(key);
            for (K child : uniqueChildrenByKey.getOrDefault(key, List.of())) {
                int remaining = indegree.compute(child, (ignored, count) -> count - 1);
                if (remaining == 0) {
                    ready.add(child);
                }
            }
        }
        if (order.size() != records.size()) {
            return BuildOutcome.failed(
                    RootProgramFailure.CYCLE,
                    findCycle(records, canonicalComparator),
                    metrics);
        }
        return BuildOutcome.compiled(
                assemble(index.generation(), root, order, records, uniqueChildrenByKey),
                metrics);
    }

    private static <K> Map<K, Integer> canonicalDiscoveryOrder(
            K root,
            Map<K, NodeRecord<K>> records) {
        Map<K, Integer> order = new LinkedHashMap<>();
        Deque<K> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            K key = pending.pop();
            if (order.putIfAbsent(key, order.size()) != null) {
                continue;
            }
            NodeRecord<K> record = records.get(key);
            requireExpanded(record);
            List<K> children = record.expansion.children;
            for (int child = children.size() - 1; child >= 0; child--) {
                pending.push(children.get(child));
            }
        }
        return order;
    }

    private static <K> List<K> findCycle(
            Map<K, NodeRecord<K>> records,
            Comparator<? super K> order) {
        List<K> starts = new ArrayList<>(records.keySet());
        starts.sort(order);
        Map<K, Byte> colors = new HashMap<>();
        Map<K, K> parents = new HashMap<>();
        for (K start : starts) {
            if (colors.containsKey(start)) {
                continue;
            }
            Deque<CycleFrame<K>> stack = new ArrayDeque<>();
            colors.put(start, (byte) 1);
            stack.push(new CycleFrame<>(start, uniqueChildren(records.get(start)).iterator()));
            while (!stack.isEmpty()) {
                CycleFrame<K> frame = stack.peek();
                if (!frame.children.hasNext()) {
                    colors.put(frame.key, (byte) 2);
                    stack.pop();
                    continue;
                }
                K child = frame.children.next();
                byte color = colors.getOrDefault(child, (byte) 0);
                if (color == 0) {
                    parents.put(child, frame.key);
                    colors.put(child, (byte) 1);
                    stack.push(new CycleFrame<>(child, uniqueChildren(records.get(child)).iterator()));
                    continue;
                }
                if (color == 1) {
                    List<K> path = new ArrayList<>();
                    path.add(child);
                    K current = frame.key;
                    while (!current.equals(child)) {
                        path.add(current);
                        current = parents.get(current);
                        if (current == null) {
                            return List.of(child, frame.key, child);
                        }
                    }
                    Collections.reverse(path);
                    path.add(path.get(0));
                    return List.copyOf(path);
                }
            }
        }
        return List.of();
    }

    private static <K> Set<K> uniqueChildren(NodeRecord<K> record) {
        requireExpanded(record);
        return new LinkedHashSet<>(record.expansion.children);
    }

    @SuppressWarnings("unchecked")
    private static <K> ParallelPlanGraph<K> assemble(
            long generation,
            K root,
            List<K> order,
            Map<K, NodeRecord<K>> records,
            Map<K, List<K>> uniqueChildrenByKey) {
        Map<K, Integer> indexByKey = new LinkedHashMap<>();
        for (int node = 0; node < order.size(); node++) {
            indexByKey.put(order.get(node), node);
        }

        int slotCount = 0;
        int edgeCount = 0;
        for (K key : order) {
            CompiledPattern<K> pattern = records.get(key).expansion.pattern;
            if (pattern == null) {
                continue;
            }
            slotCount = Math.addExact(slotCount, pattern.inputs().size());
            for (CompiledPattern.InputSlot<K> slot : pattern.inputs()) {
                edgeCount = Math.addExact(edgeCount, slot.alternatives().size());
            }
            if (slotCount > MAXIMUM_INPUT_EDGES || edgeCount > MAXIMUM_INPUT_EDGES) {
                throw new BuildFailure(RootProgramFailure.PROGRAM_TOO_LARGE);
            }
        }

        int nodeCount = order.size();
        Object[] patterns = new Object[nodeCount];
        long[] outputAmounts = new long[nodeCount];
        boolean[] emittable = new boolean[nodeCount];
        int[] inputOffsets = new int[nodeCount + 1];
        int[] alternativeOffsets = new int[slotCount + 1];
        int[] childByEdge = new int[edgeCount];
        long[] amountByEdge = new long[edgeCount];
        int[] parentCounts = new int[nodeCount];
        int[] depths = new int[nodeCount];
        int maximumDepth = 0;
        Map<K, CompiledPattern<K>> selected = new LinkedHashMap<>();
        int slotCursor = 0;
        int edgeCursor = 0;

        for (int node = 0; node < nodeCount; node++) {
            K key = order.get(node);
            NodeExpansion<K> expansion = records.get(key).expansion;
            inputOffsets[node] = slotCursor;
            emittable[node] = expansion.emittable;
            CompiledPattern<K> pattern = expansion.pattern;
            if (pattern != null) {
                patterns[node] = pattern;
                outputAmounts[node] = pattern.outputAmount(key);
                selected.put(key, pattern);
                for (CompiledPattern.InputSlot<K> slot : pattern.inputs()) {
                    alternativeOffsets[slotCursor] = edgeCursor;
                    for (CompiledPattern.Stack<K> alternative : slot.alternatives()) {
                        Integer child = indexByKey.get(alternative.key());
                        if (child == null || child <= node) {
                            throw new BuildFailure(RootProgramFailure.CYCLE);
                        }
                        childByEdge[edgeCursor] = child;
                        amountByEdge[edgeCursor] = alternative.amount();
                        depths[child] = Math.max(depths[child], Math.addExact(depths[node], 1));
                        maximumDepth = Math.max(maximumDepth, depths[child]);
                        edgeCursor++;
                    }
                    slotCursor++;
                }
            }
            List<K> childKeys = uniqueChildrenByKey.getOrDefault(key, List.of());
            for (int childIndex = 0; childIndex < childKeys.size(); childIndex++) {
                int child = indexByKey.get(childKeys.get(childIndex));
                parentCounts[child] = Math.addExact(parentCounts[child], 1);
            }
        }
        inputOffsets[nodeCount] = slotCursor;
        alternativeOffsets[slotCount] = edgeCursor;

        int[] incomingCounts = new int[nodeCount];
        for (int edge = 0; edge < edgeCount; edge++) {
            int child = childByEdge[edge];
            incomingCounts[child] = Math.addExact(incomingCounts[child], 1);
        }
        int[][] incomingEdges = new int[nodeCount][];
        for (int node = 0; node < nodeCount; node++) {
            incomingEdges[node] = new int[incomingCounts[node]];
        }
        int[] incomingCursor = new int[nodeCount];
        for (int edge = 0; edge < edgeCount; edge++) {
            int child = childByEdge[edge];
            incomingEdges[child][incomingCursor[child]++] = edge;
        }

        int[] frontierSizes = new int[maximumDepth + 1];
        for (int depth : depths) {
            frontierSizes[depth] = Math.addExact(frontierSizes[depth], 1);
        }
        int[][] frontiers = new int[frontierSizes.length][];
        for (int frontier = 0; frontier < frontiers.length; frontier++) {
            frontiers[frontier] = new int[frontierSizes[frontier]];
        }
        int[] frontierCursors = new int[frontiers.length];
        // nodeはcanonical順なので、各frontier内も同じ決定順を保つ。
        for (int node = 0; node < nodeCount; node++) {
            int frontier = depths[node];
            frontiers[frontier][frontierCursors[frontier]++] = node;
        }

        Integer rootIndex = indexByKey.get(root);
        if (rootIndex == null || parentCounts[rootIndex] != 0) {
            throw new BuildFailure(RootProgramFailure.CYCLE);
        }
        return new ParallelPlanGraph<>(
                generation,
                root,
                rootIndex,
                order,
                indexByKey,
                patterns,
                outputAmounts,
                emittable,
                inputOffsets,
                alternativeOffsets,
                childByEdge,
                amountByEdge,
                incomingEdges,
                frontiers,
                selected);
    }

    private static void requireExpanded(NodeRecord<?> record) {
        if (record == null
                || record.state.get() != NodeState.EXPANDED
                || record.expansion == null) {
            throw new IllegalStateException("parallel graph contains an unpublished node expansion");
        }
    }

    private static final class NodeRecord<K> {
        private final K key;
        private final AtomicReference<NodeState> state = new AtomicReference<>(NodeState.NEW);
        private volatile NodeExpansion<K> expansion;

        private NodeRecord(K key) {
            this.key = Objects.requireNonNull(key, "key");
        }
    }

    private static final class NodeExpansion<K> {
        private final boolean emittable;
        private final CompiledPattern<K> pattern;
        private final List<K> children;

        private NodeExpansion(
                boolean emittable,
                CompiledPattern<K> pattern,
                List<K> children) {
            this.emittable = emittable;
            this.pattern = pattern;
            this.children = List.copyOf(children);
        }

        private static <K> NodeExpansion<K> emittable() {
            return new NodeExpansion<>(true, null, List.of());
        }

        private static <K> NodeExpansion<K> terminal() {
            return new NodeExpansion<>(false, null, List.of());
        }

        private static <K> NodeExpansion<K> pattern(
                CompiledPattern<K> pattern,
                List<K> children) {
            return new NodeExpansion<>(false, pattern, children);
        }
    }

    private static final class BuildFailure extends RuntimeException {
        private final RootProgramFailure reason;

        private BuildFailure(RootProgramFailure reason) {
            super(reason.name());
            this.reason = Objects.requireNonNull(reason, "reason");
        }
    }

    private static final class CycleFrame<K> {
        private final K key;
        private final Iterator<K> children;

        private CycleFrame(K key, Iterator<K> children) {
            this.key = key;
            this.children = children;
        }
    }
}
