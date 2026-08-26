package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Issue #156の高速計算境界とAE2時間分割契約をソース上でも固定する。 */
class Issue156PlanningAccelerationSourceTest {
    private static final Path JAVA = Path.of("src", "main", "java", "com", "syaru", "ae2craftingoptimizer");

    @Test
    void deterministicPlannerIsIndependentFromTheExperimentalEngine() {
        String config = read(JAVA.resolve(Path.of("config", "ACOConfig.java")));

        assertTrue(config.contains("enableStrictDeterministicLongPlanner"));
        assertTrue(config.contains(".define(\"enableStrictDeterministicLongPlanner\", true)"));
        assertTrue(config.contains("enableStrictDeterministicLongPlanner()"));
    }

    @Test
    void coldCompilationUsesReachableRootsAndAe2sOwnPauseContract() {
        String planner = read(JAVA.resolve(Path.of("engine", "Ae2AuthoritativeCraftingPlanner.java")));
        String calculationMixin = read(JAVA.resolve(Path.of("mixin", "CraftingCalculationDiagnosticsMixin.java")));

        assertTrue(planner.contains("getOrCompileRoot("));
        assertTrue(planner.contains("workBudget.checkpoint(expanded)"));
        assertTrue(calculationMixin.contains("this::aco$honorAe2PlanningBudget"));
        assertTrue(calculationMixin.contains("handlePausing();"));
        assertFalse(calculationMixin.contains("Thread.sleep"));
    }

    @Test
    void completedPlanInvalidationObservesStorageWithoutRedirectingTransfers() {
        String networkMixin = read(JAVA.resolve(Path.of(
                "mixin", "NetworkStorageCraftingPlanGenerationMixin.java")));
        String storageMixin = read(JAVA.resolve(Path.of(
                "mixin", "StorageServiceCraftingPlanGenerationMixin.java")));

        assertTrue(networkMixin.contains("mode != Actionable.MODULATE"));
        assertTrue(networkMixin.contains("onStorageChange();"));
        assertFalse(networkMixin.contains("@Redirect"));
        assertFalse(storageMixin.contains("@Redirect"));
    }

    @Test
    void calculationMemoIsClearedFromAe2sFinallyPath() {
        String lifecycle = read(JAVA.resolve(Path.of(
                "mixin", "CraftingCalculationMemoLifecycleMixin.java")));

        assertTrue(lifecycle.contains("method = \"finish\""));
        assertTrue(lifecycle.contains("CraftingCalculationMemo.end(this)"));
        assertFalse(lifecycle.contains("method = \"run\", at = @At(\"RETURN\")"));
    }

    @Test
    void rootRetryCannotReuseTheSameFullNetworkSnapshot() {
        String cache = read(JAVA.resolve(Path.of(
                "engine", "Ae2CompiledCraftingGraphCache.java")));
        int methodStart = cache.indexOf("public static Snapshot recompileRoot(\n            IGrid grid,");
        int methodEnd = cache.indexOf("\n    public static void clear()", methodStart);
        String recompileRoot = cache.substring(methodStart, methodEnd);

        assertTrue(recompileRoot.contains("stale.isRootScopedFor(root)"));
        assertTrue(recompileRoot.contains("compileReachable("));
        assertFalse(recompileRoot.contains("getOrCompileRoot("));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
