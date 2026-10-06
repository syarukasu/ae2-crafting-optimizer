package com.ae2vm.addon.nativeengine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** #218: operation counts, not a claim about production wall-clock speed. */
class NativeVmProcessingCatalogTest {
    @BeforeAll static void bootstrap() throws Exception { NativeVmCaptureTest.bootstrap(); }

    @Test void coldProcessingChainDoesNotWaitForTheServerAtEveryLevel() throws Exception {
        try (var server = new Server()) {
            var fixture = chain(96);
            var capture = server.capture(fixture.recipes(), Map.of());
            var result = capture.plan(key(96), BigInteger.ONE, false);
            capture.validate();
            assertEquals(Map.of(key(0), BigInteger.TWO.pow(96)), result.missing());
            assertEquals(96, result.crafts().size());
            assertTrue(server.handoffs.get() <= 25,
                    "Cold chain capture and validation should batch metadata, got " + server.handoffs.get());
            assertTrue(server.maxReads.get() <= 128, "A metadata slice must stay bounded");
            System.out.println("Issue218 processing chain: stages=96 handoffsIncludingValidation=" + server.handoffs.get());
        }
    }

    @Test void exactLeafCountsAndPatternCountsMatchAcrossStockAndOrderSizes() throws Exception {
        int stages = 24;
        var fixture = chain(stages);
        for (var order : List.of(BigInteger.ONE, BigInteger.TWO, BigInteger.valueOf(Long.MAX_VALUE),
                BigInteger.ONE.shiftLeft(63), new BigInteger("9220000000000000000"), BigInteger.TEN.pow(64))) {
            var required = order.shiftLeft(stages);
            for (var stock : List.of(BigInteger.ZERO, required.divide(BigInteger.TWO), required)) {
                try (var server = new Server()) {
                    var capture = server.capture(fixture.recipes(), Map.of(key(0), stock));
                    var result = capture.plan(key(stages), order, false);
                    capture.validate();
                    assertEquals(order, result.requested());
                    assertEquals(stock, result.used().getOrDefault(key(0), BigInteger.ZERO));
                    assertEquals(required.subtract(stock), result.missing().getOrDefault(key(0), BigInteger.ZERO));
                    assertTrue(result.emitted().isEmpty());
                    var counts = patternCounts(capture, result);
                    for (int stage = 0; stage < stages; stage++)
                        assertEquals(order.shiftLeft(stages - stage - 1), counts.get(fixture.patterns().get(stage)));
                }
            }
        }
    }

    @Test void stockedIntermediateStopsSpeculativeTraversal() throws Exception {
        try (var server = new Server()) {
            var fixture = chain(24);
            var capture = server.capture(fixture.recipes(), Map.of(key(23), BigInteger.TWO));
            var result = capture.plan(key(24), BigInteger.ONE, false);
            capture.validate();
            assertEquals(Map.of(key(23), BigInteger.TWO), result.used());
            assertEquals(1, result.crafts().size());
            assertTrue(result.missing().isEmpty());
            verify(server.service, never()).getCraftingFor(key(22));
        }
    }

    @Test void prefetchedByproductProducerIsNotPartOfThePlanOrAdoptionChecks() throws Exception {
        try (var server = new Server()) {
            var first = nativePattern(List.of(stack(0, 1)), List.of(stack(1, 1), stack(2, 1)));
            var unused = nativePattern(List.of(stack(3, 1)), List.of(stack(2, 1)));
            var finish = nativePattern(List.of(stack(1, 1), stack(2, 1)), List.of(stack(4, 1)));
            var recipes = new LinkedHashMap<AEKey, List<IPatternDetails>>();
            recipes.put(key(1), List.of(first)); recipes.put(key(2), List.of(unused)); recipes.put(key(4), List.of(finish));
            var capture = server.capture(recipes, Map.of(key(0), BigInteger.ONE));
            var result = capture.plan(key(4), BigInteger.ONE, false);
            assertEquals(Map.of(key(0), BigInteger.ONE), result.used());
            assertEquals(Map.of(first, BigInteger.ONE, finish, BigInteger.ONE), patternCounts(capture, result));
            assertTrue(result.missing().isEmpty());
            recipes.put(key(2), List.of(nativePattern(List.of(stack(3, 9)), List.of(stack(2, 1)))));
            assertDoesNotThrow(capture::validate, "Unused lookahead must not expand the adoption dependency set");
        }
    }

