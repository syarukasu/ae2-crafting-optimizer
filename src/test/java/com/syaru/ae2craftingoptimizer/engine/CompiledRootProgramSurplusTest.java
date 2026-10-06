package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CompiledRootProgramSurplusTest {
    @Test
    void reusesSharedIntermediateSurplusWithOrderedAccounting() {
        var out = new CompiledPattern<>(
                "out",
                List.of(slot("a", 1), slot("b", 1)),
                Map.of("out", 1L),
                false);
        var a = pattern("a", "c", 1, "a", 1);
        var b = pattern("b", "c", 1, "b", 1);
        var c = pattern("c", "raw", 1, "c", 3);
        var graph = CompiledCraftingGraph.compile(1, List.of(out, a, b, c));

        var program = CompiledRootProgram.tryCompile(graph, "out", key -> false).orElseThrow();
        var inventory = program.captureBigInventory(
                key -> key.equals("raw") ? BigInteger.ONE : BigInteger.ZERO,
                BigCountMath.HARD_MAXIMUM_BITS);
        BigCraftingPlan<String> plan = program.planBig(
                BigInteger.ONE, inventory, PlanningGuard.none(), BigCountMath.HARD_MAXIMUM_BITS);

        assertTrue(plan.craftable());
        assertEquals(BigInteger.ONE, plan.patternExecutions().get("c"));
        assertEquals(Map.of("raw", BigInteger.ONE), plan.usedInventory());
        // The second request for c consumes prior surplus without expanding its raw input again.
        assertEquals(6, plan.expandedRequests());
        assertEquals(BigInteger.valueOf(6 * 8 + 4 + 6 * 8),
                BigExactCraftingByteCounter.calculate(plan.trace(), k -> 1, 4096));
    }

    @Test
    void acceptsIndependentByproductGraphs() {
        var multiOutput = new CompiledPattern<>(
                "multi",
                List.of(slot("raw", 1)),
                Map.of("out", 1L, "byproduct", 1L),
                false);
        var graph = CompiledCraftingGraph.compile(1, List.of(multiOutput));
        var program = CompiledRootProgram.tryCompile(graph, "out", key -> false).orElseThrow();
        var inventory = program.captureLongInventory(key -> key.equals("raw") ? 1L : 0L);
        var plan = program.planLong(1L, inventory, PlanningGuard.none());
        assertTrue(plan.craftable());
        assertEquals(Map.of("raw", 1L), plan.usedInventory());
        assertEquals(Map.of("multi", 1L), plan.patternExecutions());
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
