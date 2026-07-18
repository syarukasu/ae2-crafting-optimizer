package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class CompiledPlanningSessionTest {
    @Test
    void rejectsStaleInventoryGeneration() {
        AtomicLong patterns = new AtomicLong(1);
        AtomicLong inventory = new AtomicLong(2);
        AtomicLong recipes = new AtomicLong(3);
        CompiledPlanningSession<String> session = session(patterns, inventory, recipes);
        inventory.incrementAndGet();
        assertThrows(
                StalePlanningSnapshotException.class,
                () -> session.plan("out", BigInteger.ONE));
    }

    @Test
    void cancellationDiscardsCalculation() {
        AtomicLong patterns = new AtomicLong(1);
        AtomicLong inventory = new AtomicLong(2);
        AtomicLong recipes = new AtomicLong(3);
        CompiledPlanningSession<String> session = session(patterns, inventory, recipes);
        session.cancel();
        assertThrows(
                PlanningCancelledException.class,
                () -> session.plan("out", BigInteger.ONE));
    }

    @Test
    void promotesOnlyOverflowingRequestToBigInteger() {
        AtomicLong patterns = new AtomicLong(1);
        AtomicLong inventory = new AtomicLong(2);
        AtomicLong recipes = new AtomicLong(3);
        CompiledPlanningSession<String> session = session(patterns, inventory, recipes);
        var result = session.plan("out", BigInteger.valueOf(Long.MAX_VALUE));
        assertTrue(result.usesBigInteger());
    }

    private static CompiledPlanningSession<String> session(
            AtomicLong patterns,
            AtomicLong inventory,
            AtomicLong recipes) {
        CompiledPattern<String> pattern = new CompiledPattern<>(
                "p",
                List.of(new CompiledPattern.InputSlot<>(
                        List.of(new CompiledPattern.Stack<>("raw", 2)))),
                Map.of("out", 1L),
                false);
        var graph = CompiledCraftingGraph.compile(1, List.of(pattern));
        return new CompiledPlanningSession<>(
                graph,
                Map.of(),
                Set.of(),
                new PlanningGenerationSnapshot(1, 2, 3),
                patterns::get,
                inventory::get,
                recipes::get,
                new PlanningCancellationToken());
    }
}
