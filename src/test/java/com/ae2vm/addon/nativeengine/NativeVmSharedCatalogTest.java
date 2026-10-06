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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

/** #218: unchanged metadata is shared; quantities and decisions never are. */
class NativeVmSharedCatalogTest {
    @BeforeAll static void bootstrap() throws Exception { NativeVmCaptureTest.bootstrap(); }

    @Test @org.junit.jupiter.api.Timeout(30)
    void nativeSharedDagAggregatesWideMissingAndSuppliedPlansWithoutServerVisits() throws Exception {
        try (var server = new Server()) {
            int depth = 40;
            for (int i = 1; i <= depth; i++) {
                var inputs = new ArrayList<GenericStack>();
                inputs.add(stack(i - 1, 1));
                if (i > 1) inputs.add(stack(i - 2, 1));
                server.recipes.put(key(i), List.of(pattern(inputs, List.of(stack(i, 1)))));
            }
            var counts = new BigInteger[depth + 1];
            java.util.Arrays.fill(counts, BigInteger.ZERO);
            var order = BigInteger.TEN.pow(1024).subtract(BigInteger.ONE);
            counts[depth] = order;
            for (int i = depth; i > 0; i--) {
                counts[i - 1] = counts[i - 1].add(counts[i]);
                if (i > 1) counts[i - 2] = counts[i - 2].add(counts[i]);
            }
            for (var available : List.of(BigInteger.ZERO, counts[0].divide(BigInteger.TWO), counts[0])) {
                var capture = server.capture(Map.of(key(0), available));
                int before = server.handoffs.get();
                var result = capture.plan(key(depth), order, false);
                assertEquals(before, server.handoffs.get());
                capture.validate();
                assertEquals(available, result.used().getOrDefault(key(0), BigInteger.ZERO));
                assertEquals(counts[0].subtract(available), result.missing().getOrDefault(key(0), BigInteger.ZERO));
                var patterns = new LinkedHashMap<IPatternDetails, BigInteger>();
                result.crafts().forEach((id, value) -> patterns.put(capture.bindings().get(id), value));
                for (int i = 1; i <= depth; i++) assertEquals(counts[i], patterns.get(server.recipes.get(key(i)).get(0)));
                assertTrue(result.work() < 1024, "The production capture must use shared-demand aggregation");
                System.out.println("Issue218 native shared DAG: work=" + result.work() + " stock=" + available.signum());
            }
        }
    }

    @Test void nativeChainNeedsNoServerVisitsDuringVmArithmetic() throws Exception {
        try (var server = new Server()) {
            server.chain(96);
            var capture = server.capture(Map.of());
            int before = server.handoffs.get();
            var result = capture.plan(key(96), BigInteger.ONE, false);
            assertEquals(before, server.handoffs.get(), "Fixed native metadata must be available before VM execution");
            assertEquals(Map.of(key(0), BigInteger.ONE.shiftLeft(96)), result.missing());
            capture.validate();
            assertEquals(before + 1, server.handoffs.get(), "Unchanged native metadata needs one final version check");
            assertTrue(server.maxReads.get() <= 128, "Metadata construction must stay sliced");
            System.out.println("Issue218 shared catalog: stages=96 captureHandoffs=" + before
                    + " vmHandoffs=0 validationHandoffs=1");
        }
    }

    @Test void warmOrdersReuseMetadataButReadNewExactStock() throws Exception {
        try (var server = new Server()) {
            server.chain(12);
            var first = server.capture(Map.of());
            assertEquals(Map.of(key(0), BigInteger.ONE.shiftLeft(12)), first.plan(key(12), BigInteger.ONE, false).missing());
            first.validate();
            int reads = server.producerReads.get();
            var second = server.capture(Map.of(key(11), BigInteger.TWO));
            var result = second.plan(key(12), BigInteger.ONE, false);
            second.validate();
            assertEquals(reads, server.producerReads.get(), "A new order must not rebuild unchanged producer metadata");
            assertEquals(2, server.stockReads.get());
            assertEquals(Map.of(key(11), BigInteger.TWO), result.used());
            assertTrue(result.missing().isEmpty());
            assertEquals(1, result.crafts().size());
            assertEquals(Map.of(key(0), BigInteger.ONE.shiftLeft(12)), first.plan(key(12), BigInteger.ONE, false).missing());
        }
    }

