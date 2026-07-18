package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SymbolicCraftingPlannerTest {
    @Test
    void aggregatesSharedIntermediateBeforeExpandingIt() {
        var out = new CompiledPattern<>(
                "out",
                List.of(slot("a", 1), slot("b", 1)),
                Map.of("out", 1L),
                false);
        var a = pattern("a", "c", 1, "a", 1);
        var b = pattern("b", "c", 1, "b", 1);
        var c = pattern("c", "raw", 1, "c", 3);
        var graph = CompiledCraftingGraph.compile(1, List.of(out, a, b, c));

        BigCraftingPlan<String> plan = new SymbolicCraftingPlanner<String>()
                .tryPlanBig(graph, "out", BigInteger.ONE, Map.of("raw", BigInteger.ONE), Set.of(), PlanningGuard.none())
                .orElseThrow();

        assertTrue(plan.craftable());
        assertEquals(BigInteger.ONE, plan.patternExecutions().get("c"));
        assertEquals(5, plan.expandedRequests());
    }

    @Test
    void refusesAmbiguousOrByproductGraphs() {
        var multiOutput = new CompiledPattern<>(
                "multi",
                List.of(slot("raw", 1)),
                Map.of("out", 1L, "byproduct", 1L),
                false);
        var graph = CompiledCraftingGraph.compile(1, List.of(multiOutput));
        assertTrue(new SymbolicCraftingPlanner<String>()
                .tryPlanLong(graph, "out", 1, Map.of("raw", 1L), Set.of(), PlanningGuard.none())
                .isEmpty());
    }

    private static CompiledPattern.InputSlot<String> slot(String key, long amount) {
        return new CompiledPattern.InputSlot<>(List.of(new CompiledPattern.Stack<>(key, amount)));
    }

    private static CompiledPattern<String> pattern(
            String id,
            String input,
            long inputAmount,
            String output,
            long outputAmount) {
        return new CompiledPattern<>(id, List.of(slot(input, inputAmount)), Map.of(output, outputAmount), false);
    }
}
