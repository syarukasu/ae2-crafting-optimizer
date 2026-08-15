package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Root Programをコンパイルできなかったとき、その理由が構造的なのか
 * Snapshot都合なのかを呼出側が区別できることを固定する。
 */
class CompiledRootProgramFailureReasonTest {
    @Test
    void reportsCycleRatherThanAmbiguity() {
        var a = pattern("a", "b", 1L, "a", 1L);
        var b = pattern("b", "a", 1L, "b", 1L);

        var outcome = CompiledRootProgram.compile(
                CompiledCraftingGraph.compile(1L, List.of(a, b)),
                "a",
                ignored -> false);

        assertTrue(outcome.program().isEmpty());
        assertEquals(RootProgramFailure.CYCLE, outcome.failure());
        assertTrue(outcome.failure().structural());
    }

    @Test
    void reportsMultipleProducersForAGenuinelyAmbiguousOutput() {
        var first = pattern("first", "raw-a", 1L, "output", 1L);
        var second = pattern("second", "raw-b", 1L, "output", 1L);

        var outcome = CompiledRootProgram.compile(
                CompiledCraftingGraph.compile(1L, List.of(first, second)),
                "output",
                ignored -> false);

        assertEquals(RootProgramFailure.MULTIPLE_PRODUCERS, outcome.failure());
    }

    @Test
    void reportsMultipleOutputsForAByproductPattern() {
        var byproduct = new CompiledPattern<>(
                "byproduct",
                List.of(slot("raw", 1L)),
                Map.of("output", 1L, "extra", 1L),
                false);

        var outcome = CompiledRootProgram.compile(
                CompiledCraftingGraph.compile(1L, List.of(byproduct)),
                "output",
                ignored -> false);

        assertEquals(RootProgramFailure.MULTIPLE_OUTPUTS, outcome.failure());
    }

    @Test
    void keepsSuccessfulCompilationFreeOfAFailureReason() {
        var outcome = CompiledRootProgram.compile(
                CompiledCraftingGraph.compile(
                        1L,
                        List.of(pattern("output", "raw", 1L, "output", 1L))),
                "output",
                Set.of()::contains);

        assertTrue(outcome.program().isPresent());
        assertEquals(RootProgramFailure.NONE, outcome.failure());
    }

    @Test
    void refusesToCarryBothAProgramAndAFailure() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CompiledRootProgram.Outcome<String>(
                        java.util.Optional.empty(),
                        RootProgramFailure.NONE));
    }

    @Test
    void separatesStructuralReasonsFromSnapshotShapedOnes() {
        assertTrue(RootProgramFailure.MULTIPLE_PRODUCERS.structural());
        assertTrue(RootProgramFailure.CYCLE.structural());
        assertTrue(RootProgramFailure.MULTIPLE_OUTPUTS.structural());
        assertTrue(RootProgramFailure.PROGRAM_TOO_LARGE.structural());

        assertTrue(RootProgramFailure.INCOMPLETE_PATTERN_SNAPSHOT.snapshotShaped());
        assertTrue(RootProgramFailure.MISSING_FROM_SNAPSHOT.snapshotShaped());
        assertFalse(RootProgramFailure.INCOMPLETE_PATTERN_SNAPSHOT.structural());
        assertFalse(RootProgramFailure.MISSING_FROM_SNAPSHOT.structural());
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
