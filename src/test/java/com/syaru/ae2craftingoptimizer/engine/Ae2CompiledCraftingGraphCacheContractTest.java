package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Ae2CompiledCraftingGraphCacheContractTest {
    private static final Path CACHE_SOURCE = Path.of(
            "src/main/java/com/syaru/ae2craftingoptimizer/engine/"
                    + "Ae2CompiledCraftingGraphCache.java");
    private static final Path PLANNER_SOURCE = Path.of(
            "src/main/java/com/syaru/ae2craftingoptimizer/engine/"
                    + "Ae2AuthoritativeCraftingPlanner.java");

    @Test
    void rootProgramCacheStoresOnlySuccessfulPrograms() throws Exception {
        String source = Files.readString(CACHE_SOURCE);

        assertTrue(source.contains(
                "Map<AEKey, CompiledRootProgram<AEKey>> rootPrograms"));
        assertFalse(source.contains(
                "Map<AEKey, Optional<CompiledRootProgram<AEKey>>> rootPrograms"));
        assertTrue(source.contains("if (compiled.program().isEmpty())"));
        assertTrue(source.contains("rootPrograms.put(root, candidate);"));
        assertFalse(source.contains("rootPrograms.put(root, compiled);"));
    }

    @Test
    void lazyCompilationRejectsAStaleGeneration() throws Exception {
        String source = Files.readString(CACHE_SOURCE);

        assertTrue(source.contains("private void requireCurrentGenerations()"));
        assertTrue(source.contains(
                "long currentPatternGeneration = ProviderPatternGenerationTracker.generation();"));
        assertTrue(source.contains(
                "long currentRecipeGeneration = RecipeGenerationTracker.generation();"));

        int rootMethod = source.indexOf(
                "public CompiledRootProgram.Outcome<AEKey> rootProgramOutcome(AEKey root)");
        int compile = source.indexOf("CompiledRootProgram.compile(", rootMethod);
        int firstGuard = source.indexOf("requireCurrentGenerations();", rootMethod);
        int secondGuard = source.indexOf("requireCurrentGenerations();", firstGuard + 1);
        assertTrue(rootMethod >= 0 && firstGuard < compile && secondGuard > compile);
    }

    @Test
    void missingProgramHasItsOwnDiagnostic() throws Exception {
        String planner = Files.readString(PLANNER_SOURCE);

        assertTrue(planner.contains("FallbackReasonCode.INCOMPLETE_GRAPH_SNAPSHOT"));
        assertTrue(planner.contains("BigIntegerPlanDeclineReason.INCOMPLETE_GRAPH_SNAPSHOT"));
        assertTrue(planner.contains("\"root program unavailable: \""));
    }
}
