package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OverflowPromotingCraftingPlannerTest {
    @Test
    void retainsLongFastPathWhenAllArithmeticFits() {
        var result = new OverflowPromotingCraftingPlanner<String>().plan(
                graph(2L), "output", BigInteger.valueOf(10L), Map.of());

        assertFalse(result.usesBigInteger());
        assertTrue(result.provenEquivalent());
        var plan = assertInstanceOf(OverflowPromotingCraftingPlanner.LongResult.class, result).plan();
        assertEquals(10L, plan.patternExecutions().get("output"));
    }

    @Test
    void recomputesFromSnapshotAfterIntermediateMultiplicationOverflows() {
        BigInteger request = BigInteger.valueOf(Long.MAX_VALUE);
        var result = new OverflowPromotingCraftingPlanner<String>().plan(
                graph(2L), "output", request, Map.of());

        assertTrue(result.usesBigInteger());
        assertTrue(result.provenEquivalent());
        var plan = assertInstanceOf(OverflowPromotingCraftingPlanner.BigResult.class, result).plan();
        assertEquals(request.multiply(BigInteger.TWO), plan.patternExecutions().get("input"));
    }

    @Test
    void startsOnBigPathWhenRequestAlreadyExceedsLong() {
        BigInteger request = BigInteger.ONE.shiftLeft(256);
        var result = new OverflowPromotingCraftingPlanner<String>().plan(
                graph(1L), "output", request, Map.of());

        assertTrue(result.usesBigInteger());
        assertTrue(result.craftable());
        assertTrue(result.provenEquivalent());
    }

    @Test
    void rejectsIntermediateBigIntegerGrowthPastConfiguredBits() {
        BigInteger request = BigInteger.ONE.shiftLeft(63);
        OverflowPromotingCraftingPlanner<String> planner =
                new OverflowPromotingCraftingPlanner<>(64);

        assertThrows(
                IllegalArgumentException.class,
                () -> planner.plan(graph(2L), "output", request, Map.of()));
    }

    @Test
    void marksAmbiguousPatternSelectionAsShadowOnly() {
        CompiledPattern<String> first = new CompiledPattern<>(
                "first", List.of(), Map.of("output", 1L), true);
        CompiledPattern<String> second = new CompiledPattern<>(
                "second", List.of(), Map.of("output", 1L), true);
        var result = new OverflowPromotingCraftingPlanner<String>().plan(
                CompiledCraftingGraph.compile(1L, List.of(first, second)),
                "output",
                BigInteger.ONE,
                Map.of());

        assertFalse(result.provenEquivalent());
    }

    private static CompiledCraftingGraph<String> graph(long inputAmount) {
        CompiledPattern<String> output = new CompiledPattern<>(
                "output",
                List.of(new CompiledPattern.InputSlot<>(
                        List.of(new CompiledPattern.Stack<>("input", inputAmount)))),
                Map.of("output", 1L),
                true);
        CompiledPattern<String> input = new CompiledPattern<>(
                "input", List.of(), Map.of("input", 1L), true);
        return CompiledCraftingGraph.compile(1L, List.of(output, input));
    }
}
