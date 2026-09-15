package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LegacyByproductInventoryTest {
    private static CompiledPattern.InputSlot<String> slot(String key) {
        return new CompiledPattern.InputSlot<>(List.of(new CompiledPattern.Stack<>(key, 1)));
    }

    private static List<CompiledPattern<String>> patterns(boolean reverse) {
        return List.of(new CompiledPattern<>("root", reverse ? List.of(slot("b"), slot("a"))
                : List.of(slot("a"), slot("b")), Map.of("out", 1L), true),
                new CompiledPattern<>("split", List.of(slot("raw")), Map.of("a", 1L, "b", 1L), true));
    }

    @Test
    void longMapPlannerCannotReserveSimulatedByproductsAsInitialStock() {
        for (boolean reverse : new boolean[] {false, true}) {
            for (long b : new long[] {0, 3, 30}) {
                var graph = CompiledCraftingGraph.compile(1, patterns(reverse));
                var initial = Map.of("raw", 20L, "b", b);
                var expected = CompiledRootProgram.tryCompile(graph, "out", ignored -> false).orElseThrow();
                var ordered = expected.planLong(10, expected.captureLongInventory(k -> initial.getOrDefault(k, 0L)),
                        PlanningGuard.none());
                var actual = new LongCraftingPlanner<String>().plan(graph, "out", 10, initial);
                assertEquals(ordered.patternExecutions(), actual.patternExecutions());
                assertEquals(ordered.usedInventory(), actual.usedInventory());
                assertEquals(ordered.missing(), actual.missing());
                assertFalse(actual.usedInventory().containsKey("a"));
            }
        }
    }

    @Test
    void wideMapPlannerCannotReserveSimulatedByproductsAsInitialStock() {
        var requested = BigInteger.TEN.pow(64);
        var graph = CompiledCraftingGraph.compile(1, patterns(false));
        var initial = Map.of("raw", requested, "b", BigInteger.TEN);
        var actual = new BigCraftingPlanner<String>().plan(graph, "out", requested, initial);
        assertEquals(Map.of("root", requested, "split", requested), actual.patternExecutions());
        assertEquals(Map.of("raw", requested), actual.usedInventory());
        assertTrue(actual.missing().isEmpty());
        assertEquals(BigInteger.TEN, initial.get("b"));
    }

    @Test
    void publicFallbackStaysNonAuthoritativeAndDoesNotInventReservations() {
        var patterns = new ArrayList<>(patterns(false));
        patterns.add(new CompiledPattern<>("other-split", List.of(slot("other-raw")),
                Map.of("a", 1L, "b", 1L), true));
        var graph = CompiledCraftingGraph.compile(1, patterns);
        var result = new OverflowPromotingCraftingPlanner<String>().plan(graph, "out", BigInteger.TEN,
                Map.of("raw", BigInteger.TEN));
        var narrow = assertInstanceOf(OverflowPromotingCraftingPlanner.LongResult.class, result);
        assertFalse(narrow.provenEquivalent());
        assertEquals(Map.of("raw", 10L), narrow.plan().usedInventory());
    }
}