    @Test void customPatternBehindUnusedByproductIsNeverFrozen() throws Exception {
        try (var server = new Server()) {
            var unused = mock(IPatternDetails.class);
            when(unused.getInputs()).thenThrow(new AssertionError("custom speculative callback"));
            var first = nativePattern(List.of(stack(0, 1)), List.of(stack(1, 1), stack(2, 1)));
            var finish = nativePattern(List.of(stack(1, 1), stack(2, 1)), List.of(stack(4, 1)));
            var capture = server.capture(Map.of(key(1), List.of(first), key(2), List.of(unused),
                    key(4), List.of(finish)), Map.of(key(0), BigInteger.ONE));
            var result = capture.plan(key(4), BigInteger.ONE, false);
            capture.validate();
            assertTrue(result.missing().isEmpty());
            verifyNoInteractions(unused);
        }
    }

    @Test void demandedPrefetchedPatternIsStillRevalidated() throws Exception {
        try (var server = new Server()) {
            var fixture = chain(12);
            var capture = server.capture(fixture.recipes(), Map.of());
            capture.plan(key(12), BigInteger.ONE, false);
            fixture.recipes().put(key(6), List.of(nativePattern(List.of(stack(5, 3)), List.of(stack(6, 1)))));
            assertThrows(LegacyVmState.Changed.class, capture::validate);
        }
    }

    @Test void failedSpeculationCannotPublishHalfCapturedMultiOutputPattern() throws Exception {
        try (var server = new Server()) {
            var first = nativePattern(List.of(stack(0, 1)), List.of(stack(1, 1), stack(2, 1)));
            var shared = nativePattern(List.of(stack(3, 1)), List.of(stack(2, 1), stack(5, 1)));
            var finish = nativePattern(List.of(stack(1, 1), stack(2, 1), stack(5, 1)), List.of(stack(4, 1)));
            var attempts = new AtomicInteger();
            doAnswer(call -> {
                server.recordRead();
                if (attempts.getAndIncrement() == 0) throw new IllegalStateException("speculative lookup failure");
                return List.of();
            }).when(server.service).getCraftingFor(key(3));
            var capture = server.capture(Map.of(key(1), List.of(first), key(2), List.of(shared),
                    key(5), List.of(shared), key(4), List.of(finish)), Map.of(key(0), BigInteger.ONE, key(3), BigInteger.ONE));
            var result = capture.plan(key(4), BigInteger.ONE, false);
            capture.validate();
            assertTrue(result.missing().isEmpty());
            assertEquals(Map.of(key(0), BigInteger.ONE, key(3), BigInteger.ONE), result.used());
            assertEquals(Map.of(first, BigInteger.ONE, shared, BigInteger.ONE, finish, BigInteger.ONE),
                    patternCounts(capture, result));
        }
    }

    @Test void elapsedSliceBudgetStopsLookaheadEvenBelowPatternLimit() throws Exception {
        try (var server = new Server()) {
            server.readNanos = 1_000_000L;
            var fixture = chain(24);
            var capture = server.capture(fixture.recipes(), Map.of());
            var result = capture.plan(key(24), BigInteger.ONE, false);
            capture.validate();
            assertEquals(Map.of(key(0), BigInteger.ONE.shiftLeft(24)), result.missing());
            assertTrue(server.maxReads.get() <= 8,
                    "A timed-out slice must not keep traversing up to its count limit: " + server.maxReads.get());
        }
    }

