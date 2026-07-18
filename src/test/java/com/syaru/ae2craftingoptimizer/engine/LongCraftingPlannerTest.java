package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LongCraftingPlannerTest {
    private final LongCraftingPlanner<String> planner = new LongCraftingPlanner<>();

    @Test
    void plansNestedCraftAndConsumesSnapshotInventory() {
        var planks = pattern("planks", "log", 1L, "plank", 4L);
        var sticks = pattern("sticks", "plank", 2L, "stick", 4L);
        var graph = CompiledCraftingGraph.compile(1L, List.of(planks, sticks));

        LongCraftingPlan<String> plan = planner.plan(graph, "stick", 8L, Map.of("log", 1L));

        assertTrue(plan.craftable());
        assertEquals(Map.of("sticks", 2L, "planks", 1L), plan.patternExecutions());
        assertEquals(Map.of("log", 1L), plan.usedInventory());
    }

    @Test
    void reportsFirstUncraftableLeaf() {
        var plate = pattern("plate", "ingot", 2L, "plate", 1L);
        var graph = CompiledCraftingGraph.compile(1L, List.of(plate));

        LongCraftingPlan<String> plan = planner.plan(graph, "plate", 3L, Map.of("ingot", 4L));

        assertFalse(plan.craftable());
        assertEquals(Map.of("ingot", 2L), plan.missing());
    }

    @Test
    void terminatesCyclesAsMissingInsteadOfRecursingForever() {
        var a = pattern("a", "b", 1L, "a", 1L);
        var b = pattern("b", "a", 1L, "b", 1L);
        var graph = CompiledCraftingGraph.compile(1L, List.of(a, b));

        LongCraftingPlan<String> plan = planner.plan(graph, "a", 1L, Map.of());

        assertFalse(plan.craftable());
        assertEquals(1L, plan.missing().values().stream().mapToLong(Long::longValue).sum());
    }

    @Test
    void refusesAPlanThatWouldOverflowLong() {
        var expensive = pattern("expensive", "raw", 2L, "out", 1L);
        var graph = CompiledCraftingGraph.compile(1L, List.of(expensive));

        assertThrows(
                CountOverflowException.class,
                () -> planner.plan(graph, "out", Long.MAX_VALUE, Map.of()));
    }

    @Test
    void comparesShadowResultsWithoutChangingReference() {
        var pattern = pattern("p", "raw", 1L, "out", 1L);
        var graph = CompiledCraftingGraph.compile(1L, List.of(pattern));
        LongCraftingPlan<String> plan = planner.plan(graph, "out", 2L, Map.of("raw", 2L));

        assertTrue(CraftingPlanShadowComparator.compare(plan, Map.of("p", 2L), Map.of()).matches());
        assertFalse(CraftingPlanShadowComparator.compare(plan, Map.of("p", 1L), Map.of()).matches());
    }

    @Test
    void plansDeepDependencyChainsWithoutRecursiveCalls() {
        int depth = 5_000;
        List<CompiledPattern<String>> patterns = new ArrayList<>(depth);
        for (int index = 1; index <= depth; index++) {
            patterns.add(pattern("p" + index, "k" + (index - 1), 1L, "k" + index, 1L));
        }
        var graph = CompiledCraftingGraph.compile(1L, patterns);

        LongCraftingPlan<String> plan = planner.plan(
                graph, "k" + depth, 1L, Map.of("k0", 1L));

        assertTrue(plan.craftable());
        assertEquals(depth, plan.patternExecutions().size());
    }

    @Test
    void ranksAlternativesByTheWholeRequestedBatch() {
        var pattern = new CompiledPattern<>(
                "bulk",
                List.of(new CompiledPattern.InputSlot<>(List.of(
                        new CompiledPattern.Stack<>("a-scarce", 1L),
                        new CompiledPattern.Stack<>("z-stocked", 1L)))),
                Map.of("out", 1L),
                false);
        var graph = CompiledCraftingGraph.compile(1L, List.of(pattern));

        LongCraftingPlan<String> plan = planner.plan(
                graph,
                "out",
                100L,
                Map.of("a-scarce", 1L, "z-stocked", 100L));

        assertTrue(plan.craftable());
        assertEquals(Map.of("z-stocked", 100L), plan.usedInventory());
    }

    private static CompiledPattern<String> pattern(
            String id, String input, long inputAmount, String output, long outputAmount) {
        return new CompiledPattern<>(
                id,
                List.of(new CompiledPattern.InputSlot<>(
                        List.of(new CompiledPattern.Stack<>(input, inputAmount)))),
                Map.of(output, outputAmount),
                false);
    }
}
