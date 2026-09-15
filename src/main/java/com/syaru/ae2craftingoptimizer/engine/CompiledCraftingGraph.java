package com.syaru.ae2craftingoptimizer.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class CompiledCraftingGraph<K> {
    private final long generation;
    private final List<CompiledPattern<K>> patterns;
    private final Map<K, List<CompiledPattern<K>>> byOutput;
    private final Map<K, Integer> componentByKey;
    private final Set<Integer> cyclicComponents;

    private CompiledCraftingGraph(
            long generation,
            Collection<CompiledPattern<K>> source,
            PlanningGuard guard) {
        if (generation < 0L) {
            throw new IllegalArgumentException("generation must not be negative");
        }
        this.generation = generation;
        this.patterns = List.copyOf(Objects.requireNonNull(source, "source"));
        Map<K, List<CompiledPattern<K>>> mutableIndex = new LinkedHashMap<>();
        Map<K, Set<K>> edges = new LinkedHashMap<>();
        int indexedPatterns = 0;
        for (CompiledPattern<K> pattern : patterns) {
            guard.checkpoint(++indexedPatterns);
            Objects.requireNonNull(pattern, "pattern");
            for (K output : pattern.outputs().keySet()) {
                mutableIndex.computeIfAbsent(output, ignored -> new ArrayList<>()).add(pattern);
                Set<K> dependencies = edges.computeIfAbsent(output, ignored -> new LinkedHashSet<>());
                for (CompiledPattern.InputSlot<K> slot : pattern.inputs()) {
                    for (CompiledPattern.Stack<K> alternative : slot.alternatives()) {
                        dependencies.add(alternative.key());
                        edges.computeIfAbsent(alternative.key(), ignored -> new LinkedHashSet<>());
                    }
                }
            }
        }
        Map<K, List<CompiledPattern<K>>> frozenIndex = new LinkedHashMap<>();
        mutableIndex.forEach((key, value) -> frozenIndex.put(key, List.copyOf(value)));
        this.byOutput = Collections.unmodifiableMap(frozenIndex);

        Components<K> components = Components.find(edges, guard);
        this.componentByKey = components.componentByKey();
        this.cyclicComponents = components.cyclicComponents();
    }

    public static <K> CompiledCraftingGraph<K> compile(
            long generation, Collection<CompiledPattern<K>> patterns) {
        return compile(generation, patterns, PlanningGuard.none());
    }

    static <K> CompiledCraftingGraph<K> compile(
            long generation,
            Collection<CompiledPattern<K>> patterns,
            PlanningGuard guard) {
        return new CompiledCraftingGraph<>(generation, patterns, guard);
    }

    public long generation() {
        return generation;
    }

    public List<CompiledPattern<K>> patterns() {
        return patterns;
    }

    public List<CompiledPattern<K>> patternsFor(K output) {
        return byOutput.getOrDefault(output, List.of());
    }

    public boolean isCyclic(K key) {
        Integer component = componentByKey.get(key);
        return component != null && cyclicComponents.contains(component);
    }

    public boolean sharesCycle(K first, K second) {
        Integer firstComponent = componentByKey.get(first);
        return firstComponent != null
                && firstComponent.equals(componentByKey.get(second))
                && cyclicComponents.contains(firstComponent);
    }

    public int stronglyConnectedComponentCount() {
        return new HashSet<>(componentByKey.values()).size();
    }

    private record Components<K>(Map<K, Integer> componentByKey, Set<Integer> cyclicComponents) {
        private static <K> Components<K> find(
                Map<K, Set<K>> edges,
                PlanningGuard guard) {
            Map<K, Frame<K>> discovered = new HashMap<>();
            Map<K, Integer> componentByKey = new HashMap<>();
            Set<Integer> cyclicComponents = new HashSet<>();
            Deque<Frame<K>> traversal = new ArrayDeque<>();
            Deque<Frame<K>> unresolved = new ArrayDeque<>();
            int component = 0;

            // Issue #156: 反転Graphを作らず、非再帰Tarjan法で全連結成分を一巡する。
            for (K start : edges.keySet()) {
                // 別の始点から既に到達した共有依存は再展開しない。
                if (discovered.containsKey(start)) {
                    continue;
                }
                var first = new Frame<>(start, edges.get(start).iterator(), discovered.size());
                discovered.put(start, first);
                traversal.push(first);
                unresolved.push(first);
                // 深いchainもJavaのcall stackを使わず、入力辺を一度ずつ調べる。
                while (!traversal.isEmpty()) {
                    guard.checkpoint(discovered.size());
                    Frame<K> current = traversal.peek();
                    // 未処理辺が残る間は、子の探索または後退辺の判定を行う。
                    if (current.dependencies.hasNext()) {
                        K dependency = current.dependencies.next();
                        Frame<K> child = discovered.get(dependency);
                        // 未訪問ノードの展開を、このDFS frameが一度だけ所有する。
                        if (child == null) {
                            child = new Frame<>(dependency, edges.get(dependency).iterator(), discovered.size());
                            discovered.put(dependency, child);
                            traversal.push(child);
                            unresolved.push(child);
                        // 確定済みSCCへの片方向辺を、同一cycleへの後退辺と混同しない。
                        } else if (child.unresolved) {
                            current.lowest = Math.min(current.lowest, child.index);
                        }
                        continue;
                    }
                    traversal.pop();
                    // 子の探索完了後にだけlowlinkを親へ伝え、兄弟の完了順には依存させない。
                    if (!traversal.isEmpty()) {
                        Frame<K> parent = traversal.peek();
                        parent.lowest = Math.min(parent.lowest, current.lowest);
                    }
                    // SCCの先頭へ戻るまでは、未確定ノードを同じstack上へ保持する。
                    if (current.lowest != current.index) {
                        continue;
                    }
                    int members = 0;
                    Frame<K> member;
                    // SCCの全要素を一度だけ確定する。自己循環は下の辺検査で区別する。
                    do {
                        guard.checkpoint(discovered.size());
                        member = unresolved.pop();
                        member.unresolved = false;
                        componentByKey.put(member.key, component);
                        members++;
                    } while (member != current);
                    // 複数ノードのSCC、または自分自身への辺だけをcycleと判定する。
                    if (members > 1 || edges.get(current.key).contains(current.key)) {
                        cyclicComponents.add(component);
                    }
                    component++;
                }
            }
            return new Components<>(Map.copyOf(componentByKey), Set.copyOf(cyclicComponents));
        }

        private static final class Frame<K> {
            private final K key;
            private final Iterator<K> dependencies;
            private final int index;
            private int lowest;
            private boolean unresolved = true;

            private Frame(K key, Iterator<K> dependencies, int index) {
                this.key = key;
                this.dependencies = dependencies;
                this.index = index;
                this.lowest = index;
            }
        }
    }
}
