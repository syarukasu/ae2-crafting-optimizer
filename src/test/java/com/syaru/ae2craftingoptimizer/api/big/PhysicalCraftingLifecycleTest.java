package com.syaru.ae2craftingoptimizer.api.big;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.api.batch.v2.ProviderOwnedPatternBatchTarget;
import com.syaru.ae2craftingoptimizer.api.craftingtable.*;
import com.syaru.ae2craftingoptimizer.api.vector.*;
import com.syaru.ae2craftingoptimizer.engine.*;
import com.syaru.ae2craftingoptimizer.engine.craftingtable.PhysicalCraftingTreeTransaction;
import com.syaru.ae2craftingoptimizer.scheduler.PatternProviderRoutingCache;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** Production transaction and codec; world, storage and worker are contract doubles, not a GameTest. */
class PhysicalCraftingLifecycleTest {
    static AEKey RAW, OTHER, PART, EXTRA, OUT;

    @BeforeAll static void bootstrap() throws Exception {
        BigCraftingPhysicalExecutionTest.initializeRegistryAndConfig();
        RAW = AEItemKey.of(Items.COBBLESTONE);
        OTHER = AEItemKey.of(Items.DIRT);
        PART = AEItemKey.of(Items.IRON_INGOT);
        EXTRA = AEItemKey.of(Items.GOLD_INGOT);
        OUT = AEItemKey.of(Items.DIAMOND);
    }

    @org.junit.jupiter.api.AfterAll static void cleanupRegistry() {
        BigCraftingPhysicalExecutionTest.releaseRegistryProvider();
    }

