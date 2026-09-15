package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class EmittedCyclePlanningTest {
    private static final int BITS = BigCountMath.HARD_MAXIMUM_BITS;

    private static CompiledPattern<String> recipe(String id, String input, String output) {
        return new CompiledPattern<>(id, List.of(new CompiledPattern.InputSlot<>(
                List.of(new CompiledPattern.Stack<>(input, 2)))), Map.of(output, 1L), true);
    }

    private static CompiledCraftingGraph<String> recycling() {
        return CompiledCraftingGraph.compile(1, List.of(
                recipe("make", "feed", "product"), recipe("recycle", "product", "feed")));
    }

    @Test
    void emitterCutsItsUnusedRecipeAndTheAncestorCycle() {
        var graph = recycling();
        assertTrue(graph.isCyclic("product"));
        var program = CompiledRootProgram.tryCompile(graph, "product", "feed"::equals).orElseThrow();
        var plan = program.planLong(100, program.captureLongInventory(key -> key.equals("feed") ? 5 : 0),
                PlanningGuard.none());
        assertEquals(Map.of("make", 100L), plan.patternExecutions());
        assertEquals(Map.of("feed", 5L), plan.usedInventory());
        assertEquals(Map.of("feed", 195L), plan.emitted());
        assertTrue(plan.missing().isEmpty());
    }

    @Test
    void actualCyclesAreStillRejectedWhenNoEmitterBreaksThem() {
        assertEquals(RootProgramFailure.CYCLE, CompiledRootProgram.compile(
                recycling(), "product", ignored -> false).failure());
        assertEquals(RootProgramFailure.CYCLE, CompiledRootProgram.compile(
                recycling(), "product", "unrelated"::equals).failure());
    }

    @Test
    void rootEmitterCanAvoidTheEntireCyclicProductionTree() {
        var program = CompiledRootProgram.tryCompile(recycling(), "product", "product"::equals).orElseThrow();
        var plan = program.planLong(100, program.captureLongInventory(ignored -> 0), PlanningGuard.none());
        assertTrue(plan.patternExecutions().isEmpty());
        assertEquals(Map.of("product", 100L), plan.emitted());
    }

    @Test
    void longAndWideOrdersUseTheSameExactBoundary() {
        var program = CompiledRootProgram.tryCompile(recycling(), "product", "feed"::equals).orElseThrow();
        for (var requested : List.of(BigInteger.ONE, BigInteger.valueOf(Long.MAX_VALUE),
                BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE), BigInteger.TEN.pow(64))) {
            var plan = program.planBig(requested, program.captureBigInventory(ignored -> BigInteger.ZERO, BITS),
                    PlanningGuard.none(), BITS);
            assertEquals(Map.of("make", requested), plan.patternExecutions());
            assertEquals(Map.of("feed", requested.multiply(BigInteger.TWO)), plan.emitted());
            assertTrue(plan.missing().isEmpty());
        }
    }

    @Test
    void concurrentPlansDoNotMutateTheSharedSnapshot() throws Exception {
        var program = CompiledRootProgram.tryCompile(recycling(), "product", Set.of("feed")::contains)
                .orElseThrow();
        var stock = program.captureBigInventory(key -> key.equals("feed") ? BigInteger.TEN : BigInteger.ZERO, BITS);
        var executor = Executors.newFixedThreadPool(4);
        try {
            Callable<BigCraftingPlan<String>> task = () -> program.planBig(BigInteger.TEN.pow(64), stock,
                    PlanningGuard.none(), BITS);
            var results = executor.invokeAll(List.of(task, task, task, task));
            var expected = results.get(0).get();
            for (var result : results) {
                assertEquals(expected.patternExecutions(), result.get().patternExecutions());
                assertEquals(Map.of("feed", BigInteger.TEN), result.get().usedInventory());
                assertEquals(expected.emitted(), result.get().emitted());
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
