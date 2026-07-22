package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CompiledRootProgramTest {
    @Test
    void aggregatesSharedIntermediateBeforeProcessingItOnce() {
        var output = new CompiledPattern<>(
                "output",
                List.of(slot("a", 1L), slot("b", 1L)),
                Map.of("output", 1L),
                false);
        var a = pattern("a", "shared", 1L, "a", 1L);
        var b = pattern("b", "shared", 1L, "b", 1L);
        var shared = pattern("shared", "raw", 3L, "shared", 1L);
        var program = compile(List.of(output, a, b, shared), "output");
        var inventory = program.captureLongInventory(key -> key.equals("raw") ? 6L : 0L);

        LongCraftingPlan<String> plan = program.planLong(1L, inventory, PlanningGuard.none());

        assertTrue(plan.craftable());
        assertEquals(2L, plan.patternExecutions().get("shared"));
        assertEquals(Map.of("raw", 6L), plan.usedInventory());
    }

    @Test
    void reportsEveryMissingLeafWithoutStoppingAtTheFirstOne() {
        var output = new CompiledPattern<>(
                "output",
                List.of(slot("raw-a", 2L), slot("raw-b", 3L)),
                Map.of("output", 1L),
                false);
        var program = compile(List.of(output), "output");
        var inventory = program.captureLongInventory(ignored -> 0L);

        LongCraftingPlan<String> plan = program.planLong(4L, inventory, PlanningGuard.none());

        assertEquals(Map.of("raw-a", 8L, "raw-b", 12L), plan.missing());
    }

    @Test
    void promotesOnlyTheOverflowingOrderAndReusesTheSameProgram() {
        var program = compile(List.of(pattern("output", "gas", 2L, "output", 1L)), "output");
        var inventory = program.captureLongInventory(ignored -> 0L);
        BigInteger requested = BigInteger.valueOf(Long.MAX_VALUE);

        var result = new OverflowPromotingCraftingPlanner<String>().plan(
                program,
                requested,
                inventory,
                PlanningGuard.none());

        var big = assertInstanceOf(OverflowPromotingCraftingPlanner.BigResult.class, result);
        assertEquals(requested.multiply(BigInteger.TWO), big.plan().missing().get("gas"));
    }

    @Test
    void keepsTwoLongMaximumChemicalInputsExactAfterPromotion() {
        var output = new CompiledPattern<>(
                "pressurized-reaction",
                List.of(slot("gas-a", Long.MAX_VALUE), slot("gas-b", Long.MAX_VALUE)),
                Map.of("output", 1L),
                false);
        var program = compile(List.of(output), "output");
        var inventory = program.captureLongInventory(ignored -> 0L);

        var result = new OverflowPromotingCraftingPlanner<String>().plan(
                program,
                BigInteger.TWO,
                inventory,
                PlanningGuard.none());

        var big = assertInstanceOf(OverflowPromotingCraftingPlanner.BigResult.class, result);
        BigInteger expectedPerGas = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO);
        assertEquals(expectedPerGas, big.plan().missing().get("gas-a"));
        assertEquals(expectedPerGas, big.plan().missing().get("gas-b"));
        assertEquals(BigInteger.TWO, big.plan().patternExecutions().get("pressurized-reaction"));
    }

    @Test
    void visitsTheSameNumberOfNodesForSmallAndSixteenThousandDigitOrders() {
        int depth = 1_000;
        List<CompiledPattern<String>> patterns = new ArrayList<>(depth);
        for (int index = 1; index <= depth; index++) {
            patterns.add(pattern(
                    "p" + index,
                    "k" + (index - 1),
                    1L,
                    "k" + index,
                    1L));
        }
        var program = compile(patterns, "k" + depth);
        var inventory = program.captureLongInventory(ignored -> 0L);
        AtomicInteger smallVisits = new AtomicInteger();
        AtomicInteger hugeVisits = new AtomicInteger();

        program.planLong(1L, inventory, smallVisits::set);
        program.planBig(
                BigInteger.TEN.pow(BigCountMath.HARD_MAXIMUM_DECIMAL_DIGITS - 1),
                inventory,
                hugeVisits::set,
                BigCountMath.HARD_MAXIMUM_BITS);

        assertEquals(program.nodeCount(), smallVisits.get());
        assertEquals(program.nodeCount(), hugeVisits.get());
    }

    @Test
    void readsOnlyKeysReferencedByTheCompiledRoot() {
        var unrelated = pattern("unrelated", "unused-raw", 1L, "unused-output", 1L);
        var program = compile(
                List.of(pattern("output", "raw", 1L, "output", 1L), unrelated),
                "output");
        AtomicInteger reads = new AtomicInteger();

        program.captureLongInventory(key -> {
            reads.incrementAndGet();
            return 0L;
        });

        assertEquals(program.nodeCount(), reads.get());
        assertEquals(-1, program.indexOf("unused-output"));
        assertEquals(-1, program.indexOf("unused-raw"));
    }

    @Test
    void refusesAmbiguousByproductAndCyclicRoutes() {
        var first = new CompiledPattern<>("first", List.of(), Map.of("output", 1L), false);
        var second = new CompiledPattern<>("second", List.of(), Map.of("output", 1L), false);
        assertTrue(CompiledRootProgram.tryCompile(
                        CompiledCraftingGraph.compile(1L, List.of(first, second)),
                        "output",
                        ignored -> false)
                .isEmpty());

        var byproduct = new CompiledPattern<>(
                "byproduct",
                List.of(slot("raw", 1L)),
                Map.of("output", 1L, "extra", 1L),
                false);
        assertTrue(CompiledRootProgram.tryCompile(
                        CompiledCraftingGraph.compile(1L, List.of(byproduct)),
                        "output",
                        ignored -> false)
                .isEmpty());

        var a = pattern("a", "b", 1L, "a", 1L);
        var b = pattern("b", "a", 1L, "b", 1L);
        assertTrue(CompiledRootProgram.tryCompile(
                        CompiledCraftingGraph.compile(1L, List.of(a, b)),
                        "a",
                        ignored -> false)
                .isEmpty());
    }

    @Test
    void enforcesTheExactSixteenThousandDigitMaximum() {
        BigInteger maximum = BigInteger.TEN
                .pow(BigCountMath.HARD_MAXIMUM_DECIMAL_DIGITS)
                .subtract(BigInteger.ONE);
        var program = compile(List.of(pattern("output", "raw", 1L, "output", 1L)), "output");
        var inventory = program.captureLongInventory(ignored -> 0L);

        BigCraftingPlan<String> accepted = program.planBig(
                maximum,
                inventory,
                PlanningGuard.none(),
                BigCountMath.HARD_MAXIMUM_BITS);
        assertEquals(maximum, accepted.missing().get("raw"));

        assertThrows(
                IllegalArgumentException.class,
                () -> program.planBig(
                        maximum.add(BigInteger.ONE),
                        inventory,
                        PlanningGuard.none(),
                        BigCountMath.HARD_MAXIMUM_BITS));
    }

    private static CompiledRootProgram<String> compile(
            List<CompiledPattern<String>> patterns,
            String root) {
        return CompiledRootProgram.tryCompile(
                        CompiledCraftingGraph.compile(1L, patterns),
                        root,
                        Set.of()::contains)
                .orElseThrow();
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
        return new CompiledPattern<>(
                id,
                List.of(slot(input, inputAmount)),
                Map.of(output, outputAmount),
                false);
    }
}
