package com.syaru.ae2craftingoptimizer.engine;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 数式だけで証明できる線形レシピを一回の走査で計算する高速Planner。
 * 出力ごとにPatternが一つ、各入力候補が一つ、循環なし、という条件を外れた時点で
 * Optional.emptyを返し、通常PlannerまたはAE2標準計算へFallbackする。
 */
public final class SymbolicCraftingPlanner<K> {
    private static final int MAX_ROOTS_PER_GRAPH = 262_144;
    private static final Map<CompiledCraftingGraph<?>, Map<Object, Optional<Topology<?>>>> TOPOLOGIES =
            Collections.synchronizedMap(new WeakHashMap<>());

    public Optional<LongCraftingPlan<K>> tryPlanLong(
            CompiledCraftingGraph<K> graph,
            K requestedKey,
            long requestedAmount,
            Map<K, Long> inventory,
            Set<K> emittable,
            PlanningGuard guard) {
        CheckedLongMath.requireNonNegative(requestedAmount, "symbolic/request");
        Topology<K> topology = topology(graph, requestedKey);
        if (topology == null) {
            return Optional.empty();
        }
        Map<K, Long> demand = new LinkedHashMap<>();
        Map<K, Long> stock = checkedLongInventory(inventory);
        Map<String, Long> patterns = new LinkedHashMap<>();
        Map<K, Long> used = new LinkedHashMap<>();
        Map<K, Long> emitted = new LinkedHashMap<>();
        Map<K, Long> missing = new LinkedHashMap<>();
        if (requestedAmount > 0L) {
            demand.put(requestedKey, requestedAmount);
        }

        int checkpoints = 0;
        for (K key : topology.order()) {
            guard.checkpoint(++checkpoints);
            long required = demand.getOrDefault(key, 0L);
            if (required == 0L) {
                continue;
            }
            long present = stock.getOrDefault(key, 0L);
            long taken = Math.min(required, present);
            if (taken > 0L) {
                CheckedLongMath.merge(used, key, taken, "symbolic/inventory");
                long remainingStock = present - taken;
                if (remainingStock == 0L) {
                    stock.remove(key);
                } else {
                    stock.put(key, remainingStock);
                }
            }
            long deficit = required - taken;
            if (deficit == 0L) {
                continue;
            }
            if (emittable.contains(key)) {
                CheckedLongMath.merge(emitted, key, deficit, "symbolic/emitter");
                continue;
            }
            CompiledPattern<K> pattern = topology.patterns().get(key);
            if (pattern == null) {
                CheckedLongMath.merge(missing, key, deficit, "symbolic/missing");
                continue;
            }
            long executions = CheckedLongMath.ceilDiv(
                    deficit, pattern.outputAmount(key), "symbolic/executions/" + key);
            CheckedLongMath.merge(patterns, pattern.id(), executions, "symbolic/pattern/" + pattern.id());
            for (int slotIndex = 0; slotIndex < pattern.inputs().size(); slotIndex++) {
                CompiledPattern.Stack<K> input = pattern.inputs().get(slotIndex).alternatives().get(0);
                long amount = CheckedLongMath.multiply(
                        input.amount(), executions, "symbolic/input/" + pattern.id() + '/' + slotIndex);
                CheckedLongMath.merge(demand, input.key(), amount, "symbolic/demand/" + input.key());
            }
        }
        return Optional.of(new LongCraftingPlan<>(
                requestedKey, requestedAmount, patterns, used, emitted, missing));
    }

    public Optional<BigCraftingPlan<K>> tryPlanBig(
            CompiledCraftingGraph<K> graph,
            K requestedKey,
            BigInteger requestedAmount,
            Map<K, BigInteger> inventory,
            Set<K> emittable,
            PlanningGuard guard) {
        return tryPlanBig(
                graph,
                requestedKey,
                requestedAmount,
                inventory,
                emittable,
                guard,
                BigCountMath.HARD_MAXIMUM_BITS);
    }

