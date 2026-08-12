package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** BigInteger正本を支える必須Mixinが、将来の編集で静かに任意化されることを防ぐ。 */
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
                "NetworkCraftingSimulationStateBigIntegerSnapshotMixin.java",
                new Contract("ExactBigIntegerInventoryHookAccess", 1L),
                "NetworkStorageBigIntegerSnapshotMixin.java",
                new Contract("ExactBigIntegerInventoryHookAccess", 6L),
                "KeyCounterBigIntegerSidecarLifecycleMixin.java",
                new Contract("ExactBigIntegerInventoryHookAccess", 1L),
                "StorageServiceExactSnapshotInvalidationMixin.java",
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
    void compatibilityAuditCoversBothBigIntegerProfilesAndCriticalSurfaces() {
        String source = read(VALIDATOR);
        assertTrue(source.contains("enableBigCraftingProfile()"));
        assertTrue(source.contains("CraftingServiceCalculationHookAccess.class"));
        assertTrue(source.contains("CheckedCraftingArithmeticHookAccess.class"));
        assertTrue(source.contains("ExactBigIntegerInventoryHookAccess.class"));
    }

    @Test
    void verifiedAe2CoreOptimizationsDoNotSilentlyDisableTheirInjectionPoints() {
        Map<String, Long> requiredInjectionLines = Map.of(
                "CraftingCpuLogicExecutionBudgetMixin.java", 2L,
                "CraftingCpuLogicTransactionalBatchV2Mixin.java", 1L,
                "CraftingTreeCalculationMemoMixin.java", 4L,
                "CraftingTreeCandidatePruningMixin.java", 1L,
                "StorageServiceDeepCoalescingMixin.java", 4L,
                "StorageServiceWatcherThrottleMixin.java", 6L);

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
