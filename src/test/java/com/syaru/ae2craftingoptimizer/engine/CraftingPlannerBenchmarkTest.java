package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CraftingPlannerBenchmarkTest {
    @Test
    void thousandRecipeHugeOrderRemainsGraphBounded() {
        List<CompiledPattern<String>> patterns = new ArrayList<>();
        for (int index = 1; index <= 1_000; index++) {
            patterns.add(new CompiledPattern<>(
                    "p" + index,
                    List.of(new CompiledPattern.InputSlot<>(List.of(
                            new CompiledPattern.Stack<>("k" + (index - 1), 1L)))),
                    Map.of("k" + index, 1L),
                    false));
        }
        CompiledCraftingGraph<String> graph = CompiledCraftingGraph.compile(1L, patterns);
        CompiledRootProgram<String> program = CompiledRootProgram.tryCompile(
                        graph,
                        "k1000",
                        ignored -> false)
                .orElseThrow();
        var inventory = program.captureLongInventory(ignored -> 0L);
        AtomicInteger singleOrderVisits = new AtomicInteger();
        AtomicInteger maximumLongOrderVisits = new AtomicInteger();

        LongCraftingPlan<String> singleOrder = program.planLong(
                1L,
                inventory,
                singleOrderVisits::set);
        LongCraftingPlan<String> maximumLongOrder = program.planLong(
                Long.MAX_VALUE,
                inventory,
                maximumLongOrderVisits::set);

        BigCraftingPlan<String> plan = assertTimeout(
                Duration.ofSeconds(2),
                () -> program.planBig(
                        BigInteger.TEN.pow(BigCountMath.HARD_MAXIMUM_DECIMAL_DIGITS - 1),
                        inventory,
                        PlanningGuard.none(),
                        BigCountMath.HARD_MAXIMUM_BITS));
        assertEquals(1_001, singleOrderVisits.get());
        assertEquals(1_001, maximumLongOrderVisits.get());
        assertEquals(singleOrderVisits.get(), maximumLongOrderVisits.get());
        assertEquals(1_001, plan.expandedRequests());
    }
}