    public Optional<BigCraftingPlan<K>> tryPlanBig(
            CompiledCraftingGraph<K> graph,
            K requestedKey,
            BigInteger requestedAmount,
            Map<K, BigInteger> inventory,
            Set<K> emittable,
            PlanningGuard guard,
            int maximumBits) {
        BigCountMath.requireMaximumBits(requestedAmount, "symbolic/request", maximumBits);
        Topology<K> topology = topology(graph, requestedKey);
        if (topology == null) {
            return Optional.empty();
        }
        Map<K, BigInteger> demand = new LinkedHashMap<>();
        Map<K, BigInteger> stock = checkedBigInventory(inventory, maximumBits);
        Map<String, BigInteger> patterns = new LinkedHashMap<>();
        Map<K, BigInteger> used = new LinkedHashMap<>();
        Map<K, BigInteger> emitted = new LinkedHashMap<>();
        Map<K, BigInteger> missing = new LinkedHashMap<>();
        if (requestedAmount.signum() != 0) {
            demand.put(requestedKey, requestedAmount);
        }

        int checkpoints = 0;
        for (K key : topology.order()) {
            guard.checkpoint(++checkpoints);
            BigInteger required = demand.getOrDefault(key, BigInteger.ZERO);
            if (required.signum() == 0) {
                continue;
            }
            BigInteger present = stock.getOrDefault(key, BigInteger.ZERO);
            BigInteger taken = required.min(present);
            BigCountMath.merge(used, key, taken, "symbolic/inventory", maximumBits);
            BigInteger remainingStock = present.subtract(taken);
            if (remainingStock.signum() == 0) {
                stock.remove(key);
            } else {
                stock.put(key, remainingStock);
            }
            BigInteger deficit = required.subtract(taken);
            if (deficit.signum() == 0) {
                continue;
            }
            if (emittable.contains(key)) {
                BigCountMath.merge(emitted, key, deficit, "symbolic/emitter", maximumBits);
                continue;
            }
            CompiledPattern<K> pattern = topology.patterns().get(key);
            if (pattern == null) {
                BigCountMath.merge(missing, key, deficit, "symbolic/missing", maximumBits);
                continue;
            }
            BigInteger executions = BigCountMath.ceilDiv(
                    deficit, BigInteger.valueOf(pattern.outputAmount(key)), "symbolic/executions/" + key);
            BigCountMath.requireMaximumBits(
                    executions, "symbolic/executions/" + key, maximumBits);
            BigCountMath.merge(
                    patterns,
                    pattern.id(),
                    executions,
                    "symbolic/pattern/" + pattern.id(),
                    maximumBits);
            for (CompiledPattern.InputSlot<K> slot : pattern.inputs()) {
                CompiledPattern.Stack<K> input = slot.alternatives().get(0);
                BigInteger requiredInput = BigCountMath.multiply(
                        BigInteger.valueOf(input.amount()),
                        executions,
                        "symbolic/demand/" + input.key(),
                        maximumBits);
                BigCountMath.merge(
                        demand,
                        input.key(),
                        requiredInput,
                        "symbolic/demand/" + input.key(),
                        maximumBits);
            }
        }
        return Optional.of(new BigCraftingPlan<>(
                requestedKey,
                requestedAmount,
                patterns,
                used,
                emitted,
                missing,
                checkpoints));
    }

