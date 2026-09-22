package com.syaru.ae2craftingoptimizer.engine;

import static com.syaru.ae2craftingoptimizer.engine.OrderedBranchingPlannerTest.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OrdinaryBranchingPerformanceTest {
    @Test
    void oneRootWithThousandIntermediateCraftsAndFullStockHasBoundedWork() {
        var result = nested(Map.of("raw", BigInteger.valueOf(2000)));
        assertTrue(result.plan().craftable());
        assertEquals(Map.of("raw", BigInteger.valueOf(2000)), result.plan().usedInventory());
        assertEquals(Map.of("root", BigInteger.ONE, "first", BigInteger.valueOf(1000)),
                result.plan().patternExecutions());
        bounded(result);
    }

    @Test
    void oneRootWithThousandIntermediateCraftsAndMissingStockHasBoundedWork() {
        var result = nested(Map.of());
        assertFalse(result.plan().craftable());
        assertEquals(Map.of("raw", BigInteger.valueOf(2000)), result.plan().missing());
        assertEquals(Map.of(), result.plan().usedInventory());
        assertEquals(Map.of("root", BigInteger.ONE, "first", BigInteger.valueOf(1000)),
                result.plan().patternExecutions());
        bounded(result);
    }

    @Test
    void ordinaryPartialStockStopsBatchAtProducerChange() {
        var result = nested(Map.of("raw", BigInteger.valueOf(501), "other", BigInteger.valueOf(2250)));
        assertTrue(result.plan().craftable());
        assertEquals(Map.of("raw", BigInteger.valueOf(500), "other", BigInteger.valueOf(2250)),
                result.plan().usedInventory());
        assertEquals(Map.of("root", BigInteger.ONE, "first", BigInteger.valueOf(250),
                "second", BigInteger.valueOf(750)), result.plan().patternExecutions());
        bounded(result);
    }

    @Test
    void ordinaryReusableCatalystIsReservedOnlyOnce() {
        var graph = CompiledCraftingGraph.compile(1, List.of(recipe("reuse",
                List.of(slot("tool", 1), slot("raw", 2)), Map.of("out", 1L, "tool", 1L))));
        var stock = Map.of("tool", BigInteger.ONE, "raw", BigInteger.valueOf(2000));
        var result = new OrderedBranchingPlanner<>("out", graph::patternsFor, k -> false,
                k -> stock.getOrDefault(k, BigInteger.ZERO), k -> 1, PlanningGuard.none(), 4096)
                .plan(BigInteger.valueOf(1000), false);
        assertTrue(result.plan().craftable());
        assertEquals(stock, result.plan().usedInventory());
        assertEquals(Map.of("reuse", BigInteger.valueOf(1000)), result.plan().patternExecutions());
        bounded(result);
    }

    private static OrderedBranchingPlanner.Result<String> nested(Map<String, BigInteger> stock) {
        var graph = CompiledCraftingGraph.compile(1, List.of(
                recipe("root", List.of(slot("part", 1000)), Map.of("out", 1L)),
                recipe("first", List.of(slot("raw", 2)), Map.of("part", 1L)),
                recipe("second", List.of(slot("other", 3)), Map.of("part", 1L))));
        return new OrderedBranchingPlanner<>("out", graph::patternsFor, k -> false,
                k -> stock.getOrDefault(k, BigInteger.ZERO), k -> 1, PlanningGuard.none(), 4096)
                .plan(BigInteger.ONE, false);
    }

    private static void bounded(OrderedBranchingPlanner.Result<String> result) {
        System.out.printf("Issue207 craftable=%s work=%d skipped=%s%n", result.plan().craftable(),
                result.plan().expandedRequests(), result.skippedIterations());
        assertTrue(result.plan().expandedRequests() < 150, "must not visit each repeated craft");
        assertTrue(result.skippedIterations().compareTo(BigInteger.valueOf(900)) > 0);
    }
}
