package com.syaru.ae2craftingoptimizer.engine;

import java.math.BigInteger;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;

/** Immutable planning session with cancellation and generation revalidation. */
public final class CompiledPlanningSession<K> {
    private final CompiledCraftingGraph<K> graph;
    private final Map<K, BigInteger> inventory;
    private final Set<K> emittable;
    private final PlanningGenerationSnapshot generations;
    private final LongSupplier currentPatternGeneration;
    private final LongSupplier currentInventoryGeneration;
    private final LongSupplier currentRecipeGeneration;
    private final PlanningCancellationToken cancellation;
    private final int maximumBits;

    public CompiledPlanningSession(
            CompiledCraftingGraph<K> graph,
            Map<K, BigInteger> inventory,
            Set<K> emittable,
            PlanningGenerationSnapshot generations,
            LongSupplier currentPatternGeneration,
            LongSupplier currentInventoryGeneration,
            LongSupplier currentRecipeGeneration,
            PlanningCancellationToken cancellation) {
        this(
                graph,
                inventory,
                emittable,
                generations,
                currentPatternGeneration,
                currentInventoryGeneration,
                currentRecipeGeneration,
                cancellation,
                BigCountMath.HARD_MAXIMUM_BITS);
    }

    public CompiledPlanningSession(
            CompiledCraftingGraph<K> graph,
            Map<K, BigInteger> inventory,
            Set<K> emittable,
            PlanningGenerationSnapshot generations,
            LongSupplier currentPatternGeneration,
            LongSupplier currentInventoryGeneration,
            LongSupplier currentRecipeGeneration,
            PlanningCancellationToken cancellation,
            int maximumBits) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.inventory = Map.copyOf(Objects.requireNonNull(inventory, "inventory"));
        this.emittable = Set.copyOf(Objects.requireNonNull(emittable, "emittable"));
        this.generations = Objects.requireNonNull(generations, "generations");
        this.currentPatternGeneration = Objects.requireNonNull(currentPatternGeneration, "currentPatternGeneration");
        this.currentInventoryGeneration = Objects.requireNonNull(currentInventoryGeneration, "currentInventoryGeneration");
        this.currentRecipeGeneration = Objects.requireNonNull(currentRecipeGeneration, "currentRecipeGeneration");
        this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
        BigCountMath.requireMaximumBits(BigInteger.ZERO, "planning session maximum", maximumBits);
        this.maximumBits = maximumBits;
        if (graph.generation() != generations.patternGeneration()) {
            throw new IllegalArgumentException("compiled graph and pattern generation differ");
        }
    }

    public OverflowPromotingCraftingPlanner.Result<K> plan(K requestedKey, BigInteger requestedAmount) {
        PlanningGuard guard = generations.guard(
                currentPatternGeneration,
                currentInventoryGeneration,
                currentRecipeGeneration,
                cancellation);
        guard.checkpoint(0);
        var result = new OverflowPromotingCraftingPlanner<K>(maximumBits).plan(
                graph, requestedKey, requestedAmount, inventory, emittable, guard);
        guard.checkpoint(Integer.MAX_VALUE);
        return result;
    }

    public void cancel() {
        cancellation.cancel();
    }

    public PlanningGenerationSnapshot generations() {
        return generations;
    }
}
