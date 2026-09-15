package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Issue #179: AE2の計算workerと外部機械の所有権を二重化しない。 */
class Issue179PlanningBoundarySourceTest {
    private static final Path MAIN = Path.of("src", "main", "java");
    private static final Path RESOURCES = Path.of("src", "main", "resources");

    @Test
    void planningUsesAe2WorkerWithoutASecondExecutor() throws IOException {
        String planner = readMain(
                "com/syaru/ae2craftingoptimizer/engine/Ae2AuthoritativeCraftingPlanner.java");
        String lifecycle = readMain(
                "com/syaru/ae2craftingoptimizer/lifecycle/ACOServerLifecycle.java");

        assertTrue(planner.contains("PlanningPreparation preparation = preparePlanning("));
        assertTrue(planner.contains("PlanningGuard guard = planningGuard(workerYield);"));
        assertTrue(planner.contains("immutableCapture.compile(guard)"));
        assertTrue(planner.contains("workerYield.yieldToServerThread();"));
        assertFalse(planner.contains("awaitPlanningTask("));
        assertFalse(lifecycle.contains("startPlanningExecutor"));
        assertFalse(lifecycle.contains("stopPlanningExecutor"));
        assertFalse(existsMain(
                "com/syaru/ae2craftingoptimizer/engine/AsyncPlanningExecutor.java"));

        String mixin = readMain("com/syaru/ae2craftingoptimizer/mixin/CraftingCalculationDiagnosticsMixin.java");
        assertTrue(mixin.contains("aco$detachedPlanning = true;"));
        assertTrue(mixin.contains("Ae2AuthoritativeCraftingPlanner.tryPlanDetached("));
        assertTrue(mixin.contains("aco$waitOnlyForLiveAe2Work(Object monitor)"));
        assertTrue(mixin.contains("incTime = Integer.MAX_VALUE;"));
        assertTrue(mixin.contains("this::aco$reattachPlanning"));
        assertTrue(planner.indexOf("workerYield.beforeResult();")
                < planner.lastIndexOf("// Emitterまたはrecipe世代が変わった場合も"));
        assertFalse(mixin.contains("registerCraftingSimulation"));
        assertTrue(lifecycle.contains("PlanningServerTasks.stop(event.getServer())"));
        assertTrue(mixin.contains("snapshot.supportsDetachedAe2Planning("));
        assertTrue(mixin.contains("snapshot.isCurrent()"));
        assertTrue(mixin.contains("PlanningServerTasks.registerWorker("));
        assertTrue(mixin.contains("PlanningServerTasks.releaseWorker("));
        String tree = readMain("com/syaru/ae2craftingoptimizer/mixin/CraftingTreeCalculationMemoMixin.java");
        assertTrue(tree.contains("nodes == null"));
        assertTrue(tree.contains(".aco$runPatternLookup(this::aco$buildChildPatterns)"));
        assertTrue(tree.contains(".aco$observeCraftingService(service)"));
        assertTrue(mixin.contains("snapshot.isCurrent() && aco$nativeBindingCurrent"));
    }

    @Test
    void mekanismKeepsItsNativeRecipeLookup() throws IOException {
        String mixins = readAllMixinConfigs();

        assertFalse(mixins.contains("MekanismRecipeIntentFastPathMixin"));
        assertFalse(mixins.contains("MekanismCachedRecipeAccessor"));
        assertFalse(existsMain(
                "com/syaru/ae2craftingoptimizer/mekanism/MekanismRecipeIntentFastPath.java"));
        assertFalse(existsMain(
                "com/syaru/ae2craftingoptimizer/access/MekanismCachedRecipeAccess.java"));
    }

    private static String readMain(String relativePath) throws IOException {
        return Files.readString(MAIN.resolve(relativePath), StandardCharsets.UTF_8);
    }

    private static String readAllMixinConfigs() throws IOException {
        StringBuilder combined = new StringBuilder();
        try (var configs = Files.list(RESOURCES)) {
            for (Path config : configs
                    .filter(path -> path.getFileName().toString().endsWith("mixins.json"))
                    .sorted()
                    .toList()) {
                combined.append(Files.readString(config, StandardCharsets.UTF_8));
            }
        }
        return combined.toString();
    }

    private static boolean existsMain(String relativePath) {
        return Files.exists(MAIN.resolve(relativePath));
    }
}
