package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class CraftingPlannerPropertyTest {
    @Test
    void longAndBigPlannersAgreeAcrossRandomAcyclicGraphs() {
        Random random = new Random(0xA0C2026L);
        for (int iteration = 0; iteration < 250; iteration++) {
            int nodes = 2 + random.nextInt(30);
            List<CompiledPattern<String>> patterns = new ArrayList<>();
            for (int node = 1; node < nodes; node++) {
                int dependency = random.nextInt(node);
                long inputAmount = 1 + random.nextInt(5);
                long outputAmount = 1 + random.nextInt(4);
                patterns.add(pattern("p" + node, "k" + dependency, inputAmount, "k" + node, outputAmount));
            }
            CompiledCraftingGraph<String> graph = CompiledCraftingGraph.compile(iteration, patterns);
            long requested = 1 + random.nextInt(100_000);
            Map<String, Long> inventory = Map.of("k0", (long) random.nextInt(1_000_000));

            LongCraftingPlan<String> longPlan = new LongCraftingPlanner<String>().plan(
                    graph, "k" + (nodes - 1), requested, inventory);
            CompiledRootProgram<String> program = CompiledRootProgram.tryCompile(
                            graph,
                            "k" + (nodes - 1),
                            ignored -> false)
                    .orElseThrow();
            LongCraftingPlan<String> compiledPlan = program.planLong(
                    requested,
                    program.captureLongInventory(key -> inventory.getOrDefault(key, 0L)),
                    PlanningGuard.none());
            BigCraftingPlan<String> bigPlan = new BigCraftingPlanner<String>().plan(
                    graph,
                    "k" + (nodes - 1),
                    BigInteger.valueOf(requested),
                    toBig(inventory));

            assertEquals(toBig(longPlan.patternExecutions()), bigPlan.patternExecutions());
            assertEquals(toBig(longPlan.usedInventory()), bigPlan.usedInventory());
            assertEquals(toBig(longPlan.missing()), bigPlan.missing());
            assertEquals(longPlan.patternExecutions(), compiledPlan.patternExecutions());
            assertEquals(longPlan.usedInventory(), compiledPlan.usedInventory());
            assertEquals(longPlan.emitted(), compiledPlan.emitted());
            assertEquals(longPlan.missing(), compiledPlan.missing());
        }
    }

    @Test
    void planningWorkDependsOnGraphNotDecimalDigitCount() {
        List<CompiledPattern<String>> patterns = new ArrayList<>();
        for (int index = 1; index <= 200; index++) {
            patterns.add(pattern("p" + index, "k" + (index - 1), 2L, "k" + index, 1L));
        }
        CompiledCraftingGraph<String> graph = CompiledCraftingGraph.compile(1L, patterns);
        BigCraftingPlanner<String> planner = new BigCraftingPlanner<>();
        int at64Digits = planner.plan(graph, "k200", BigInteger.TEN.pow(63), Map.of()).expandedRequests();
        int at128Digits = planner.plan(graph, "k200", BigInteger.TEN.pow(127), Map.of()).expandedRequests();
        int at1024Digits = planner.plan(graph, "k200", BigInteger.TEN.pow(1023), Map.of()).expandedRequests();

        assertEquals(at64Digits, at128Digits);
        assertEquals(at64Digits, at1024Digits);
    }

    private static CompiledPattern<String> pattern(
            String id,
            String input,
            long inputAmount,
            String output,
            long outputAmount) {
        return new CompiledPattern<>(
                id,
                List.of(new CompiledPattern.InputSlot<>(
                        List.of(new CompiledPattern.Stack<>(input, inputAmount)))),
                Map.of(output, outputAmount),
                false);
    }

    private static <K> Map<K, BigInteger> toBig(Map<K, Long> source) {
        Map<K, BigInteger> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, BigInteger.valueOf(value)));
        return Map.copyOf(result);
    }
}