    @Test void completesExactBranchesWithReloadAfterEveryTick() throws Exception {
        for (BigInteger amount : List.of(BigInteger.ONE, BigInteger.valueOf(100),
                BigInteger.valueOf(Long.MAX_VALUE), BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE),
                BigInteger.TEN.pow(64))) {
            try (var f = new Fixture(amount)) {
                f.finish(false);
                assertEquals(Map.of(OUT, amount), f.stock);
                assertEquals(3, f.accepts, "one accepted job per selected pattern, independent of quantity");
                assertEquals(2, f.mutations, "one input batch and one final output batch");
                assertTrue(f.transaction.escrowSnapshot().isEmpty());
                assertTrue(f.jobs.isEmpty());
                assertTrue(f.transaction.accountingSnapshot().finalOutputReturned());
            }
        }
    }

    @Test void aqeScaleBranchesCompleteAndCancelWithExactSavedCounts() throws Exception {
        for (int digits : List.of(1024, BigCountMath.HARD_MAXIMUM_DECIMAL_DIGITS)) {
            BigInteger amount = BigInteger.TEN.pow(digits).subtract(BigInteger.ONE).divide(BigInteger.valueOf(64));
            for (int mode = 0; mode < 3; mode++) {
                try (var f = new Fixture(amount)) {
                    boolean cancel = mode != 0;
                    if (cancel) {
                        f.untilAccepted();
                        f.ready = mode == 2;
                        assertTrue(f.transaction.requestCancellation());
                    }
                    f.finish(cancel);
                    var expected = mode == 0 ? Map.of(OUT, amount)
                            : mode == 1 ? Map.of(RAW, amount, OTHER, amount)
                            : Map.of(PART, amount.multiply(BigInteger.TWO), EXTRA, amount.multiply(BigInteger.TWO));
                    assertEquals(expected, f.stock);
                    assertEquals(cancel ? 2 : 3, f.accepts);
                    assertEquals(2, f.mutations);
                    assertTrue(f.jobs.isEmpty());
                    assertTrue(f.transaction.escrowSnapshot().isEmpty());
                }
            }
        }
    }

    @Test void unloadedWorkersWaitWithoutLosingOrDuplicatingOwnedInputs() throws Exception {
        try (var f = new Fixture(BigInteger.TEN.pow(64))) {
            f.untilAccepted();
            f.loaded = false;
            for (int i = 0; i < 10; i++) f.tick();
            assertEquals(PhysicalCraftingTreeTransaction.State.EXECUTING_RECIPES, f.transaction.state());
            assertEquals(2, f.accepts);
            assertTrue(f.transaction.accountingSnapshot().creditedOutputs().isEmpty());
            f.loaded = true;
            f.finish(false);
            assertEquals(Map.of(OUT, f.amount), f.stock);
            assertEquals(3, f.accepts);
        }
    }

    @Test void cancellationReturnsInputsOrCompletedOutputsButNeverBoth() throws Exception {
        for (boolean completed : List.of(false, true)) {
            try (var f = new Fixture(BigInteger.TEN.pow(64))) {
                f.untilAccepted();
                f.ready = completed;
                assertTrue(f.transaction.requestCancellation());
                f.finish(true);
                Map<AEKey, BigInteger> expected = completed
                        ? Map.of(PART, f.amount.multiply(BigInteger.TWO), EXTRA, f.amount.multiply(BigInteger.TWO))
                        : Map.of(RAW, f.amount, OTHER, f.amount);
                assertEquals(expected, f.stock);
                assertFalse(f.stock.containsKey(OUT));
                assertTrue(f.jobs.isEmpty());
                assertTrue(f.transaction.escrowSnapshot().isEmpty());
            }
        }
    }

    @Test void foreignReceiptIsRejectedBeforeOutputCreditOrWorkerRelease() throws Exception {
        for (boolean wrongId : List.of(false, true)) for (boolean cancel : List.of(false, true)) {
            try (var f = new Fixture(BigInteger.TEN.pow(64))) {
                f.untilAccepted();
                f.foreignId = wrongId;
                f.foreignDigest = !wrongId;
                if (cancel) {
                    f.transaction.requestCancellation();
                }
                f.tick();
                assertEquals(PhysicalCraftingTreeTransaction.State.QUARANTINED, f.transaction.state());
                assertTrue(f.transaction.accountingSnapshot().creditedOutputs().isEmpty());
                assertEquals(0, f.acknowledgements);
                assertEquals(2, f.jobs.size());
                assertTrue(f.stock.isEmpty());
            }
        }
    }

    @Test void foreignReceiptAfterCreditCannotReleaseOwnedWorker() throws Exception {
        for (boolean wrongId : List.of(false, true)) for (boolean cancel : List.of(false, true)) {
            try (var f = new Fixture(BigInteger.TEN.pow(64))) {
                f.untilCredited();
                var credited = f.transaction.accountingSnapshot().creditedOutputs();
                var creditedJobs = Set.copyOf(f.acknowledged);
                f.foreignId = wrongId;
                f.foreignDigest = !wrongId;
                if (cancel) {
                    f.transaction.requestCancellation();
                }
                f.tick();
                assertEquals(PhysicalCraftingTreeTransaction.State.QUARANTINED, f.transaction.state());
                assertEquals(credited, f.transaction.accountingSnapshot().creditedOutputs());
                assertTrue(f.jobs.keySet().containsAll(creditedJobs));
                assertEquals(2, f.acknowledgements);
                assertTrue(f.stock.isEmpty());
            }
        }
    }

    @Test void changedCreditedOutputsCannotReleaseWorker() throws Exception {
        for (boolean cancel : List.of(false, true)) {
            try (var f = new Fixture(BigInteger.TEN.pow(64))) {
                f.untilCredited();
                var credited = f.transaction.accountingSnapshot().creditedOutputs();
                var creditedJobs = Set.copyOf(f.acknowledged);
                f.changedOutputs = true;
                if (cancel) {
                    f.transaction.requestCancellation();
                }
                f.tick();
                assertEquals(PhysicalCraftingTreeTransaction.State.QUARANTINED, f.transaction.state());
                assertEquals(credited, f.transaction.accountingSnapshot().creditedOutputs());
                assertTrue(f.jobs.keySet().containsAll(creditedJobs));
                assertTrue(f.stock.isEmpty());
            }
        }
    }

    @Test void laggingWorkerSaveWaitsAndResumesWithoutCreditOrReleaseTwice() throws Exception {
        for (boolean cancel : List.of(false, true)) {
            try (var f = new Fixture(BigInteger.TEN.pow(64))) {
                f.untilCredited();
                var credited = f.transaction.accountingSnapshot().creditedOutputs();
                var creditedJobs = Set.copyOf(f.acknowledged);
                f.acknowledged.clear();
                f.ready = false;
                f.staleRunning = true;
                if (cancel) f.transaction.requestCancellation();
                for (int i = 0; i < 6; i++) f.tick();
                assertEquals(cancel ? PhysicalCraftingTreeTransaction.State.CANCELLING_THREADS
                        : PhysicalCraftingTreeTransaction.State.EXECUTING_RECIPES, f.transaction.state());
                assertEquals(credited, f.transaction.accountingSnapshot().creditedOutputs());
                assertTrue(f.jobs.keySet().containsAll(creditedJobs));
                assertEquals(2, f.acknowledgements);
                assertEquals(0, f.forgotten);
                assertTrue(f.stock.isEmpty());
                f.ready = true;
                f.staleRunning = false;
                f.finish(cancel);
                var expected = cancel
                        ? Map.of(PART, f.amount.multiply(BigInteger.TWO), EXTRA, f.amount.multiply(BigInteger.TWO))
                        : Map.of(OUT, f.amount);
                assertEquals(expected, f.stock);
                assertEquals(3, f.accepts);
                assertTrue(f.jobs.isEmpty());
            }
        }
    }

    @Test void retryAcknowledgementValidatesTheNewReceiptBeforeForgetting() throws Exception {
        for (boolean cancel : List.of(false, true)) {
            try (var f = new Fixture(BigInteger.TEN.pow(64))) {
                f.untilAccepted();
                f.denyAcknowledgement = true;
                while (f.acknowledgements < 2) f.tick();
                assertTrue(f.acknowledged.isEmpty());
                if (cancel) {
                    f.transaction.requestCancellation();
                }
                f.denyAcknowledgement = false;
                f.corruptOnAcknowledgement = true;
                f.tick();
                assertEquals(PhysicalCraftingTreeTransaction.State.QUARANTINED, f.transaction.state());
                assertEquals(0, f.forgotten);
                assertTrue(f.stock.isEmpty());
                assertFalse(f.jobs.isEmpty());
            }
        }
    }

    @Test void delayedAcknowledgementRetriesWithoutDoubleCrediting() throws Exception {
        try (var f = new Fixture(BigInteger.TEN.pow(64))) {
            f.untilAccepted();
            f.denyAcknowledgement = true;
            for (int i = 0; i < 6; i++) f.tick();
            assertTrue(f.stock.isEmpty());
            assertEquals(0, f.forgotten);
            assertEquals(3, f.jobs.size());
            f.denyAcknowledgement = false;
            f.finish(false);
            assertEquals(Map.of(OUT, f.amount), f.stock);
            assertEquals(3, f.accepts);
            assertEquals(3, f.forgotten);
        }
    }

    static final class Fixture implements AutoCloseable {
        final BigInteger amount;
        final Map<AEKey, BigInteger> stock = new LinkedHashMap<>();
        final Map<UUID, CraftingTableBatchRequest> jobs = new LinkedHashMap<>();
        final Set<UUID> acknowledged = new HashSet<>();
        final IGrid grid = mock(IGrid.class);
        final Level level = mock(Level.class);
        final IActionSource source = mock(IActionSource.class);
        final Ae2CompiledCraftingGraphCache.Snapshot graph = mock(Ae2CompiledCraftingGraphCache.Snapshot.class);
        final MockedStatic<ExactVectorStorageService> storage = mockStatic(ExactVectorStorageService.class);
        PhysicalCraftingTreeTransaction transaction;
        boolean loaded = true, ready = true, foreignId, foreignDigest, changedOutputs, staleRunning;
        boolean denyAcknowledgement, corruptOnAcknowledgement;
        int accepts, acknowledgements, mutations, ticks, forgotten;

        Fixture(BigInteger amount) {
            this.amount = amount;
            stock.put(RAW, amount);
            stock.put(OTHER, amount);
            var patterns = List.of(pattern("a", Map.of(RAW, 1L), Map.of(PART, 1L, EXTRA, 2L)),
                    pattern("b", Map.of(OTHER, 1L), Map.of(PART, 1L)),
                    pattern("root", Map.of(PART, 2L, EXTRA, 2L), Map.of(OUT, 1L)));
            var compiled = CompiledCraftingGraph.compile(1, patterns);
            when(graph.graph()).thenReturn(compiled);
            when(graph.recipeGeneration()).thenReturn(1L);
            var exact = new BigCraftingPlan<>(OUT, amount, Map.of("a", amount, "b", amount, "root", amount),
                    Map.copyOf(stock), Map.of(), Map.of(), 3);
            var plan = SelectedBranchPhysicalPlan.prepare(exact, patterns, 1, 1,
                    com.syaru.ae2craftingoptimizer.config.ACOConfig.getBigIntegerMaximumBits());
            var service = mock(CraftingService.class);
            when(grid.getCraftingService()).thenReturn(service);
            var block = mock(BlockEntity.class, withSettings().extraInterfaces(CraftingTableBatchTarget.class));
            var worker = (CraftingTableBatchTarget) block;
            when(block.getLevel()).thenReturn(level);
            when(block.getBlockPos()).thenReturn(BlockPos.ZERO);
            when(level.isLoaded(any(BlockPos.class))).thenAnswer(i -> loaded);
            when(level.getBlockEntity(BlockPos.ZERO)).thenReturn(block);
            var provider = mock(ICraftingProvider.class, withSettings().extraInterfaces(ProviderOwnedPatternBatchTarget.class));
            when(((ProviderOwnedPatternBatchTarget) provider).aco$getProviderOwnedBatchTarget()).thenReturn(block);
            for (var p : patterns) {
                var detail = mock(IMolecularAssemblerSupportedPattern.class);
                when(detail.getDefinition()).thenReturn(AEItemKey.of(switch (p.id()) {
                    case "a" -> Items.PAPER;
                    case "b" -> Items.BOOK;
                    default -> Items.STICK;
                }));
                var inputs = p.inputs().stream().map(slot -> {
                    var s = slot.alternatives().get(0);
                    var input = mock(IPatternDetails.IInput.class);
                    when(input.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(s.key(), s.amount())});
                    when(input.getMultiplier()).thenReturn(1L);
                    when(input.isValid(s.key(), level)).thenReturn(true);
                    return input;
                }).toArray(IPatternDetails.IInput[]::new);
                when(detail.getInputs()).thenReturn(inputs);
                when(detail.getOutputs()).thenReturn(p.outputs().entrySet().stream()
                        .map(e -> new GenericStack(e.getKey(), e.getValue())).toList());
                when(graph.pattern(p.id())).thenReturn(detail);
                when(service.getProviders(detail)).thenReturn(List.of(provider));
            }
            transaction = PhysicalCraftingTreeTransaction.create(plan,
                    PhysicalCraftingTreeTransaction.capturePatternAccounting(plan, graph, level));
            when(worker.aco$acceptCraftingTableBatch(any())).thenAnswer(i -> {
                CraftingTableBatchRequest request = i.getArgument(0);
                var prior = jobs.putIfAbsent(request.transactionId(), request);
                if (prior == null) accepts++;
                else assertEquals(prior.payloadDigest(), request.payloadDigest());
                return true;
            });
            when(worker.aco$ownsCraftingTableBatch(any(), anyString())).thenAnswer(i -> jobs.containsKey(i.getArgument(0)));
            when(worker.aco$craftingTableBatchSnapshot(any(), anyString())).thenAnswer(i -> {
                var request = jobs.get(i.getArgument(0));
                if (request == null) return Optional.empty();
                var state = acknowledged.contains(request.transactionId()) ? CraftingTableBatchSnapshot.State.ACKNOWLEDGED
                        : ready ? CraftingTableBatchSnapshot.State.OUTPUT_READY : CraftingTableBatchSnapshot.State.RUNNING;
                if (staleRunning) state = CraftingTableBatchSnapshot.State.RUNNING;
                var outputs = changedOutputs ? Map.of(OUT, BigInteger.ONE) : request.aggregateExpectedOutputs();
                return Optional.of(new CraftingTableBatchSnapshot(foreignId ? UUID.randomUUID() : request.transactionId(),
                        foreignDigest ? "wrong-job" : request.payloadDigest(), state, ready ? 1 : 0, 1,
                        ready && !staleRunning ? outputs : Map.of(), ""));
            });
            when(worker.aco$acknowledgeCraftingTableBatch(any(), anyString())).thenAnswer(i -> {
                acknowledgements++;
                if (denyAcknowledgement) return false;
                acknowledged.add(i.getArgument(0));
                if (corruptOnAcknowledgement) foreignDigest = true;
                return true;
            });
            when(worker.aco$forgetCraftingTableBatch(any(), anyString())).thenAnswer(i -> {
                if (!acknowledged.remove(i.getArgument(0))) return false;
                forgotten++;
                jobs.remove(i.getArgument(0));
                return true;
            });
            when(worker.aco$cancelCraftingTableBatch(any(), anyString())).thenAnswer(i -> {
                jobs.remove(i.getArgument(0));
                return true;
            });
            storage.when(() -> ExactVectorStorageService.exactStoredAmounts(eq(grid), anySet())).thenAnswer(i -> {
                Map<AEKey, BigInteger> result = new LinkedHashMap<>();
                for (AEKey k : i.<Set<AEKey>>getArgument(1)) result.put(k, stock.getOrDefault(k, BigInteger.ZERO));
                return Optional.of(result);
            });
            storage.when(() -> ExactVectorStorageService.canExtractAll(eq(grid), anyMap(), eq(source))).thenReturn(true);
            storage.when(() -> ExactVectorStorageService.canInsertAll(eq(grid), anyMap(), eq(source))).thenReturn(true);
            storage.when(() -> ExactVectorStorageService.extractAll(eq(grid), anyMap(), eq(source)))
                    .thenAnswer(i -> mutate(i.getArgument(1), false));
            storage.when(() -> ExactVectorStorageService.insertAll(eq(grid), anyMap(), eq(source)))
                    .thenAnswer(i -> mutate(i.getArgument(1), true));
        }

        ExactStorageMutationResult mutate(Map<AEKey, BigInteger> counts, boolean insert) {
            mutations++;
            counts.forEach((k, v) -> {
                BigInteger next = stock.getOrDefault(k, BigInteger.ZERO).add(insert ? v : v.negate());
                assertTrue(next.signum() >= 0);
                if (next.signum() == 0) stock.remove(k); else stock.put(k, next);
            });
            return ExactStorageMutationResult.success(counts.values().stream().reduce(BigInteger.ZERO, BigInteger::add));
        }

        void tick() {
            assertTrue(++ticks < 100, "bounded contract fixture stopped progressing: " + transaction.state());
            transaction.tick(grid, level, source, graph, 8);
            var saved = transaction.save();
            transaction = PhysicalCraftingTreeTransaction.load(saved);
            assertEquals(saved, transaction.save());
        }

        void untilAccepted() {
            ready = false;
            while (accepts < 2) tick();
            ready = true;
        }

        void finish(boolean cancel) {
            var terminal = cancel ? PhysicalCraftingTreeTransaction.State.CANCELLED : PhysicalCraftingTreeTransaction.State.COMPLETE;
            while (transaction.state() != terminal) {
                assertNotEquals(PhysicalCraftingTreeTransaction.State.QUARANTINED, transaction.state());
                tick();
            }
        }

        void untilCredited() {
            untilAccepted();
            while (acknowledgements < 2) tick();
            assertEquals(2, acknowledged.size());
        }

        @Override public void close() { storage.close(); PatternProviderRoutingCache.clear(); }
    }

    static CompiledPattern<AEKey> pattern(String id, Map<AEKey, Long> inputs, Map<AEKey, Long> outputs) {
        return new CompiledPattern<>(id, inputs.entrySet().stream().map(e -> new CompiledPattern.InputSlot<>(
                List.of(new CompiledPattern.Stack<>(e.getKey(), e.getValue())))).toList(), outputs, false);
    }
}
