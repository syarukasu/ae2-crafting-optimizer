package com.syaru.ae2craftingoptimizer.issue125;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.stream.IntStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Issue #125の「標準long経路の回帰」と「wideの無言待機」をソース契約で固定する。 */
class Issue125RegressionSourceTest {
    private static final Path ROOT = Path.of("src", "main", "java");

    @Test
    void nativeBudgetStorePrecedesOuterBatchCallAndFollowsAe2Cancellation() throws IOException {
        var owner = new ClassNode();
        try (var bytes = getClass().getResourceAsStream("/appeng/crafting/execution/CraftingCpuLogic.class")) {
            new ClassReader(bytes).accept(owner, 0);
        }
        var tick = owner.methods.stream().filter(method -> method.name.equals("tickCraftingLogic"))
                .findFirst().orElseThrow();
        var instructions = Arrays.asList(tick.instructions.toArray());
        int store = IntStream.range(0, instructions.size())
                .filter(index -> instructions.get(index).getOpcode() == Opcodes.ISTORE).findFirst().orElseThrow();
        assertEquals(3, ((VarInsnNode) instructions.get(store)).var);
        assertTrue(instructions.subList(0, store).stream()
                .anyMatch(instruction -> instruction instanceof MethodInsnNode call && call.name.equals("isCanceled")));
        assertTrue(instructions.subList(store + 1, instructions.size()).stream()
                .anyMatch(instruction -> instruction instanceof MethodInsnNode call && call.name.equals("executeCrafting")));
        String mixin = read("com/syaru/ae2craftingoptimizer/mixin/Ae2ExactCraftingLogicMixin.java");
        String hook = mixin.substring(mixin.indexOf("@ModifyVariable"), mixin.indexOf("@Inject"));
        assertTrue(hook.contains("method = \"tickCraftingLogic\""));
        assertTrue(hook.contains("@At(value = \"STORE\", ordinal = 0)"));
        assertTrue(hook.contains("ordinal = 0") && hook.contains("require = 1"));
        assertTrue(hook.contains("CraftingExecutionBudget.nativeTickOperations(job, availableOperations)"));
    }

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
    void snapshotCaptureStalenessWaitsBeforeAnyPhysicalExecution() {
        String source = read(
                "com/syaru/ae2craftingoptimizer/integration/Ae2BigCraftingExecutionManager.java");
        String capture = source.substring(
                source.indexOf("Ae2CompiledCraftingGraphCache.Snapshot graphSnapshot;"),
                source.indexOf("restoreOrStart(context, grid, graphSnapshot)"));
        assertTrue(capture.indexOf("try {") < capture.indexOf("getOrCompile("));
        int deferred = capture.indexOf("catch (StalePlanningSnapshotException deferred)");
        assertTrue(deferred > capture.indexOf("getOrCompile("));
        String wait = capture.substring(deferred);
        assertTrue(wait.contains("waiting for a stable crafting snapshot"));
        assertTrue(wait.contains("return true;"));
        assertFalse(wait.contains("quarantine(") || wait.contains("finish("));
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
