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
    void rootProgramCacheIsBoundedAndRetainsTheExactOutcome() throws Exception {
        String source = Files.readString(CACHE_SOURCE);

        assertTrue(source.contains(
                "WeightedLruMap<AEKey, CompiledRootProgram.Outcome<AEKey>> rootPrograms"));
        assertFalse(source.contains(
                "Map<AEKey, Optional<CompiledRootProgram<AEKey>>> rootPrograms"));
        assertTrue(source.contains("MAXIMUM_ROOT_PROGRAMS_PER_SNAPSHOT"));
        assertTrue(source.contains("MAXIMUM_ROOT_PROGRAM_NODES_PER_SNAPSHOT"));
        assertTrue(source.contains("return rootPrograms.putIfAbsent("));
        assertTrue(source.contains("Snapshot::rootProgramWeight"));
    }

    @Test
    void immutableSnapshotIsValidatedBeforePublicationAndNotRelabeledLater() throws Exception {
        String source = Files.readString(CACHE_SOURCE);

        assertTrue(source.contains(
                "long generation = ProviderPatternGenerationTracker.generation();"));
        assertTrue(source.contains(
                "long recipeGeneration = RecipeGenerationTracker.generation();"));
        assertTrue(source.contains(
                "long configurationRevision = PlanningConfigurationRevisionTracker.current();"));
        assertTrue(source.contains(
                "ProviderPatternGenerationTracker.generation() != generation"));
        assertTrue(source.contains(
                "RecipeGenerationTracker.generation() != recipeGeneration"));
        assertTrue(source.contains(
                "!PlanningConfigurationRevisionTracker.isCurrent("));

        int rootMethod = source.indexOf(
                "public CompiledRootProgram.Outcome<AEKey> rootProgramOutcome(AEKey root)");
        int nextMethod = source.indexOf(
                "public Optional<CompiledRootProgram.Outcome<AEKey>> cachedRootProgramOutcome",
                rootMethod);
        String rootBody = source.substring(rootMethod, nextMethod);
        assertTrue(rootBody.contains("CompiledRootProgram.compile("));
        assertFalse(rootBody.contains("ProviderPatternGenerationTracker.generation()"));
        assertFalse(rootBody.contains("RecipeGenerationTracker.generation()"));
    }

    @Test
    void missingProgramHasItsOwnDiagnostic() throws Exception {
        String planner = Files.readString(PLANNER_SOURCE);

        assertTrue(planner.contains("BigIntegerPlanDeclineReason.INCOMPLETE_GRAPH_SNAPSHOT"));
        assertTrue(planner.contains("classifyRootProgramFailure(rootOutcome.failure())"));
        assertTrue(planner.contains("\"root program unavailable: \""));
    }
}
