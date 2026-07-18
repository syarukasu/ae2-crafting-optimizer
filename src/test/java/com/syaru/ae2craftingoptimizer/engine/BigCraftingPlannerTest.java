package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BigCraftingPlannerTest {
    @Test
    void plansA128DigitRequestWithoutScalingByQuantity() {
        CompiledCraftingGraph<String> graph = graph();
        BigInteger requested = BigInteger.TEN.pow(128).subtract(BigInteger.ONE);

        BigCraftingPlan<String> plan = new BigCraftingPlanner<String>().plan(
                graph, "plate", requested, Map.of());

        assertTrue(plan.craftable());
        assertEquals(requested, plan.patternExecutions().get("plate"));
        assertEquals(requested.multiply(BigInteger.valueOf(3L)), plan.patternExecutions().get("ingot"));
        assertEquals(2, plan.expandedRequests());
    }

    @Test
    void reportsMissingInsteadOfLoopingOnCycles() {
        CompiledPattern<String> a = new CompiledPattern<>(
                "a", List.of(slot("b", 1)), Map.of("a", 1L), true);
        CompiledPattern<String> b = new CompiledPattern<>(
                "b", List.of(slot("a", 1)), Map.of("b", 1L), true);
        CompiledCraftingGraph<String> graph = CompiledCraftingGraph.compile(1L, List.of(a, b));

        BigCraftingPlan<String> plan = new BigCraftingPlanner<String>().plan(
                graph, "a", BigInteger.TEN.pow(64), Map.of());

        assertFalse(plan.craftable());
        assertEquals(BigInteger.TEN.pow(64), plan.missing().get("a"));
        assertTrue(plan.expandedRequests() <= 3);
    }

    private static CompiledCraftingGraph<String> graph() {
        CompiledPattern<String> plate = new CompiledPattern<>(
                "plate", List.of(slot("ingot", 3)), Map.of("plate", 1L), true);
        CompiledPattern<String> ingot = new CompiledPattern<>(
                "ingot", List.of(), Map.of("ingot", 1L), true);
        return CompiledCraftingGraph.compile(1L, List.of(plate, ingot));
    }

    private static CompiledPattern.InputSlot<String> slot(String key, long amount) {
        return new CompiledPattern.InputSlot<>(List.of(new CompiledPattern.Stack<>(key, amount)));
    }
}
