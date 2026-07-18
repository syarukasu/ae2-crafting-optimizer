package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CraftingPlannerBenchmarkTest {
    @Test
    void thousandRecipeHugeOrderRemainsGraphBounded() {
        List<CompiledPattern<String>> patterns = new ArrayList<>();
        for (int index = 1; index <= 1_000; index++) {
            patterns.add(new CompiledPattern<>(
                    "p" + index,
                    List.of(new CompiledPattern.InputSlot<>(List.of(
                            new CompiledPattern.Stack<>("k" + (index - 1), 9L)))),
                    Map.of("k" + index, 1L),
                    false));
        }
        CompiledCraftingGraph<String> graph = CompiledCraftingGraph.compile(1L, patterns);

        BigCraftingPlan<String> plan = assertTimeout(
                Duration.ofSeconds(2),
                () -> new BigCraftingPlanner<String>().plan(
                        graph,
                        "k1000",
                        BigInteger.TEN.pow(1023),
                        Map.of("k0", BigInteger.TEN.pow(2048))));
        assertEquals(1_001, plan.expandedRequests());
    }
}
