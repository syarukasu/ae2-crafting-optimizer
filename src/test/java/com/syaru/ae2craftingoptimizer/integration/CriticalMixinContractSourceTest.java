package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** BigInteger正本を支える必須Mixinと通常AE2境界の分離を固定する。 */
class CriticalMixinContractSourceTest {
    private static final Path PRODUCTION_ROOT = Path.of("src", "main", "java");
    private static final Path MIXIN_ROOT = PRODUCTION_ROOT.resolve(
            Path.of("com", "syaru", "ae2craftingoptimizer", "mixin"));
    private static final Path VALIDATOR = PRODUCTION_ROOT.resolve(
            Path.of("com", "syaru", "ae2craftingoptimizer", "integration",
                    "ExperimentalCompatibilityValidator.java"));

    @Test
    void criticalMixinsDeclareFailClosedInjectionCountsAndAuditMarkers() {
        Map<String, Contract> contracts = Map.of(
                "CraftingServiceCalculationDeduplicationMixin.java",
                new Contract("CraftingServiceCalculationHookAccess", 2L),
                "CraftingCalculationCheckedMathMixin.java",
                new Contract("CheckedCraftingArithmeticHookAccess", 1L),
                "CraftingTreeNodeCheckedMathMixin.java",
                new Contract("CheckedCraftingArithmeticHookAccess", 1L),
                "CraftingTreeProcessCheckedMathMixin.java",
                new Contract("CheckedCraftingArithmeticHookAccess", 1L),
                "CraftingSimulationStateCheckedMathMixin.java",
                new Contract("CheckedCraftingArithmeticHookAccess", 4L),
                "KeyCounterBigIntegerSidecarLifecycleMixin.java",
                new Contract("ExactBigIntegerInventoryHookAccess", 1L));

        contracts.forEach((fileName, contract) -> {
            String source = read(MIXIN_ROOT.resolve(fileName));
            assertTrue(
                    source.contains("implements " + contract.marker()),
                    fileName + " must expose its runtime audit marker");
            assertEquals(
                    contract.requiredInjectionCount(),
                    source.lines().filter(line -> line.contains("require =")).count(),
                    fileName + " must fail closed at every verified injection point");
        });
    }

    @Test
    void exactInventoryCaptureDoesNotHookTheSharedAe2InventoryPath() {
        String mixinConfig = read(Path.of(
                "src", "main", "resources", "ae2_crafting_optimizer.mixins.json"));
        assertFalse(mixinConfig.contains("NetworkStorageBigIntegerSnapshotMixin"));
        assertFalse(mixinConfig.contains("NetworkCraftingSimulationStateBigIntegerSnapshotMixin"));
        assertFalse(mixinConfig.contains("StorageServiceExactSnapshotInvalidationMixin"));
        assertTrue(read(PRODUCTION_ROOT.resolve(Path.of(
                "com", "syaru", "ae2craftingoptimizer", "integration",
                "PlanningExactInventorySnapshot.java")))
                .contains("BigIntegerStorageSnapshotBridge.collect"));
    }

    @Test
    void compatibilityAuditCoversTheSharedPlanningCoreAndCriticalSurfaces() {
        String source = read(VALIDATOR);
        assertTrue(source.contains("enableCompiledCraftingGraph()"));
        assertFalse(source.contains("enableAqeBigCraftingProfile()"));
        assertFalse(source.contains("enableExternalBigCraftingProfile()"));
        assertTrue(source.contains("CraftingServiceCalculationHookAccess.class"));
        assertTrue(source.contains("CheckedCraftingArithmeticHookAccess.class"));
        assertTrue(source.contains("ExactBigIntegerInventoryHookAccess.class"));
        assertTrue(source.contains("NetworkStorageMountsAccess.class"));
    }

    @Test
    void verifiedAe2CoreOptimizationsDoNotSilentlyDisableTheirInjectionPoints() {
        Map<String, Long> requiredInjectionLines = Map.of(
                "CraftingCpuLogicTransactionalBatchV2Mixin.java", 1L,
                "CraftingTreeCalculationMemoMixin.java", 7L,
                "CraftingCpuHelperCalculationMemoMixin.java", 2L);

        requiredInjectionLines.forEach((fileName, requiredLines) -> {
            String source = read(MIXIN_ROOT.resolve(fileName));
            assertEquals(
                    requiredLines,
                    source.lines().filter(line -> line.contains("require =")).count(),
                    fileName + " must declare every verified injection count");
            assertFalse(
                    source.contains("require = 0"),
                    fileName + " must not silently disable an AE2 core hook");
        });
    }

    @Test
    void issue167KeepsAe2CandidateOwnershipInsideAe2() throws IOException {
        String mixinConfig = Files.readString(
                Path.of("src/main/resources/ae2_crafting_optimizer.mixins.json"),
                StandardCharsets.UTF_8);
        // Issue #167: AE2の候補集合を削るMixinと世代外global cacheは再導入しない。
        assertFalse(mixinConfig.contains("CraftingTreeCandidatePruningMixin"));
        assertFalse(mixinConfig.contains("CraftingServicePatternLookupCacheMixin"));
        assertFalse(Files.exists(MIXIN_ROOT.resolve("CraftingTreeCandidatePruningMixin.java")));
        assertFalse(Files.exists(MIXIN_ROOT.resolve("CraftingServicePatternLookupCacheMixin.java")));
    }

    @Test
    void executionBudgetYieldsToHigherPriorityCraftingOwnersWithoutSilentlyDisappearing() {
        String source = read(MIXIN_ROOT.resolve("CraftingCpuLogicExecutionBudgetMixin.java"));

        // issue #102/#123: 専門アドオンの実行所有権をACOが奪わない優先度を固定する。
        assertTrue(source.contains("priority = 900"));
        // 競合時だけRedirect 0件を許可し、Mixin debug環境では期待件数1を監査する。
        assertEquals(1L, source.lines().filter(line -> line.contains("require = 0")).count());
        assertEquals(1L, source.lines().filter(line -> line.contains("expect = 1")).count());
        // 競合しないexecuteCrafting計測境界は引き続き必須とする。
        assertEquals(1L, source.lines().filter(line -> line.contains("require = 1")).count());
        assertTrue(source.contains("if (!aco$ownsExecutionBudgetHook)"));
        assertTrue(source.contains("return logic.executeCrafting(maxOperations"));
    }

    @Test
    void broadThrowableCatchIsLimitedToTheMethodHandleInvocationBoundary() throws IOException {
        List<String> offenders;
        try (Stream<Path> files = Files.walk(PRODUCTION_ROOT)) {
            offenders = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> read(path).contains("catch (Throwable"))
                    .map(PRODUCTION_ROOT::relativize)
                    .map(path -> path.toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        }
        assertEquals(
                List.of("com/syaru/ae2craftingoptimizer/optimization/MethodHandleInvocationCache.java"),
                offenders);
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }

    private record Contract(String marker, long requiredInjectionCount) {
    }
}