    private Topology<K> topology(CompiledCraftingGraph<K> graph, K root) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(root, "root");
        synchronized (TOPOLOGIES) {
            Map<Object, Optional<Topology<?>>> byRoot = TOPOLOGIES.computeIfAbsent(
                    graph, ignored -> new LinkedHashMap<>());
            Optional<Topology<?>> cached = byRoot.get(root);
            if (cached != null) {
                @SuppressWarnings("unchecked")
                Topology<K> typed = (Topology<K>) cached.orElse(null);
                return typed;
            }
        }
        Topology<K> compiled = compileTopology(graph, root);
        synchronized (TOPOLOGIES) {
            Map<Object, Optional<Topology<?>>> byRoot = TOPOLOGIES.computeIfAbsent(
                    graph, ignored -> new LinkedHashMap<>());
            Optional<Topology<?>> raced = byRoot.get(root);
            if (raced != null) {
                @SuppressWarnings("unchecked")
                Topology<K> typed = (Topology<K>) raced.orElse(null);
                return typed;
            }
            if (byRoot.size() >= MAX_ROOTS_PER_GRAPH) {
                byRoot.clear();
            }
            byRoot.put(root, Optional.ofNullable(compiled));
            return compiled;
        }
    }

    private Topology<K> compileTopology(CompiledCraftingGraph<K> graph, K root) {
        Map<K, CompiledPattern<K>> selected = new LinkedHashMap<>();
        Map<K, Set<K>> dependencies = new LinkedHashMap<>();
        ArrayDeque<K> discover = new ArrayDeque<>();
        Set<K> reachable = new LinkedHashSet<>();
        discover.push(root);
        while (!discover.isEmpty()) {
            K key = discover.pop();
            if (!reachable.add(key) || graph.isCyclic(key)) {
                if (graph.isCyclic(key)) {
                    return null;
                }
                continue;
            }
            List<CompiledPattern<K>> candidates = graph.patternsFor(key);
            if (candidates.isEmpty()) {
                dependencies.put(key, Set.of());
                continue;
            }
            if (candidates.size() != 1) {
                return null;
            }
            CompiledPattern<K> pattern = candidates.get(0);
            if (pattern.outputs().size() != 1 || pattern.outputAmount(key) <= 0L) {
                return null;
            }
            Set<K> children = new LinkedHashSet<>();
            for (CompiledPattern.InputSlot<K> slot : pattern.inputs()) {
                if (slot.alternatives().size() != 1) {
                    return null;
                }
                K child = slot.alternatives().get(0).key();
                children.add(child);
                discover.push(child);
            }
            selected.put(key, pattern);
            dependencies.put(key, Set.copyOf(children));
        }

        Map<K, Integer> indegree = new HashMap<>();
        reachable.forEach(key -> indegree.put(key, 0));
        dependencies.forEach((parent, children) -> children.forEach(child ->
                indegree.merge(child, 1, Math::addExact)));
        Queue<K> ready = new ArrayDeque<>();
        reachable.stream().filter(key -> indegree.get(key) == 0).forEach(ready::add);
        List<K> order = new ArrayList<>(reachable.size());
        while (!ready.isEmpty()) {
            K key = ready.remove();
            order.add(key);
            for (K child : dependencies.getOrDefault(key, Set.of())) {
                int remaining = indegree.compute(child, (ignored, value) -> value - 1);
                if (remaining == 0) {
                    ready.add(child);
                }
            }
        }
        return order.size() == reachable.size()
                ? new Topology<>(List.copyOf(order), Map.copyOf(selected))
                : null;
    }

    public static void clearTopologyCache() {
        synchronized (TOPOLOGIES) {
            TOPOLOGIES.clear();
        }
    }

    private static <K> Map<K, Long> checkedLongInventory(Map<K, Long> source) {
        Map<K, Long> result = new LinkedHashMap<>();
        Objects.requireNonNull(source, "inventory").forEach((key, amount) -> {
            Objects.requireNonNull(key, "inventory key");
            if (amount == null || amount < 0L) {
                throw new IllegalArgumentException("inventory amounts must not be negative");
            }
            if (amount > 0L) {
                result.put(key, amount);
            }
        });
        return result;
    }

    private static <K> Map<K, BigInteger> checkedBigInventory(
            Map<K, BigInteger> source,
            int maximumBits) {
        Map<K, BigInteger> result = new LinkedHashMap<>();
        Objects.requireNonNull(source, "inventory").forEach((key, amount) -> {
            Objects.requireNonNull(key, "inventory key");
            BigCountMath.requireMaximumBits(amount, "inventory", maximumBits);
            if (amount.signum() != 0) {
                result.put(key, amount);
            }
        });
        return result;
    }

    private record Topology<K>(List<K> order, Map<K, CompiledPattern<K>> patterns) {
    }
}