    @Test void quantitiesStayExactAcrossStockAndOrderMatrix() throws Exception {
        try (var server = new Server()) {
            server.chain(12);
            Integer reads = null;
            for (var order : List.of(BigInteger.ONE, BigInteger.TWO, BigInteger.valueOf(Long.MAX_VALUE),
                    BigInteger.ONE.shiftLeft(63), new BigInteger("9220000000000000000"), BigInteger.TEN.pow(64))) {
                var required = order.shiftLeft(12);
                for (var available : List.of(BigInteger.ZERO, required.divide(BigInteger.TWO), required)) {
                    var capture = server.capture(Map.of(key(0), available));
                    var result = capture.plan(key(12), order, false);
                    capture.validate();
                    assertEquals(order, result.requested());
                    assertEquals(available, result.used().getOrDefault(key(0), BigInteger.ZERO));
                    assertEquals(required.subtract(available), result.missing().getOrDefault(key(0), BigInteger.ZERO));
                    var bindings = capture.bindings();
                    for (int stage = 1; stage <= 12; stage++) {
                        var pattern = server.recipes.get(key(stage)).get(0);
                        var id = bindings.entrySet().stream().filter(e -> e.getValue() == pattern).findFirst().orElseThrow().getKey();
                        assertEquals(order.shiftLeft(12 - stage), result.crafts().get(id));
                    }
                    if (reads == null) reads = server.producerReads.get();
                    else assertEquals(reads.intValue(), server.producerReads.get());
                }
            }
        }
    }

    @Test void producerAddRemoveAndPriorityChangesInvalidateMetadata() throws Exception {
        try (var server = new Server()) {
            var first = pattern(List.of(stack(0, 2)), List.of(stack(1, 1)));
            var alternate = pattern(List.of(stack(2, 3)), List.of(stack(1, 1)));
            server.recipes.put(key(1), List.of(first, alternate));
            var stock = Map.of(key(0), BigInteger.TEN, key(2), BigInteger.TEN);
            var old = server.capture(stock);
            assertEquals(Map.of(key(0), BigInteger.TWO), old.plan(key(1), BigInteger.ONE, false).used());
            old.validate();
            server.recipes.put(key(1), List.of(alternate, first));
            LegacyVmState.patternsChanged(server.grid);
            assertThrows(LegacyVmState.Changed.class, old::validate);
            var reordered = server.capture(stock);
            assertEquals(Map.of(key(2), BigInteger.valueOf(3)), reordered.plan(key(1), BigInteger.ONE, false).used());
            reordered.validate();
            server.recipes.remove(key(1));
            LegacyVmState.patternsChanged(server.grid);
            var removed = server.capture(stock);
            assertEquals(Map.of(key(1), BigInteger.ONE), removed.plan(key(1), BigInteger.ONE, false).missing());
            removed.validate();
            server.recipes.put(key(1), List.of(first));
            LegacyVmState.patternsChanged(server.grid);
            var added = server.capture(stock);
            assertTrue(added.plan(key(1), BigInteger.ONE, false).missing().isEmpty());
            added.validate();
        }
    }

    @Test void recipeReloadRebuildsMetadataAndDoesNotPublishPartialCapture() throws Exception {
        try (var server = new Server()) {
            server.chain(40);
            var capture = server.capture(Map.of());
            capture.plan(key(40), BigInteger.ONE, false);
            int reads = server.producerReads.get();
            LegacyVmState.recipesChanged();
            assertThrows(LegacyVmState.Changed.class, capture::validate);
            var next = server.capture(Map.of());
            next.plan(key(40), BigInteger.ONE, false);
            next.validate();
            assertTrue(server.producerReads.get() > reads);
        }
    }

