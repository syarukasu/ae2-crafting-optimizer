package com.syaru.ae2craftingoptimizer.issue125;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Issue #125の「標準long経路の回帰」と「wideの無言待機」をソース契約で固定する。 */
class Issue125RegressionSourceTest {
    private static final Path ROOT = Path.of("src", "main", "java");

    @Test
    void standardLongPlansAreReturnedToAe2BeforeAcoOwnership() {
        String source = read("com/syaru/ae2craftingoptimizer/mixin/Ae2BigCapacityPlanSubmissionMixin.java");

        assertTrue(source.contains("exact == null || exact.fitsStandardLongExecution()"));
        assertTrue(source.contains("return;"));
    }

    @Test
    void unsupportedWidePlansAreClosedInsteadOfBeingHeldForever() {
        String source = read(
                "com/syaru/ae2craftingoptimizer/integration/Ae2BigCraftingExecutionManager.java");

        assertTrue(source.contains("PhysicalPlanSupport"));
        assertTrue(source.contains("SUBMISSION_BACKING_MISSING"));
        assertTrue(source.contains("finish(context, false)"));
        assertFalse(source.contains("if (!supportsPhysicalPlan(grid, graphSnapshot, plan))"));
    }

    @Test
    void stallDiagnosticsCarryExactExecutionState() {
        String manager = read(
                "com/syaru/ae2craftingoptimizer/integration/Ae2BigCraftingExecutionManager.java");
        String transaction = read(
                "com/syaru/ae2craftingoptimizer/engine/craftingtable/PhysicalCraftingTreeTransaction.java");

        assertTrue(manager.contains("remainingOperations"));
        assertTrue(manager.contains("remainingPhysicalSteps"));
        assertTrue(manager.contains("finalOutputRemaining"));
        assertTrue(transaction.contains("ExecutionDiagnostics"));
        assertTrue(transaction.contains("CraftingTableBatchTargetResolver"));
        assertFalse(transaction.contains("waiting for a NeoECO crafting-table Pattern Bus"));
    }

    @Test
    void targetResolutionUsesOnlyTheGenericAcoContract() {
        String source = read(
                "com/syaru/ae2craftingoptimizer/engine/craftingtable/CraftingTableBatchTargetResolver.java");

        assertTrue(source.contains("CraftingTableBatchTarget"));
        assertTrue(source.contains("PatternProviderTargetAccess"));
        assertFalse(source.contains("aco$stageOwnedBatch"));
        assertTrue(source.contains("ProviderOwnedPatternBatchTarget"));
        assertFalse(source.contains("NeoECO"));
    }

    private static String read(String relativePath) {
        try {
            return Files.readString(ROOT.resolve(relativePath));
        } catch (IOException exception) {
            throw new UncheckedIOException(relativePath, exception);
        }
    }
}
