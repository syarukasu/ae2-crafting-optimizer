package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class SharedInputPlanningTest {
    @Test
    @Timeout(10)
    void wideSharedPlansPreserveExactConservationWithoutPerItemExpansion() {
        var program = sharedProgram(250);
        var stock = program.captureBigInventory(k -> BigInteger.ZERO, 4096);
        for (var amount : List.of(BigInteger.ONE, BigInteger.valueOf(100),
                new BigInteger("9220000000000000000"), BigInteger.valueOf(Long.MAX_VALUE),
                BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE), BigInteger.TEN.pow(64))) {
            var plan = program.planBig(amount, stock, PlanningGuard.none(), 4096);
            // Each branch consumes one common part. One raw makes three, and all surplus is reused.
            var raw = amount.multiply(BigInteger.valueOf(250)).add(BigInteger.TWO).divide(BigInteger.valueOf(3));
            assertEquals(raw, plan.patternExecutions().get("common"));
            assertEquals(Map.of("raw", raw), plan.missing());
            assertEquals(amount, plan.patternExecutions().get("root"));
            assertTrue(plan.usedInventory().isEmpty());
            assertTrue(plan.expandedRequests() <= 751);
            for (int i = 0; i < 250; i++) {
                assertEquals(amount, plan.patternExecutions().get("branch" + i));
            }
            // AE2 charges 8 * requested / amountPerByte, plus process times and 8 per tree node.
            var bytes = amount.multiply(BigInteger.valueOf(4259)).add(raw.multiply(BigInteger.valueOf(9)))
                    .add(BigInteger.valueOf(8L * plan.expandedRequests()));
            assertEquals(bytes, BigExactCraftingByteCounter.calculate(plan.trace(), k -> 1, 4096));
        }
    }

    @Test
    void concurrentSharedPlansDoNotReserveOrMutateTheInputSnapshot() throws Exception {
        var program = sharedProgram(20);
        var amount = BigInteger.TEN.pow(64);
        var raw = amount.multiply(BigInteger.valueOf(20)).add(BigInteger.TWO).divide(BigInteger.valueOf(3));
        var stock = program.captureBigInventory(k -> k.equals("raw") ? raw : BigInteger.ZERO, 4096);
        var expected = program.planBig(amount, stock, PlanningGuard.none(), 4096);
        var executor = Executors.newFixedThreadPool(4);
        try {
            var tasks = new ArrayList<Callable<BigCraftingPlan<String>>>();
            for (int i = 0; i < 8; i++) {
                tasks.add(() -> program.planBig(amount, stock, PlanningGuard.none(), 4096));
            }
            for (var future : executor.invokeAll(tasks)) {
                var actual = future.get();
                assertEquals(expected, actual);
                assertEquals(Map.of("raw", raw), actual.usedInventory());
                assertTrue(actual.craftable());
            }
        } finally {
            executor.shutdownNow();
        }
        assertThrows(PlanningCancelledException.class, () -> program.planBig(amount, stock, visited -> {
            if (visited > 3) throw new PlanningCancelledException(visited);
        }, 4096));
        assertEquals(expected, program.planBig(amount, stock, PlanningGuard.none(), 4096));
    }

    @Test
    void sharedAlternativeInputsDoNotEnterTheExactOrderedEvaluator() {
        var alternatives = new CompiledPattern.InputSlot<>(List.of(
                new CompiledPattern.Stack<>("a", 1), new CompiledPattern.Stack<>("b", 1)));
        var root = new CompiledPattern<>("root", List.of(alternatives, alternatives), Map.of("out", 1L), true);
        var program = CompiledRootProgram.tryCompile(CompiledCraftingGraph.compile(1, List.of(root)),
                "out", k -> false).orElseThrow();
        assertFalse(program.usesOrderedAccounting());
        assertFalse(program.hasUniqueInputOccurrencePerKey());
    }

    private static CompiledRootProgram<String> sharedProgram(int width) {
        var patterns = new ArrayList<CompiledPattern<String>>();
        var rootInputs = new ArrayList<CompiledPattern.InputSlot<String>>();
        for (int i = 0; i < width; i++) {
            String key = "branch" + i;
            rootInputs.add(slot(key));
            patterns.add(new CompiledPattern<>(key, List.of(slot("common")), Map.of(key, 1L), true));
        }
        patterns.add(new CompiledPattern<>("root", rootInputs, Map.of("out", 1L), true));
        patterns.add(new CompiledPattern<>("common", List.of(slot("raw")), Map.of("common", 3L), true));
        return CompiledRootProgram.tryCompile(CompiledCraftingGraph.compile(1, patterns),
                "out", k -> false).orElseThrow();
    }

    private static CompiledPattern.InputSlot<String> slot(String key) {
        return new CompiledPattern.InputSlot<>(List.of(new CompiledPattern.Stack<>(key, 1)));
    }
}