    @Test void incompleteStockProofDoesNotExploreAnUnusedByproductBranch() throws Exception {
        try (var server = new Server()) {
            var first = nativePattern(List.of(stack(0, 1)), List.of(stack(1, 1), stack(2, 1)));
            var unused = nativePattern(List.of(stack(3, 1)), List.of(stack(2, 1)));
            var finish = nativePattern(List.of(stack(1, 1), stack(2, 1)), List.of(stack(4, 1)));
            var stock = new VmAccounting.Stock(Map.of(key(0), BigInteger.ONE), false,
                    java.util.Set.of(key(0), key(1), key(2), key(4)));
            var capture = server.capture(Map.of(key(1), List.of(first), key(2), List.of(unused),
                    key(4), List.of(finish)), stock);
            var result = capture.plan(key(4), BigInteger.ONE, false);
            capture.validate();
            assertTrue(result.missing().isEmpty());
            assertEquals(Map.of(key(0), BigInteger.ONE), result.used());
            verify(server.service, never()).getCraftingFor(key(3));
        }
    }

    @Test void concurrentOrdersUseIndependentStockAndCatalogs() throws Exception {
        var fixture = chain(24);
        var workers = Executors.newFixedThreadPool(2);
        try (var server = new Server()) {
            var order = new BigInteger("9220000000000000000");
            var required = order.shiftLeft(24);
            var empty = workers.submit(() -> {
                var capture = server.capture(fixture.recipes(), Map.of());
                var result = capture.plan(key(24), order, false);
                capture.validate(); return result;
            });
            var stocked = workers.submit(() -> {
                var capture = server.capture(fixture.recipes(), Map.of(key(0), required));
                var result = capture.plan(key(24), order, false);
                capture.validate(); return result;
            });
            assertEquals(Map.of(key(0), required), empty.get(10, TimeUnit.SECONDS).missing());
            var result = stocked.get(10, TimeUnit.SECONDS);
            assertTrue(result.missing().isEmpty());
            assertEquals(Map.of(key(0), required), result.used());
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test void cancellationStopsSpeculativeWorkWithoutContinuingCallbacks() throws Exception {
        try (var server = new Server()) {
            var fixture = chain(96);
            server.interruptAtRead = 12;
            server.worker = Thread.currentThread();
            try {
                assertThrows(java.util.concurrent.CancellationException.class,
                        () -> server.capture(fixture.recipes(), Map.of()).plan(key(96), BigInteger.ONE, false));
                assertTrue(Thread.currentThread().isInterrupted());
            } finally { Thread.interrupted(); }
            server.executor.submit(() -> { }).get(10, TimeUnit.SECONDS);
            assertTrue(server.reads.get() <= 15, "Cancellation must stop the current catalog slice: " + server.reads.get());
        }
    }

    private static Fixture chain(int count) {
        var recipes = new LinkedHashMap<AEKey, List<IPatternDetails>>();
        var patterns = new ArrayList<IPatternDetails>();
        for (int stage = 0; stage < count; stage++) {
            var pattern = nativePattern(List.of(stack(stage, 2)), List.of(stack(stage + 1, 1)));
            recipes.put(key(stage + 1), List.of(pattern)); patterns.add(pattern);
        }
        return new Fixture(recipes, patterns);
    }

    private record Fixture(Map<AEKey, List<IPatternDetails>> recipes, List<IPatternDetails> patterns) { }

    private static GenericStack stack(int id, long count) { return new GenericStack(key(id), count); }

    private static AEKey key(int id) {
        var stack = new ItemStack(Items.IRON_INGOT);
        com.ae2vm.addon.PortStacks.putInt(stack, "stage", id);
        return AEItemKey.of(stack);
    }

    private static IPatternDetails nativePattern(List<GenericStack> inputs, List<GenericStack> outputs) {
        var encoded = new ItemStack(Items.PAPER);
        var in = new ListTag(); inputs.forEach(value -> in.add(GenericStack.writeTag(com.ae2vm.addon.PortStacks.REGISTRIES, value)));
        var out = new ListTag(); outputs.forEach(value -> out.add(GenericStack.writeTag(com.ae2vm.addon.PortStacks.REGISTRIES, value)));
        com.ae2vm.addon.PortStacks.put(encoded, "in", in); com.ae2vm.addon.PortStacks.put(encoded, "out", out);
        return new AEProcessingPattern(AEItemKey.of(encoded));
    }

    private static Map<IPatternDetails, BigInteger> patternCounts(NativeVmCapture capture, VmQuantities result) {
        var counts = new LinkedHashMap<IPatternDetails, BigInteger>();
        var bindings = capture.bindings();
        result.crafts().forEach((id, count) -> counts.put(bindings.get(id), count));
        return counts;
    }

    private static final class Server implements AutoCloseable {
        final MinecraftServer server = mock(MinecraftServer.class);
        final ICraftingService service = mock(ICraftingService.class);
        final AtomicInteger handoffs = new AtomicInteger();
        final AtomicInteger reads = new AtomicInteger(), maxReads = new AtomicInteger();
        final AtomicLong clock = new AtomicLong();
        final AtomicReference<Thread> owner = new AtomicReference<>();
        final java.util.concurrent.ExecutorService executor = Executors.newSingleThreadExecutor();
        volatile Map<AEKey, List<IPatternDetails>> recipes = Map.of();
        volatile int interruptAtRead;
        volatile Thread worker;
        long readNanos;

        @SuppressWarnings("unchecked") Server() {
            when(server.isSameThread()).thenAnswer(call -> Thread.currentThread() == owner.get());
            when(server.submit(any(Supplier.class))).thenAnswer(call -> {
                Supplier<?> action = call.getArgument(0);
                return CompletableFuture.supplyAsync(() -> {
                    owner.set(Thread.currentThread()); handoffs.incrementAndGet();
                    int before = reads.get();
                    try { return action.get(); }
                    finally { maxReads.accumulateAndGet(reads.get() - before, Math::max); }
                }, executor);
            });
            when(service.canEmitFor(any())).thenAnswer(call -> {
                recordRead(); return false;
            });
            when(service.getCraftingFor(any())).thenAnswer(call -> {
                recordRead();
                return recipes.getOrDefault(call.getArgument(0), List.of());
            });
        }

        private void recordRead() {
            assertSame(owner.get(), Thread.currentThread());
            clock.addAndGet(readNanos);
            if (reads.incrementAndGet() == interruptAtRead) worker.interrupt();
        }

        NativeVmCapture capture(Map<AEKey, List<IPatternDetails>> recipes, Map<AEKey, BigInteger> stock) {
            return capture(recipes, new VmAccounting.Stock(stock, true, stock.keySet()));
        }

        NativeVmCapture capture(Map<AEKey, List<IPatternDetails>> recipes, VmAccounting.Stock stock) {
            this.recipes = recipes;
            var grid = mock(IGrid.class);
            when(grid.getCraftingService()).thenReturn(service);
            var level = mock(Level.class);
            when(level.getServer()).thenReturn(server);
            return new NativeVmCapture(grid, level, new VmAccounting() {
                public BigInteger maximumCount() { return BigInteger.ONE.shiftLeft(4096); }
                public Stock exactStock(IGrid ignored) {
                    assertSame(owner.get(), Thread.currentThread());
                    return stock;
                }
                public ICraftingPlan wideResult(IGrid ignored, Level world, NativeVmResult result) {
                    throw new AssertionError("No ownership transfer during planning");
                }
            }, ignored -> { }, new NativeVmTiming(clock::get));
        }

        @Override public void close() throws Exception {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