    @Test void byproductsAndUnusedCustomPatternsDoNotBecomeLiveDependencies() throws Exception {
        try (var server = new Server()) {
            var unused = mock(IPatternDetails.class);
            when(unused.getInputs()).thenThrow(new AssertionError("Unrelated custom pattern must not be evaluated"));
            var first = pattern(List.of(stack(0, 1)), List.of(stack(1, 1), stack(2, 1)));
            var finish = pattern(List.of(stack(1, 1), stack(2, 1)), List.of(stack(3, 1)));
            server.recipes.put(key(1), List.of(first));
            server.recipes.put(key(2), List.of(unused));
            server.recipes.put(key(3), List.of(finish));
            var capture = server.capture(Map.of(key(0), BigInteger.ONE));
            var result = capture.plan(key(3), BigInteger.ONE, false);
            capture.validate();
            assertTrue(result.missing().isEmpty());
            assertEquals(Map.of(key(0), BigInteger.ONE), result.used());
            assertEquals(2, result.crafts().size());
            verifyNoInteractions(unused);
        }
    }

    @Test void incompleteStockAndNbtIdentityAreNotWeakenedBySharedMetadata() throws Exception {
        try (var server = new Server()) {
            server.chain(1);
            var capture = server.capture(Map.of(key(77), BigInteger.TEN));
            assertEquals(Map.of(key(0), BigInteger.TWO), capture.plan(key(1), BigInteger.ONE, false).missing());
            capture.validate();
            var incomplete = server.capture(new VmAccounting.Stock(Map.of(), false, Set.of(key(1))));
            assertThrows(IllegalStateException.class, () -> incomplete.plan(key(1), BigInteger.ONE, false));
        }
    }

    @Test void emitterSnapshotIsRefreshedOnlyWithPatternChanges() throws Exception {
        try (var server = new Server()) {
            server.chain(1);
            server.emitters.add(key(0));
            var capture = server.capture(Map.of());
            var result = capture.plan(key(1), BigInteger.ONE, false);
            capture.validate();
            assertEquals(Map.of(key(0), BigInteger.TWO), result.emitted());
            server.emitters.clear();
            LegacyVmState.patternsChanged(server.grid);
            var next = server.capture(Map.of());
            assertEquals(Map.of(key(0), BigInteger.TWO), next.plan(key(1), BigInteger.ONE, false).missing());
            next.validate();
        }
    }

