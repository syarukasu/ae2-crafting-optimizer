package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.*;
import static com.syaru.ae2craftingoptimizer.engine.ReusableByproductPlanningTest.*;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LinearWidePlanningTest {
    private static final BigInteger WIDE = BigInteger.TEN.pow(64);
    private static final int BITS = 4096;

    @Test
    void deepSharedDagHasBoundedWorkAndExactLogicalByteOverhead() {
        int depth = 28;
        var program = diamond(depth, false);
        int[] work = {0};
        long start = System.nanoTime();
        var plan = program.planBig(WIDE, program.captureBigInventory(k -> BigInteger.ZERO, BITS), n -> {
            if (++work[0] > 1000) fail("shared DAG repeated more than 1000 planning checkpoints");
        }, BITS);
        var leaves = BigInteger.ONE.shiftLeft(depth);
        var nodes = leaves.shiftLeft(1).subtract(BigInteger.ONE);
        assertEquals(Map.of("raw", WIDE.multiply(leaves)), plan.missing());
        assertEquals(depth, plan.patternExecutions().size());
        for (int i = 0; i < depth; i++)
            assertEquals(WIDE.shiftLeft(i), plan.patternExecutions().get("p" + i));
        var expectedBytes = WIDE.multiply(nodes).multiply(BigInteger.valueOf(8))
                .add(WIDE.multiply(leaves.subtract(BigInteger.ONE))).add(nodes.multiply(BigInteger.valueOf(8)));
        assertEquals(expectedBytes, BigExactCraftingByteCounter.calculate(plan.trace(), k -> 1, BITS));
        assertEquals(depth + 1, plan.expandedRequests());
        assertTrue(plan.trace().charges().size() <= 2 * (depth + 1));
        System.out.printf("Issue202 depth=%d logicalRequests=%s checkpoints=%d charges=%d elapsedMs=%.3f%n",
                depth, nodes, work[0], plan.trace().charges().size(), (System.nanoTime() - start) / 1e6);
    }

    @Test
    void exactMapsAndRationalBytesMatchOrderedReference() {
        for (int depth = 1; depth <= 8; depth++) {
            for (boolean emit : new boolean[] {false, true}) {
                var program = diamond(depth, emit);
                for (var stock : List.of(BigInteger.ZERO, WIDE, WIDE.shiftLeft(depth).add(BigInteger.ONE))) {
                    BigInteger[] initial = new BigInteger[program.nodeCount()];
                    Arrays.fill(initial, BigInteger.ZERO);
                    initial[program.indexOf("raw")] = stock;
                    var reference = OrderedByproductPlanner.plan(program, WIDE, initial, PlanningGuard.none(), BITS);
                    var actual = program.planBig(WIDE, program.captureBigInventory(
                            k -> k.equals("raw") ? stock : BigInteger.ZERO, BITS), PlanningGuard.none(), BITS);
                    assertEquals(reference.patternExecutions(), actual.patternExecutions());
                    assertEquals(reference.usedInventory(), actual.usedInventory());
                    assertEquals(reference.emitted(), actual.emitted());
                    assertEquals(reference.missing(), actual.missing());
                    for (long divisor : new long[] {1, 3, 1000, Long.MAX_VALUE}) {
                        assertEquals(BigExactCraftingByteCounter.calculate(reference.trace(), k -> divisor, BITS),
                                BigExactCraftingByteCounter.calculate(actual.trace(), k -> divisor, BITS));
                    }
                }
            }
        }
    }

    @Test
    void intermediateStockAndLongOrdersKeepOrderedTrace() {
        var program = diamond(5, false);
        BigInteger[] initial = new BigInteger[program.nodeCount()];
        Arrays.fill(initial, BigInteger.ZERO);
        initial[program.indexOf("n2")] = WIDE.add(BigInteger.ONE);
        assertEquals(OrderedByproductPlanner.plan(program, WIDE, initial, PlanningGuard.none(), BITS),
                program.planBig(WIDE, program.captureBigInventory(
                        k -> initial[program.indexOf(k)], BITS), PlanningGuard.none(), BITS));
        Arrays.fill(initial, BigInteger.ZERO);
        for (var amount : List.of(BigInteger.ONE, BigInteger.valueOf(Long.MAX_VALUE))) {
            assertEquals(OrderedByproductPlanner.plan(program, amount, initial, PlanningGuard.none(), BITS),
                    program.planBig(amount, program.captureBigInventory(k -> BigInteger.ZERO, BITS),
                            PlanningGuard.none(), BITS));
        }
    }

    @Test
    void cancellationAndCountLimitsAreNotSuppressed() {
        var program = diamond(28, false);
        var inventory = program.captureBigInventory(k -> BigInteger.ZERO, BITS);
        assertThrows(PlanningCancelledException.class, () -> program.planBig(WIDE, inventory, n -> {
            if (n > 2) throw new PlanningCancelledException(n);
        }, BITS));
        // After eligibility, cancellation must also reach the demand evaluation pass.
        assertThrows(PlanningCancelledException.class, () -> program.planBig(WIDE, inventory, n -> {
            if (n > 100) throw new PlanningCancelledException(n);
        }, BITS));
        assertThrows(IllegalArgumentException.class, () -> program.planBig(
                BigInteger.ONE.shiftLeft(BITS - 1), inventory, PlanningGuard.none(), BITS));
        assertEquals(Map.of("raw", WIDE.shiftLeft(28)),
                program.planBig(WIDE, inventory, PlanningGuard.none(), BITS).missing());
    }


    @Test
    void weightedReconvergingGraphsMatchOrderedReference() {
        var random = new java.util.Random(202);
        for (int sample = 0; sample < 80; sample++) {
            var patterns = new ArrayList<CompiledPattern<String>>();
            for (int i = 0; i < 7; i++) {
                var inputs = new ArrayList<CompiledPattern.InputSlot<String>>();
                for (int s = 0; s < 2; s++) {
                    int child = i + 1 + random.nextInt(7 - i);
                    inputs.add(slot(child == 7 ? "raw" : "n" + child, 1 + random.nextInt(5)));
                }
                patterns.add(process("p" + i, inputs, Map.of(i == 0 ? "out" : "n" + i, 1L)));
            }
            var program = CompiledRootProgram.tryCompile(CompiledCraftingGraph.compile(1, patterns),
                    "out", k -> false).orElseThrow();
            BigInteger[] stock = new BigInteger[program.nodeCount()];
            Arrays.fill(stock, BigInteger.ZERO);
            stock[program.indexOf("raw")] = WIDE.multiply(BigInteger.valueOf(random.nextInt(200)));
            var expected = OrderedByproductPlanner.plan(program, WIDE, stock, PlanningGuard.none(), BITS);
            var actual = LinearWidePlanning.tryPlan(program, WIDE, stock, PlanningGuard.none(), BITS);
            assertNotNull(actual);
            assertEquals(expected.patternExecutions(), actual.patternExecutions());
            assertEquals(expected.usedInventory(), actual.usedInventory());
            assertEquals(expected.missing(), actual.missing());
            assertEquals(BigExactCraftingByteCounter.calculate(expected.trace(), k -> k.equals("raw") ? 1000 : 3, BITS),
                    BigExactCraftingByteCounter.calculate(actual.trace(), k -> k.equals("raw") ? 1000 : 3, BITS));
        }
    }

    @Test
    void roundingCoProductsAndTemplateQuantaAreExcluded() {
        for (var pattern : List.of(
                process("p", List.of(slot("raw", 1)), Map.of("out", 2L)),
                process("p", List.of(slot("raw", 1)), Map.of("out", 1L, "waste", 1L)),
                process("p", List.of(new CompiledPattern.InputSlot<>(
                        List.of(new CompiledPattern.Stack<>("raw", 1000)), 1000)), Map.of("out", 1L)),
                process("p", List.of(new CompiledPattern.InputSlot<>(List.of(
                        new CompiledPattern.Stack<>("raw", 1),
                        new CompiledPattern.Stack<>("other", 1)))), Map.of("out", 1L)))) {
            var program = compile(pattern);
            var initial = new BigInteger[program.nodeCount()];
            Arrays.fill(initial, BigInteger.ZERO);
            assertNull(LinearWidePlanning.tryPlan(program, WIDE, initial, PlanningGuard.none(), BITS));
        }
    }

    @Test
    void logicalPathCountBeyondIntegerRangeIsNotNarrowed() {
        var program = diamond(80, false);
        var plan = program.planBig(WIDE, program.captureBigInventory(k -> BigInteger.ZERO, BITS),
                PlanningGuard.none(), BITS);
        assertEquals(81, plan.expandedRequests());
        assertEquals(BigInteger.ONE.shiftLeft(81).subtract(BigInteger.ONE).multiply(BigInteger.valueOf(8)),
                plan.trace().charges().get(plan.trace().charges().size() - 1).amount());
        assertEquals(Map.of("raw", WIDE.shiftLeft(80)), plan.missing());
    }

    @Test
    void compressedChargeLimitDoesNotRejectValidOrderedPlan() {
        var program = compile(process("p", List.of(slot("raw", 32), slot("raw", 32)), Map.of("out", 1L)));
        var amount = BigInteger.ONE.shiftLeft(64);
        var stock = BigInteger.ONE.shiftLeft(69);
        var initial = new BigInteger[program.nodeCount()];
        Arrays.fill(initial, BigInteger.ZERO);
        initial[program.indexOf("raw")] = stock;
        var reference = OrderedByproductPlanner.plan(program, amount, initial, PlanningGuard.none(), 70);
        var actual = program.planBig(amount, program.captureBigInventory(
                k -> k.equals("raw") ? stock : BigInteger.ZERO, 70), PlanningGuard.none(), 70);
        assertEquals(reference, actual);
        assertEquals(Map.of("raw", stock), actual.usedInventory());
        assertEquals(Map.of("raw", stock), actual.missing());
        assertTrue(BigExactCraftingByteCounter.calculate(actual.trace(), k -> 1000, 70).bitLength() <= 70);
    }

    static CompiledRootProgram<String> diamond(int depth, boolean emit) {
        var patterns = new ArrayList<CompiledPattern<String>>();
        for (int i = 0; i < depth; i++) {
            String child = i + 1 == depth ? "raw" : "n" + (i + 1);
            patterns.add(process("p" + i, List.of(slot(child, 1), slot(child, 1)),
                    Map.of(i == 0 ? "out" : "n" + i, 1L)));
        }
        return CompiledRootProgram.tryCompile(CompiledCraftingGraph.compile(1, patterns),
                "out", k -> emit && k.equals("raw")).orElseThrow();
    }
}
