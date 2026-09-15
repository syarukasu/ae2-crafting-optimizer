package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** issue #103で混同された構造失敗とSnapshot失敗の分類を固定する。 */
class CompiledRootProgramFailureReasonTest {
    @Test
    void reportsCycleRatherThanAmbiguity() {
        var a = pattern("a", "b", "a");
        var b = pattern("b", "a", "b");
        var outcome = CompiledRootProgram.compile(
                CompiledCraftingGraph.compile(1L, List.of(a, b)),
                "a",
                ignored -> false);
        assertTrue(outcome.program().isEmpty());
        assertEquals(RootProgramFailure.CYCLE, outcome.failure());
    }

    @Test
    void reportsMultipleProducers() {
        var first = pattern("first", "raw-a", "output");
        var second = pattern("second", "raw-b", "output");
        var outcome = CompiledRootProgram.compile(
                CompiledCraftingGraph.compile(1L, List.of(first, second)),
                "output",
                ignored -> false);
        assertEquals(RootProgramFailure.MULTIPLE_PRODUCERS, outcome.failure());
    }

    @Test
    void separatesStructuralAndSnapshotFailures() {
        assertTrue(RootProgramFailure.CYCLE.structural());
        assertTrue(RootProgramFailure.MULTIPLE_PRODUCERS.structural());
        assertFalse(RootProgramFailure.INCOMPLETE_PATTERN_SNAPSHOT.structural());
        assertTrue(RootProgramFailure.INCOMPLETE_PATTERN_SNAPSHOT.snapshotShaped());
        assertTrue(RootProgramFailure.MISSING_FROM_SNAPSHOT.snapshotShaped());
    }

    private static CompiledPattern<String> pattern(String id, String input, String output) {
        return new CompiledPattern<>(
                id,
                List.of(new CompiledPattern.InputSlot<>(
                        List.of(new CompiledPattern.Stack<>(input, 1L)))),
                Map.of(output, 1L),
                false);
    }
}
