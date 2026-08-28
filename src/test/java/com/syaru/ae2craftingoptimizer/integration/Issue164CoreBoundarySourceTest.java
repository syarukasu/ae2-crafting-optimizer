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
    private static final Path MIXIN_ROOT = Path.of("src", "main", "resources");

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
        String mixins = readAllMixinConfigs();

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
        String mixins = readAllMixinConfigs();

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
        assertFalse(existsMain(
                "com/syaru/ae2craftingoptimizer/batch/ExactMultisetMatcher.java"));
        assertFalse(existsMain(
                "com/syaru/ae2craftingoptimizer/engine/CompiledPlanningSession.java"));
        assertFalse(existsMain(
                "com/syaru/ae2craftingoptimizer/engine/GenerationAwareGraphCache.java"));
        assertFalse(existsMain(
                "com/syaru/ae2craftingoptimizer/engine/vector/LongClampedProgressProjection.java"));
        assertFalse(existsMain(
                "com/syaru/ae2craftingoptimizer/optimization/GenerationSlotCache.java"));
        assertFalse(existsMain(
                "com/syaru/ae2craftingoptimizer/transaction/BatchConservationLedger.java"));
    }

    private static String readMain(String relativePath) throws IOException {
        return Files.readString(MAIN.resolve(relativePath), StandardCharsets.UTF_8);
    }

    private static String readAllMixinConfigs() throws IOException {
        try (var paths = Files.list(MIXIN_ROOT)) {
            var configs = paths
                    .filter(path -> path.getFileName().toString().endsWith("mixins.json"))
                    .sorted()
                    .toList();
            StringBuilder combined = new StringBuilder();
            // 分割された全Mixin設定を連結し、別domainへの再登録も同じ境界試験で検出する。
            for (Path config : configs) {
                combined.append(Files.readString(config, StandardCharsets.UTF_8)).append('\n');
            }
            return combined.toString();
        }
    }

    private static boolean existsMain(String relativePath) {
        return Files.exists(MAIN.resolve(relativePath));
    }
}
