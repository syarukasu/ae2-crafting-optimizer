package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Issue #164: ACOの中核責務と外部MODの実行所有権の境界を固定する。 */
class Issue164CoreBoundarySourceTest {
    private static final Path MAIN = Path.of("src", "main", "java");
    private static final Path MIXIN_CONFIG = Path.of(
            "src", "main", "resources", "ae2_crafting_optimizer.mixins.json");

    @Test
    void lifecycleOwnsOnlyStandardAe2ExactExecution() throws IOException {
        String lifecycle = readMain(
                "com/syaru/ae2craftingoptimizer/lifecycle/ACOServerLifecycle.java");

        assertTrue(lifecycle.contains("Ae2BigCraftingExecutionManager.tick"));
        assertTrue(lifecycle.contains("Ae2BigCraftingExecutionManager.clear"));
        assertFalse(lifecycle.contains("AqeBigCraftingExecutionManager"));
        assertFalse(lifecycle.contains("OptionalAqeBigCraftingExecution"));
        assertFalse(lifecycle.contains("OptionalNativeBatchIntegrations"));
    }

    @Test
    void legacyV1AndBuiltInMachineAdaptersStayRemoved() throws IOException {
        String entrypoint = readMain(
                "com/syaru/ae2craftingoptimizer/AE2CraftingOptimizer.java");

        assertTrue(entrypoint.contains("PatternBatchV2Api.registerBuiltIns"));
        assertFalse(entrypoint.contains("PatternBatchApi"));
        assertFalse(existsMain("com/syaru/ae2craftingoptimizer/api/batch/PatternBatchApi.java"));
        assertFalse(existsMain("com/syaru/ae2craftingoptimizer/optimization/BatchedCraftingExecutor.java"));
        assertFalse(existsMain("com/syaru/ae2craftingoptimizer/gtceu/GTCEuNativePatternBatchAdapter.java"));
        assertFalse(existsMain("com/syaru/ae2craftingoptimizer/mekanism/MekanismNativePatternBatchAdapter.java"));
    }

    @Test
    void providerAndMixinBoundariesRemainReadOnlyAndNarrow() throws IOException {
        String access = readMain(
                "com/syaru/ae2craftingoptimizer/access/PatternProviderTargetAccess.java");
        String mixins = Files.readString(MIXIN_CONFIG, StandardCharsets.UTF_8);

        assertTrue(access.contains("aco$getProviderTargets"));
        assertFalse(access.contains("aco$stageOwnedBatch"));
        assertFalse(access.contains("NativeBatchReceipt"));
        assertTrue(mixins.contains("PatternProviderLogicTargetAccessMixin"));
        assertFalse(mixins.contains("NativeBatchReceiptMixin"));
        assertFalse(mixins.contains("AdvancedAeExactCraftingLogicMixin"));
        assertFalse(mixins.contains("ExportBusCandidateCacheMixin"));
        assertFalse(mixins.contains("ClientRepoUpdateCoalescingMixin"));
    }

    @Test
    void exactInventorySnapshotStaysInsidePlanningBoundary() throws IOException {
        String mixins = Files.readString(MIXIN_CONFIG, StandardCharsets.UTF_8);

        assertTrue(existsMain(
                "com/syaru/ae2craftingoptimizer/integration/PlanningExactInventorySnapshot.java"));
        assertFalse(mixins.contains("NetworkStorageBigIntegerSnapshotMixin"));
        assertFalse(mixins.contains("NetworkCraftingSimulationStateBigIntegerSnapshotMixin"));
        assertFalse(mixins.contains("StorageServiceExactSnapshotInvalidationMixin"));
        assertFalse(existsMain(
                "com/syaru/ae2craftingoptimizer/integration/ExactNetworkStorageSnapshotCache.java"));
        assertFalse(existsMain(
                "com/syaru/ae2craftingoptimizer/integration/GridStorageSnapshotBridge.java"));
    }

    @Test
    void unreachableInternalCompatibilityTypesStayRemoved() {
        assertFalse(existsMain(
                "com/syaru/ae2craftingoptimizer/access/BigCapacityPlanBoundaryAccess.java"));
        assertFalse(existsMain(
                "com/syaru/ae2craftingoptimizer/batch/ExactPatternSnapshot.java"));
        assertFalse(existsMain(
                "com/syaru/ae2craftingoptimizer/engine/vector/VectorInventorySnapshot.java"));
    }

    private static String readMain(String relativePath) throws IOException {
        return Files.readString(MAIN.resolve(relativePath), StandardCharsets.UTF_8);
    }

    private static boolean existsMain(String relativePath) {
        return Files.exists(MAIN.resolve(relativePath));
    }
}
