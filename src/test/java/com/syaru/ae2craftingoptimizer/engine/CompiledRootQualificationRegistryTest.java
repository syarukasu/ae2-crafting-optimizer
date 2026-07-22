package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CompiledRootQualificationRegistryTest {
    @AfterEach
    void clearRegistry() {
        CompiledRootQualificationRegistry.clear();
    }

    @Test
    void qualifiesOnlyAfterRequiredMatches() {
        var program = program(1L);
        CompiledRootQualificationRegistry.recordMatch(program);
        assertFalse(CompiledRootQualificationRegistry.isQualified(program, 2));

        CompiledRootQualificationRegistry.recordMatch(program);
        assertTrue(CompiledRootQualificationRegistry.isQualified(program, 2));
    }

    @Test
    void oneMismatchRejectsOnlyThatGenerationProgram() {
        var firstGeneration = program(1L);
        CompiledRootQualificationRegistry.recordMatch(firstGeneration);
        CompiledRootQualificationRegistry.recordMismatch(firstGeneration);
        CompiledRootQualificationRegistry.recordMatch(firstGeneration);
        assertFalse(CompiledRootQualificationRegistry.isQualified(firstGeneration, 1));

        var secondGeneration = program(2L);
        CompiledRootQualificationRegistry.recordMatch(secondGeneration);
        assertTrue(CompiledRootQualificationRegistry.isQualified(secondGeneration, 1));
    }

    private static CompiledRootProgram<String> program(long generation) {
        var pattern = new CompiledPattern<>(
                "p",
                List.of(new CompiledPattern.InputSlot<>(
                        List.of(new CompiledPattern.Stack<>("raw", 1L)))),
                Map.of("output", 1L),
                false);
        return CompiledRootProgram.tryCompile(
                        CompiledCraftingGraph.compile(generation, List.of(pattern)),
                        "output",
                        ignored -> false)
                .orElseThrow();
    }
}
