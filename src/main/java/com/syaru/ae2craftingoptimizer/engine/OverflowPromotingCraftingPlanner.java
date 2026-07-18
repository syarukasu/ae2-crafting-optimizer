package com.syaru.ae2craftingoptimizer.engine;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * まず低コストなlong経路を試し、overflowした注文だけBigInteger経路で最初から再計算する。
 * 途中まで作ったlong結果は再利用しないため、昇格による二重計上は発生しない。
 */
public final class OverflowPromotingCraftingPlanner<K> {
    private final int maximumBits;

    public OverflowPromotingCraftingPlanner() {
        this(BigCountMath.HARD_MAXIMUM_BITS);
    }

    public OverflowPromotingCraftingPlanner(int maximumBits) {
        BigCountMath.requireMaximumBits(BigInteger.ZERO, "planner maximum", maximumBits);
        this.maximumBits = maximumBits;
    }

    public Result<K> plan(
            CompiledCraftingGraph<K> graph,
            K requestedKey,
            BigInteger requestedAmount,
            Map<K, BigInteger> inventory,
            Set<K> emittable) {
        return plan(graph, requestedKey, requestedAmount, inventory, emittable, PlanningGuard.none());
    }

    public Result<K> plan(
            CompiledCraftingGraph<K> graph,
            K requestedKey,
            BigInteger requestedAmount,
            Map<K, BigInteger> inventory,
            Set<K> emittable,
            PlanningGuard guard) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(requestedKey, "requestedKey");
        BigCountMath.requireMaximumBits(requestedAmount, "request", maximumBits);
        Map<K, BigInteger> snapshot = immutableInventory(inventory, maximumBits);
        SymbolicCraftingPlanner<K> symbolic = new SymbolicCraftingPlanner<>();
        if (requestedAmount.bitLength() <= 63 && allFitLong(snapshot)) {
            try {
                Map<K, Long> longs = new LinkedHashMap<>();
                snapshot.forEach((key, value) -> longs.put(key, value.longValueExact()));
                var symbolicPlan = symbolic.tryPlanLong(
                                graph,
                                requestedKey,
                                requestedAmount.longValueExact(),
                                longs,
                                emittable,
                                guard);
                if (symbolicPlan.isPresent()) {
                    return new LongResult<>(symbolicPlan.get(), true);
                }
                return new LongResult<>(new LongCraftingPlanner<K>().plan(
                        graph,
                        requestedKey,
                        requestedAmount.longValueExact(),
                        longs,
                        emittable,
                        guard), false);
            } catch (ArithmeticException overflow) {
                // 不変SnapshotからBigIntegerで再計算し、途中までのlong計算結果は絶対に混ぜない。
            }
        }
        var symbolicPlan = symbolic.tryPlanBig(
                        graph,
                        requestedKey,
                        requestedAmount,
                        snapshot,
                        emittable,
                        guard,
                        maximumBits);
        if (symbolicPlan.isPresent()) {
            return new BigResult<>(symbolicPlan.get(), true);
        }
        return new BigResult<>(new BigCraftingPlanner<K>(maximumBits).plan(
                graph, requestedKey, requestedAmount, snapshot, emittable, guard), false);
    }

    public Result<K> plan(
            CompiledCraftingGraph<K> graph,
            K requestedKey,
            BigInteger requestedAmount,
            Map<K, BigInteger> inventory) {
        return plan(graph, requestedKey, requestedAmount, inventory, Set.of());
    }

    private static <K> Map<K, BigInteger> immutableInventory(
            Map<K, BigInteger> inventory,
            int maximumBits) {
        Map<K, BigInteger> copy = new LinkedHashMap<>();
        Objects.requireNonNull(inventory, "inventory").forEach((key, value) -> {
            Objects.requireNonNull(key, "inventory key");
            copy.put(key, BigCountMath.requireMaximumBits(value, "inventory", maximumBits));
        });
        return Map.copyOf(copy);
    }

    private static boolean allFitLong(Map<?, BigInteger> values) {
        return values.values().stream().allMatch(value -> value.bitLength() <= 63);
    }

    public sealed interface Result<K> permits LongResult, BigResult {
        boolean usesBigInteger();

        boolean craftable();

        /** True only for the graph subset whose arithmetic result is proven deterministic. */
        boolean provenEquivalent();
    }

    public record LongResult<K>(LongCraftingPlan<K> plan, boolean provenEquivalent) implements Result<K> {
        public LongResult {
            Objects.requireNonNull(plan, "plan");
        }

        @Override
        public boolean usesBigInteger() {
            return false;
        }

        @Override
        public boolean craftable() {
            return plan.craftable();
        }
    }

    public record BigResult<K>(BigCraftingPlan<K> plan, boolean provenEquivalent) implements Result<K> {
        public BigResult {
            Objects.requireNonNull(plan, "plan");
        }

        @Override
        public boolean usesBigInteger() {
            return true;
        }

        @Override
        public boolean craftable() {
            return plan.craftable();
        }
    }
}