    @Test void simultaneousOrdersShareOnlyMetadata() throws Exception {
        var workers = Executors.newFixedThreadPool(2);
        try (var server = new Server()) {
            server.chain(12);
            server.capture(Map.of());
            var required = BigInteger.TEN.pow(64).shiftLeft(12);
            var empty = workers.submit(() -> server.capture(Map.of()).plan(key(12), BigInteger.TEN.pow(64), false));
            var stocked = workers.submit(() -> server.capture(Map.of(key(0), required)).plan(key(12), BigInteger.TEN.pow(64), false));
            assertEquals(Map.of(key(0), required), empty.get(10, TimeUnit.SECONDS).missing());
            var result = stocked.get(10, TimeUnit.SECONDS);
            assertTrue(result.missing().isEmpty());
            assertEquals(Map.of(key(0), required), result.used());
            assertEquals(3, server.stockReads.get());
        } finally { workers.shutdownNow(); assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS)); }
    }

    @Test void dynamicInputsStillRunAndRevalidateOnTheServer() throws Exception {
        try (var server = new Server()) {
            var allowed = new java.util.concurrent.atomic.AtomicBoolean(true);
            var input = mock(IPatternDetails.IInput.class);
            when(input.getPossibleInputs()).thenReturn(new GenericStack[] { stack(0, 1) });
            when(input.getMultiplier()).thenReturn(1L);
            when(input.isValid(any(), any())).thenAnswer(call -> {
                server.checkThread(); return allowed.get() && key(0).equals(call.getArgument(0));
            });
            var custom = mock(IPatternDetails.class);
            when(custom.getDefinition()).thenReturn(AEItemKey.of(Items.PAPER));
            when(custom.getInputs()).thenReturn(new IPatternDetails.IInput[] { input });
            when(custom.getOutputs()).thenReturn(new GenericStack[] { stack(1, 1) });
            server.recipes.put(key(1), List.of(custom));
            var capture = server.capture(Map.of(key(0), BigInteger.ONE));
            var result = capture.plan(key(1), BigInteger.ONE, false);
            assertTrue(result.missing().isEmpty());
            capture.validate();
            allowed.set(false);
            assertThrows(LegacyVmState.Changed.class, capture::validate);
        }
    }

    @Test void fluidsRetainTheirExactUnitsWithWarmMetadata() throws Exception {
        try (var server = new Server()) {
            var fluid = appeng.api.stacks.AEFluidKey.of(net.minecraft.world.level.material.Fluids.WATER);
            server.recipes.put(key(1), List.of(pattern(List.of(new GenericStack(fluid, 1000)), List.of(stack(1, 2)))));
            var order = BigInteger.TEN.pow(64).add(BigInteger.ONE);
            var required = order.add(BigInteger.ONE).divide(BigInteger.TWO).multiply(BigInteger.valueOf(1000));
            for (var available : List.of(BigInteger.ZERO, required.divide(BigInteger.TWO), required)) {
                var capture = server.capture(Map.of(fluid, available));
                var result = capture.plan(key(1), order, false);
                capture.validate();
                assertEquals(available, result.used().getOrDefault(fluid, BigInteger.ZERO));
                assertEquals(required.subtract(available), result.missing().getOrDefault(fluid, BigInteger.ZERO));
            }
        }
    }

    @Test void cancellationCannotPublishAPartialCatalog() throws Exception {
        try (var server = new Server()) {
            server.chain(96);
            var worker = Thread.currentThread();
            server.onProducerRead = () -> { if (server.producerReads.get() == 10) worker.interrupt(); };
            try {
                assertThrows(java.util.concurrent.CancellationException.class, () -> server.capture(Map.of()));
                assertTrue(Thread.currentThread().isInterrupted());
            } finally { Thread.interrupted(); }
            server.executor.submit(() -> { }).get(10, TimeUnit.SECONDS);
            server.onProducerRead = () -> { };
            int reads = server.producerReads.get();
            var retry = server.capture(Map.of());
            assertEquals(Map.of(key(0), BigInteger.ONE.shiftLeft(96)), retry.plan(key(96), BigInteger.ONE, false).missing());
            retry.validate();
            assertEquals(96, server.producerReads.get() - reads, "Interrupted metadata must be rebuilt, not adopted");
        }
    }

    @Test void changingProvidersDuringBuildCannotPublishMixedVersions() throws Exception {
        try (var server = new Server()) {
            server.chain(40);
            server.onProducerRead = () -> {
                if (server.producerReads.get() == 10) LegacyVmState.patternsChanged(server.grid);
            };
            assertThrows(LegacyVmState.Changed.class, () -> server.capture(Map.of()));
            server.onProducerRead = () -> { };
            var next = server.capture(Map.of());
            assertEquals(Map.of(key(0), BigInteger.ONE.shiftLeft(40)), next.plan(key(40), BigInteger.ONE, false).missing());
            next.validate();
        }
    }

    @Test void serverStopReleasesRetainedMetadata() throws Exception {
        try (var server = new Server()) {
            server.chain(1);
            server.capture(Map.of());
            int reads = server.producerReads.get();
            NativeVmPatternCatalog.stop(server.server);
            var next = server.capture(Map.of());
            assertEquals(Map.of(key(0), BigInteger.TWO), next.plan(key(1), BigInteger.ONE, false).missing());
            next.validate();
            assertTrue(server.producerReads.get() > reads);
        }
    }

    @Test void oversizedCatalogRetainsTheDemandDrivenVmWithoutPartialMetadata() throws Exception {
        try (var server = new Server()) {
            server.chain(1);
            @SuppressWarnings("unchecked") var keys = (Set<AEKey>) mock(Set.class);
            when(keys.size()).thenReturn(65_537);
            doReturn(keys).when(server.service).getCraftables(any());
            var capture = server.capture(Map.of());
            var result = capture.plan(key(1), BigInteger.ONE, false);
            capture.validate();
            assertEquals(Map.of(key(0), BigInteger.TWO), result.missing());
            verify(keys, never()).iterator();
        }
    }

    @Test void retainedGridsAreBoundedAndIsolated() throws Exception {
        var servers = new ArrayList<Server>();
        try {
            for (int i = 0; i < 17; i++) {
                var server = new Server(); servers.add(server);
                server.recipes.put(key(1), List.of(pattern(List.of(stack(0, i + 1)), List.of(stack(1, 1)))));
                var capture = server.capture(Map.of());
                assertEquals(Map.of(key(0), BigInteger.valueOf(i + 1)), capture.plan(key(1), BigInteger.ONE, false).missing());
                capture.validate();
            }
            var first = servers.get(0);
            int before = first.producerReads.get();
            var capture = first.capture(Map.of());
            assertEquals(Map.of(key(0), BigInteger.ONE), capture.plan(key(1), BigInteger.ONE, false).missing());
            assertTrue(first.producerReads.get() > before, "The oldest retained grid should have been evicted");
        } finally { for (var server : servers) server.close(); }
    }

    private static AEKey key(int stage) {
        var stack = new ItemStack(Items.IRON_INGOT);
        stack.getOrCreateTag().putInt("stage", stage);
        return AEItemKey.of(stack);
    }
    private static GenericStack stack(int stage, long count) { return new GenericStack(key(stage), count); }
    private static IPatternDetails pattern(List<GenericStack> inputs, List<GenericStack> outputs) {
        var encoded = new ItemStack(Items.PAPER);
        var in = new ListTag(); inputs.forEach(value -> in.add(GenericStack.writeTag(value)));
        var out = new ListTag(); outputs.forEach(value -> out.add(GenericStack.writeTag(value)));
        encoded.getOrCreateTag().put("in", in); encoded.getOrCreateTag().put("out", out);
        return new AEProcessingPattern(AEItemKey.of(encoded));
    }

    private static final class Server implements AutoCloseable {
        final MinecraftServer server = mock(MinecraftServer.class);
        final ICraftingService service = mock(ICraftingService.class, withSettings().extraInterfaces(NativeVmHook.class));
        final IGrid grid = mock(IGrid.class);
        final Level level = mock(Level.class);
        final Map<AEKey, List<IPatternDetails>> recipes = new LinkedHashMap<>();
        final Set<AEKey> emitters = new LinkedHashSet<>();
        final AtomicInteger handoffs = new AtomicInteger(), producerReads = new AtomicInteger(), stockReads = new AtomicInteger();
        final AtomicInteger reads = new AtomicInteger(), maxReads = new AtomicInteger();
        final AtomicReference<Thread> owner = new AtomicReference<>();
        final AtomicLong clock = new AtomicLong();
        final java.util.concurrent.ExecutorService executor = Executors.newSingleThreadExecutor();
        volatile Runnable onProducerRead = () -> { };

        @SuppressWarnings("unchecked") Server() {
            when(grid.getCraftingService()).thenAnswer(call -> { checkThread(); return service; });
            when(level.getServer()).thenReturn(server);
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
            when(service.getCraftables(any())).thenAnswer(call -> {
                checkThread();
                var keys = new LinkedHashSet<>(recipes.keySet()); keys.addAll(emitters); return keys;
            });
            when(service.getCraftingFor(any())).thenAnswer(call -> {
                checkThread(); reads.incrementAndGet(); producerReads.incrementAndGet();
                onProducerRead.run();
                return recipes.getOrDefault(call.getArgument(0), List.of());
            });
            when(service.canEmitFor(any())).thenAnswer(call -> {
                checkThread(); reads.incrementAndGet(); return emitters.contains(call.getArgument(0));
            });
        }
        void checkThread() { assertSame(owner.get(), Thread.currentThread(), "No live API calls from calculator threads"); }
        void chain(int count) {
            for (int i = 0; i < count; i++) recipes.put(key(i + 1), List.of(pattern(List.of(stack(i, 2)), List.of(stack(i + 1, 1)))));
        }
        NativeVmCapture capture(Map<AEKey, BigInteger> stock) {
            return capture(new VmAccounting.Stock(stock, true, stock.keySet()));
        }
        NativeVmCapture capture(VmAccounting.Stock stock) {
            return new NativeVmCapture(grid, level, new VmAccounting() {
                public BigInteger maximumCount() { return BigInteger.ONE.shiftLeft(4096); }
                public Stock exactStock(IGrid ignored) { checkThread(); stockReads.incrementAndGet(); return stock; }
                public ICraftingPlan wideResult(IGrid ignored, Level world, NativeVmResult result) {
                    throw new AssertionError("Planning must not transfer ownership");
                }
            }, ignored -> { }, new NativeVmTiming(clock::get));
        }
        @Override public void close() throws Exception {
            NativeVmPatternCatalog.stop(server);
            executor.shutdownNow(); assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
