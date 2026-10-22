package com.syaru.ae2craftingoptimizer.api.big;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.*;
import com.syaru.ae2craftingoptimizer.api.batch.ExactPatternFormula;
import com.syaru.ae2craftingoptimizer.api.vector.*;
import com.syaru.ae2craftingoptimizer.engine.Ae2CompiledCraftingGraphCache;
import com.syaru.ae2craftingoptimizer.engine.craftingtable.PhysicalCraftingTreeTransaction;
import com.syaru.ae2craftingoptimizer.engine.craftingtable.CraftingTableBatchTargetResolver;
import com.syaru.ae2craftingoptimizer.api.processing.*;
import com.syaru.ae2craftingoptimizer.api.craftingtable.*;
import com.syaru.ae2craftingoptimizer.engine.*;
import com.syaru.ae2craftingoptimizer.integration.ExactNetworkStorageBridge;
import com.syaru.ae2craftingoptimizer.optimization.ProviderPatternGenerationTracker;
import static org.mockito.ArgumentMatchers.*;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ProcessingPatternExecutionTest {
    @BeforeAll static void initialize() throws Exception {
        PhysicalCraftingLifecycleTest.bootstrap();
    }

    @org.junit.jupiter.api.AfterAll static void cleanup() {
        BigCraftingPhysicalExecutionTest.releaseRegistryProvider();
    }

    @Test void mixedProcessingAndCraftingCompletesWideRequestsWithReloadEveryTick() {
        for (BigInteger n : List.of(BigInteger.ONE, BigInteger.valueOf(Long.MAX_VALUE),
                BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE), BigInteger.TEN.pow(64),
                BigInteger.TEN.pow(1024).subtract(BigInteger.ONE).divide(BigInteger.valueOf(64)))) {
            try (var f = new PhysicalCraftingLifecycleTest.Fixture(n, true)) {
                f.finish(false);
                assertEquals(Map.of(PhysicalCraftingLifecycleTest.OUT, n), f.stock);
                assertEquals(3, f.accepts);
                assertEquals(2, f.processingRequests.size());
                for (var request : f.processingRequests) {
                    assertEquals(n, request.executions());
                    assertEquals(n, request.aggregateInputTotals().values().iterator().next());
                    assertFalse(request.pattern() instanceof appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern);
                }
                assertTrue(f.processingRequests.stream().anyMatch(r ->
                        r.aggregateExpectedOutputs().getOrDefault(PhysicalCraftingLifecycleTest.EXTRA, BigInteger.ZERO)
                                .equals(n.multiply(BigInteger.TWO))));
                assertEquals(2, f.mutations);
                assertTrue(f.jobs.isEmpty());
                assertTrue(f.transaction.escrowSnapshot().isEmpty());
            }
        }
    }

    @Test void cancellationReturnsEitherUnusedInputsOrVerifiedOutputsExactlyOnce() {
        for (boolean completed : List.of(false, true)) {
            try (var f = new PhysicalCraftingLifecycleTest.Fixture(BigInteger.TEN.pow(64), true)) {
                f.untilAccepted();
                f.ready = completed;
                f.transaction.requestCancellation();
                f.finish(true);
                assertEquals(completed ? Map.of(PhysicalCraftingLifecycleTest.PART, f.amount.multiply(BigInteger.TWO),
                                PhysicalCraftingLifecycleTest.EXTRA, f.amount.multiply(BigInteger.TWO))
                        : Map.of(PhysicalCraftingLifecycleTest.RAW, f.amount, PhysicalCraftingLifecycleTest.OTHER, f.amount), f.stock);
                assertTrue(f.jobs.isEmpty());
            }
        }
    }

    @Test void acceptedWorkSurvivesEligibilityChangesAndUnloadedTarget() {
        try (var f = new PhysicalCraftingLifecycleTest.Fixture(BigInteger.TEN.pow(64), true)) {
            f.untilAccepted();
            f.supportsProcessing = false;
            f.loaded = false;
            for (int i = 0; i < 5; i++) f.tick();
            assertEquals(2, f.processingRequests.size());
            f.loaded = true;
            f.finish(false);
            assertEquals(Map.of(PhysicalCraftingLifecycleTest.OUT, f.amount), f.stock);
        }
    }

    @Test void lostOrForeignReceiptNeverRefundsOrRedispatchesAcceptedProcessing() {
        for (boolean cancel : List.of(false, true)) for (int failure = 0; failure < 3; failure++) {
            try (var f = new PhysicalCraftingLifecycleTest.Fixture(BigInteger.TEN.pow(64), true)) {
                f.untilAccepted();
                if (failure == 0) f.jobs.clear();
                if (failure == 1) f.foreignDigest = true;
                if (failure == 2) when(f.level.getBlockEntity(net.minecraft.core.BlockPos.ZERO)).thenReturn(null);
                if (cancel) f.transaction.requestCancellation();
                f.tick();
                assertEquals(PhysicalCraftingTreeTransaction.State.QUARANTINED, f.transaction.state());
                assertTrue(f.stock.isEmpty());
                assertTrue(f.transaction.accountingSnapshot().creditedOutputs().isEmpty());
                assertEquals(2, f.processingRequests.size());
            }
        }
    }

    @Test void legacyWorkerIsNotAProcessingTargetAndProcessingWorkerIsNotAnAssembler() {
        var processing = mock(IPatternDetails.class);
        var table = mock(appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern.class);
        var oldWorker = mock(net.minecraft.world.level.block.entity.BlockEntity.class,
                withSettings().extraInterfaces(CraftingTableBatchTarget.class));
        var newWorker = mock(net.minecraft.world.level.block.entity.BlockEntity.class,
                withSettings().extraInterfaces(ProcessingPatternBatchTarget.class));
        assertNull(CraftingTableBatchTargetResolver.bind(oldWorker, processing));
        assertNull(CraftingTableBatchTargetResolver.bind(newWorker, table));
        assertNotNull(CraftingTableBatchTargetResolver.bind(oldWorker, table));
        assertNotNull(CraftingTableBatchTargetResolver.bind(newWorker, processing));
    }

    @Test void rejectedCancellationKeepsInputsOwnedAndUnconfirmedReleaseQuarantines() {
        for (boolean losesOwnership : List.of(false, true)) {
            try (var f = new PhysicalCraftingLifecycleTest.Fixture(BigInteger.TEN.pow(64), true)) {
                f.untilAccepted();
                f.ready = false;
                var target = (ProcessingPatternBatchTarget) f.block;
                doAnswer(i -> {
                    if (losesOwnership) f.jobs.remove(i.getArgument(0));
                    return false;
                }).when(target).aco$cancelProcessingPatternBatch(any(), anyString());
                f.transaction.requestCancellation();
                f.tick();
                assertEquals(losesOwnership ? PhysicalCraftingTreeTransaction.State.QUARANTINED
                        : PhysicalCraftingTreeTransaction.State.CANCELLING_THREADS, f.transaction.state());
                assertTrue(f.stock.isEmpty());
                assertTrue(f.transaction.accountingSnapshot().creditedOutputs().isEmpty());
                assertEquals(2, f.processingRequests.size());
            }
        }
    }

    @Test void wrongProcessingOutputsCannotBeCredited() {
        try (var f = new PhysicalCraftingLifecycleTest.Fixture(BigInteger.TEN.pow(64), true)) {
            f.untilAccepted();
            f.changedOutputs = true;
            f.tick();
            assertEquals(PhysicalCraftingTreeTransaction.State.QUARANTINED, f.transaction.state());
            assertTrue(f.transaction.accountingSnapshot().creditedOutputs().isEmpty());
            assertTrue(f.stock.isEmpty());
            assertEquals(0, f.acknowledgements);
        }
    }

    @Test void processingAccountingAcceptsWideCountsWithoutChangingLegacyFormula() {
        var level = mock(Level.class);
        var pattern = mock(IPatternDetails.class);
        var input = mock(IPatternDetails.IInput.class);
        var raw = AEItemKey.of(Items.IRON_INGOT);
        var out = AEItemKey.of(Items.GOLD_INGOT);
        when(input.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(raw, 3)});
        when(input.getMultiplier()).thenReturn(1L);
        when(input.isValid(raw, level)).thenReturn(true);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[]{input});
        when(pattern.getOutputs()).thenReturn(List.of(new GenericStack(out, 2)));
        when(pattern.getDefinition()).thenReturn(AEItemKey.of(Items.PAPER));
        var count = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        var plan = new PreparedVectorBatch(UUID.randomUUID(), UUID.randomUUID(), VectorResourceMode.NETWORK_STORAGE,
                out, count.multiply(BigInteger.TWO), count, 1,
                List.of(new ExactStack(raw, count.multiply(BigInteger.valueOf(3)))),
                List.of(new ExactStack(out, count.multiply(BigInteger.TWO))), List.of(), List.of("processing"),
                List.of(new ExactCraftingStep("processing", 1, count, List.of(new ExactCraftingInputSlot(raw, 3)))),
                "fixture", 1, 1);
        var graph = mock(Ae2CompiledCraftingGraphCache.Snapshot.class);
        when(graph.pattern("processing")).thenReturn(pattern);
        assertTrue(ExactPatternFormula.tryCreate(pattern, level).isEmpty(), "legacy API stays table-only");
        var identity = PhysicalCraftingTreeTransaction.capturePatternAccounting(plan, graph, level).get("processing");
        assertEquals(Map.of(raw, count.multiply(BigInteger.valueOf(3))), identity.inputTotals());
        assertEquals(Map.of(out, count.multiply(BigInteger.TWO)), identity.expectedOutputs());
    }

    @Test void realAe2ProcessingPatternPreservesComponentsFluidsAndSparseSlots() {
        var named = new net.minecraft.world.item.ItemStack(Items.IRON_INGOT);
        named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("specific component"));
        var key = AEItemKey.of(named);
        var water = AEFluidKey.of(net.minecraft.world.level.material.Fluids.WATER);
        var out = AEItemKey.of(Items.GOLD_INGOT);
        var bucket = AEItemKey.of(Items.BUCKET);
        var definition = new net.minecraft.world.item.ItemStack(Items.PAPER);
        appeng.crafting.pattern.AEProcessingPattern.encode(definition,
                Arrays.asList(new GenericStack(key, 3), null, new GenericStack(water, 1000)),
                Arrays.asList(new GenericStack(out, 2), null, new GenericStack(bucket, 1)));
        var pattern = new appeng.crafting.pattern.AEProcessingPattern(AEItemKey.of(definition));
        var formula = ExactPatternFormula.tryCreateForExecution(pattern, mock(Level.class)).orElseThrow();
        var n = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        var base = new CraftingTableBatchRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "processing-test", 0, CraftingTableBatchMode.BIG_INTEGER_JOB, pattern, n,
                formula.copyInputsPerExecution(), formula.exactSlotInputs(n), formula.outputsPerExecution(),
                formula.remainingPerExecution(), formula.exactExpectedOutputTotals(n));
        var request = new ProcessingPatternBatchRequest(base);
        assertEquals(Map.of(key, n.multiply(BigInteger.valueOf(3)), water, n.multiply(BigInteger.valueOf(1000))),
                request.aggregateInputTotals());
        assertEquals(Map.of(out, n.multiply(BigInteger.TWO), bucket, n), request.aggregateExpectedOutputs());
        assertFalse(request.aggregateInputTotals().containsKey(AEItemKey.of(Items.IRON_INGOT)));
        assertNull(pattern.getSparseInputs().get(1));
        assertSame(pattern, request.pattern());
        request.inputsPerExecution()[0].reset();
        assertEquals(base.aggregateInputTotals(), request.aggregateInputTotals());
        assertThrows(UnsupportedOperationException.class, () -> request.aggregateExpectedOutputs().clear());
        assertFalse(base.countsFitSignedLong());
    }

    @Test void deterministicProcessingPlanUsesOneWideStepAndKeepsLegacyEntryTableOnly() {
        var raw = PhysicalCraftingLifecycleTest.RAW;
        var out = PhysicalCraftingLifecycleTest.OUT;
        var compiled = new com.syaru.ae2craftingoptimizer.engine.CompiledPattern<AEKey>("processing",
                List.of(new com.syaru.ae2craftingoptimizer.engine.CompiledPattern.InputSlot<>(
                        List.of(new com.syaru.ae2craftingoptimizer.engine.CompiledPattern.Stack<>(raw, 3)))), Map.of(out, 2L), true);
        var graph = com.syaru.ae2craftingoptimizer.engine.CompiledCraftingGraph.compile(1, List.of(compiled));
        var program = com.syaru.ae2craftingoptimizer.engine.CompiledRootProgram.tryCompile(graph, out, k -> false).orElseThrow();
        var n = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        var inventory = program.captureBigInventory(k -> k.equals(raw) ? n.multiply(BigInteger.valueOf(3)) : BigInteger.ZERO, 4096);
        assertTrue(program.tryPlanDeterministicCraftingBig(n.multiply(BigInteger.TWO), inventory, 4096).isEmpty());
        var plan = com.syaru.ae2craftingoptimizer.engine.vector.VectorBatchPlanner.prepareForPhysicalExecution(
                UUID.randomUUID(), UUID.randomUUID(), program, inventory, n.multiply(BigInteger.TWO), "test", 1, 1, 4096);
        assertEquals(1, plan.craftingSteps().size());
        assertEquals(n, plan.craftingSteps().get(0).executions());
        assertEquals(List.of(new ExactStack(raw, n.multiply(BigInteger.valueOf(3)))), plan.totalInputs());
        assertEquals(List.of(new ExactStack(out, n.multiply(BigInteger.TWO))), plan.finalOutputs());
    }

    @Test void publicCpuApiPreflightsProcessingWithoutStockMutationThenCompletes() {
        var n = BigInteger.valueOf(Long.MAX_VALUE);
        try (var f = new PhysicalCraftingLifecycleTest.Fixture(n, true);
                var cache = mockStatic(Ae2CompiledCraftingGraphCache.class);
                var boundary = mockStatic(ExactNetworkStorageBridge.class)) {
            var exact = new BigCraftingPlan<AEKey>(PhysicalCraftingLifecycleTest.OUT, n,
                    Map.of("a", n, "b", n, "root", n), Map.copyOf(f.stock), Map.of(), Map.of(), 3);
            long patterns = ProviderPatternGenerationTracker.generation();
            long recipes = RecipeGenerationTracker.generation();
            var selected = SelectedBranchPhysicalPlan.prepareForPhysicalExecution(exact,
                    f.graph.graph().patterns(), patterns, recipes, 4096);
            var prepared = new Ae2BigCraftingPlanFactory.PreparedBigRootPlan(null, exact, n, patterns, recipes,
                    Ae2BigCraftingPlanFactory.ExecutionMode.EXACT_PATTERN_EXECUTOR, 0,
                    PlanningRuntimeEpoch.current(), selected.programFingerprint());
            var metadata = new BigIntegerCraftingPlan(new GenericStack(PhysicalCraftingLifecycleTest.OUT, Long.MAX_VALUE),
                    exact, Map.of(f.graph.pattern("a"), n, f.graph.pattern("b"), n, f.graph.pattern("root"), n),
                    prepared, true, selected, true);
            var facade = Ae2CraftingPlanSidecars.expose(metadata);
            cache.when(() -> Ae2CompiledCraftingGraphCache.getOrCompile(f.grid, f.level)).thenReturn(f.graph);
            boundary.when(() -> ExactNetworkStorageBridge.exactStoredAmounts(eq(f.grid), anySet()))
                    .thenReturn(Optional.of(Map.copyOf(f.stock)));
            boundary.when(() -> ExactNetworkStorageBridge.canExtractAll(eq(f.grid), anyMap(), eq(f.source))).thenReturn(true);
            boundary.when(() -> ExactNetworkStorageBridge.canInsertAll(eq(f.grid), anyMap(), eq(f.source))).thenReturn(true);
            var job = UUID.randomUUID();
            var execution = BigCraftingPhysicalExecution.prepare(facade, job, f.grid, f.level, f.source);
            assertEquals(0, f.mutations);
            assertEquals(0, f.accepts);
            assertEquals(job, execution.jobId());
            f.supportsProcessing = false;
            assertThrows(IllegalArgumentException.class,
                    () -> BigCraftingPhysicalExecution.prepare(facade, UUID.randomUUID(), f.grid, f.level, f.source));
            assertEquals(0, f.mutations);
            f.supportsProcessing = true;
            f.transaction = PhysicalCraftingTreeTransaction.load(execution.save().getCompound("transaction"));
            f.finish(false);
            assertEquals(Map.of(PhysicalCraftingLifecycleTest.OUT, n), f.stock);
            assertEquals(2, f.processingRequests.size());
            assertTrue(f.processingRequests.stream().anyMatch(r -> r.aggregateExpectedOutputs().values().stream()
                    .anyMatch(amount -> amount.compareTo(n) > 0)));
        }
    }
}
