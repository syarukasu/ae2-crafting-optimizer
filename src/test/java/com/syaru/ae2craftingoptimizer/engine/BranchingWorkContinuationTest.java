package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BranchingWorkContinuationTest {
    private static final BigInteger RAW = BigInteger.valueOf(1024L * 1024L);

    @Test
    void nestedSmallBranchesFinishPastOldCumulativeWorkLimit() {
        var result = planner(PlanningGuard.none()).plan(BigInteger.ONE, false);
        assertTrue(result.plan().expandedRequests() > 1_048_576);
        assertEquals(Map.of("raw", RAW), result.plan().usedInventory());
        assertTrue(result.plan().missing().isEmpty());
        assertEquals(Map.of("root", BigInteger.ONE, "mid-first", BigInteger.valueOf(1024),
                "unit-first", RAW), result.plan().patternExecutions());
    }

    @Test
    void callerCanCancelAfterOldWorkLimit() {
        AtomicInteger last = new AtomicInteger();
        assertThrows(PlanningCancelledException.class, () -> planner(work -> {
            last.set(work);
            if (work > 1_048_700) throw new PlanningCancelledException(work);
        }).plan(BigInteger.ONE, false));
        assertTrue(last.get() > 1_048_576);
    }

    @Test
    void diagnosticCounterCannotWrapOrAffectExactQuantity() throws Exception {
        BigInteger amount = BigInteger.TEN.pow(30).add(BigInteger.ONE);
        var planner = new OrderedBranchingPlanner<String>("missing", key -> List.of(), key -> false,
                key -> BigInteger.ZERO, key -> 8, work -> assertEquals(Integer.MAX_VALUE, work), 256);
        var counter = OrderedBranchingPlanner.class.getDeclaredField("work");
        counter.setAccessible(true);
        counter.setInt(planner, Integer.MAX_VALUE);
        var result = planner.plan(amount, false);
        assertEquals(Integer.MAX_VALUE, result.plan().expandedRequests());
        assertEquals(amount, result.plan().requestedAmount());
        assertEquals(Map.of("missing", amount), result.plan().missing());
    }

    private static OrderedBranchingPlanner<String> planner(PlanningGuard guard) {
        var patterns = Map.of(
                "root", List.of(recipe("root", "root", "mid", 1024)),
                "mid", List.of(recipe("mid-first", "mid", "unit", 1024), recipe("mid-second", "mid", "other", 1)),
                "unit", List.of(recipe("unit-first", "unit", "raw", 1), recipe("unit-second", "unit", "other", 1)));
        return new OrderedBranchingPlanner<>("root", key -> patterns.getOrDefault(key, List.of()),
                key -> false, key -> key.equals("raw") ? RAW : BigInteger.ZERO,
                key -> 8, guard, 256);
    }

    private static CompiledPattern<String> recipe(String id, String output, String input, long amount) {
        return new CompiledPattern<>(id, List.of(new CompiledPattern.InputSlot<>(
                List.of(new CompiledPattern.Stack<>(input, amount)))), Map.of(output, 1L), false);
    }
}
