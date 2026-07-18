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

    private CompiledCraftingGraph(long generation, Collection<CompiledPattern<K>> source) {
        if (generation < 0L) {
            throw new IllegalArgumentException("generation must not be negative");
        }
        this.generation = generation;
        this.patterns = List.copyOf(Objects.requireNonNull(source, "source"));
        Map<K, List<CompiledPattern<K>>> mutableIndex = new LinkedHashMap<>();
        Map<K, Set<K>> edges = new LinkedHashMap<>();
        for (CompiledPattern<K> pattern : patterns) {
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

        Components<K> components = Components.find(edges);
        this.componentByKey = components.componentByKey();
        this.cyclicComponents = components.cyclicComponents();
    }

    public static <K> CompiledCraftingGraph<K> compile(
            long generation, Collection<CompiledPattern<K>> patterns) {
        return new CompiledCraftingGraph<>(generation, patterns);
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
        private static <K> Components<K> find(Map<K, Set<K>> edges) {
            List<K> finishOrder = finishingOrder(edges);
            Map<K, Set<K>> reversed = reverse(edges);
            Map<K, Integer> componentByKey = new HashMap<>();
            Set<Integer> cyclicComponents = new HashSet<>();
            int component = 0;

            for (int index = finishOrder.size() - 1; index >= 0; index--) {
                K start = finishOrder.get(index);
                if (componentByKey.containsKey(start)) {
                    continue;
                }
                List<K> members = new ArrayList<>();
                Deque<K> pending = new ArrayDeque<>();
                pending.push(start);
                componentByKey.put(start, component);
                while (!pending.isEmpty()) {
                    K key = pending.pop();
                    members.add(key);
                    for (K dependency : reversed.getOrDefault(key, Set.of())) {
                        if (!componentByKey.containsKey(dependency)) {
                            componentByKey.put(dependency, component);
                            pending.push(dependency);
                        }
                    }
                }
                if (members.size() > 1
                        || edges.getOrDefault(start, Set.of()).contains(start)) {
                    cyclicComponents.add(component);
                }
                component++;
            }
            return new Components<>(Map.copyOf(componentByKey), Set.copyOf(cyclicComponents));
        }

        private static <K> List<K> finishingOrder(Map<K, Set<K>> edges) {
            List<K> order = new ArrayList<>(edges.size());
            Set<K> visited = new HashSet<>();
            for (K start : edges.keySet()) {
                if (!visited.add(start)) {
                    continue;
                }
                Deque<Frame<K>> stack = new ArrayDeque<>();
                stack.push(new Frame<>(start, edges.getOrDefault(start, Set.of()).iterator()));
                while (!stack.isEmpty()) {
                    Frame<K> frame = stack.peek();
                    if (frame.dependencies().hasNext()) {
                        K dependency = frame.dependencies().next();
                        if (visited.add(dependency)) {
                            stack.push(new Frame<>(
                                    dependency,
                                    edges.getOrDefault(dependency, Set.of()).iterator()));
                        }
                    } else {
                        order.add(frame.key());
                        stack.pop();
                    }
                }
            }
            return order;
        }

        private static <K> Map<K, Set<K>> reverse(Map<K, Set<K>> edges) {
            Map<K, Set<K>> reversed = new LinkedHashMap<>();
            for (K key : edges.keySet()) {
                reversed.computeIfAbsent(key, ignored -> new LinkedHashSet<>());
                for (K dependency : edges.getOrDefault(key, Set.of())) {
                    reversed.computeIfAbsent(dependency, ignored -> new LinkedHashSet<>()).add(key);
                }
            }
            return reversed;
        }

        private record Frame<K>(K key, Iterator<K> dependencies) {
        }
    }
}
