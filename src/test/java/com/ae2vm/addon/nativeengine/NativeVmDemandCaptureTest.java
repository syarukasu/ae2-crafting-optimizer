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
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NativeVmDemandCaptureTest {
    @BeforeAll static void bootstrap() throws Exception { NativeVmCaptureTest.bootstrap(); }

    @Test void oneOrTwoCraftsDoNotEnumerateFutureDamageStates() {
        for (int order : new int[]{1, 2}) {
            var calls = new AtomicInteger();
            var input = longLivedTool(calls);
            var output = AEItemKey.of(Items.DIAMOND);
            var pattern = pattern(output, input);
            var capture = capture(output, List.of(pattern), Map.of(tool(0), BigInteger.ONE));
            var result = capture.plan(output, BigInteger.valueOf(order), false);
            capture.validate();
            assertEquals(BigInteger.valueOf(order), result.crafts().get("vm-0"));
            assertEquals(Map.of(tool(0), BigInteger.ONE), result.used());
            assertTrue(result.missing().isEmpty());
            assertTrue(calls.get() <= 2 * order + 2, "Only consumed tool variants and their revalidation are needed");
        }
    }

    @Test void stockedFirstProducerDoesNotExpandDiscardedProducerReturns() {
        var raw = AEItemKey.of(Items.IRON_INGOT);
        var input = mock(IPatternDetails.IInput.class);
        when(input.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(raw, 1)});
        when(input.getMultiplier()).thenReturn(1L);
        when(input.isValid(any(), any())).thenAnswer(call -> raw.equals(call.getArgument(0)));
        var calls = new AtomicInteger();
        var output = AEItemKey.of(Items.DIAMOND);
        var capture = capture(output, List.of(pattern(output, input), pattern(output, longLivedTool(calls))),
                Map.of(raw, BigInteger.TEN));
        var result = capture.plan(output, BigInteger.TEN, false);
        capture.validate();
        assertEquals(Map.of("vm-0", BigInteger.TEN), result.crafts());
        assertEquals(Map.of(raw, BigInteger.TEN), result.used());
        assertTrue(result.missing().isEmpty());
        assertTrue(calls.get() <= 2, "At most the unused producer's primary rule is inspected");
    }

    @Test void industrialAlternativesDoNotCreateSlotTimesStockVariantCrossProduct() {
        var observations = new AtomicInteger();
        var input = new IPatternDetails.IInput() {
            public GenericStack[] getPossibleInputs() { return new GenericStack[]{new GenericStack(tool(0), 1)}; }
            public long getMultiplier() { return 1; }
            public boolean isValid(AEKey key, Level level) { observations.incrementAndGet(); return true; }
            public AEKey getRemainingKey(AEKey key) { return null; }
        };
        var output = AEItemKey.of(Items.DIAMOND);
        var recipes = new java.util.ArrayList<IPatternDetails>();
        for (int i = 0; i < 1024; i++) recipes.add(pattern(output, input));
        var stock = new java.util.LinkedHashMap<AEKey, BigInteger>();
        for (int i = 0; i < 2048; i++) stock.put(tool(i), BigInteger.ONE);
        var capture = capture(output, recipes, stock);
        var result = capture.plan(output, BigInteger.ONE, false);
        capture.validate();
        assertTrue(result.missing().isEmpty());
        assertEquals(Map.of("vm-0", BigInteger.ONE), result.crafts());
        assertEquals(BigInteger.ONE, result.used().values().stream().reduce(BigInteger.ZERO, BigInteger::add));
        assertTrue(observations.get() <= 2 * (1024 + 128), "Expected linear primary capture plus one candidate batch, not 2M pairs");
        System.out.println("Issue215 industrial capture: producers=1024 variants=2048 callbacksIncludingValidation=" + observations.get());
    }

    @Test void newlyUsedReturnVariantIsRevalidatedBeforeAdoption() {
        var changed = new java.util.concurrent.atomic.AtomicBoolean();
        var input = new IPatternDetails.IInput() {
            public GenericStack[] getPossibleInputs() { return new GenericStack[]{new GenericStack(tool(0), 1)}; }
            public long getMultiplier() { return 1; }
            public boolean isValid(AEKey key, Level level) { return !changed.get() || !key.equals(tool(1)); }
            public AEKey getRemainingKey(AEKey key) { return tool(((AEItemKey) key).toStack().getDamageValue() + 1); }
        };
        var output = AEItemKey.of(Items.DIAMOND);
        var capture = capture(output, List.of(pattern(output, input)), Map.of(tool(0), BigInteger.ONE));
        var result = capture.plan(output, BigInteger.TWO, false);
        assertTrue(result.missing().isEmpty());
        changed.set(true);
        assertThrows(NativeVm.Changed.class, capture::validate);
    }

    @Test void wideReturnedToolPeriodsKeepExactStockAndMissingCountsThroughNativeCapture() {
        var input = new IPatternDetails.IInput() {
            public GenericStack[] getPossibleInputs() { return new GenericStack[]{new GenericStack(tool(0), 1)}; }
            public long getMultiplier() { return 1; }
            public boolean isValid(AEKey key, Level level) { return key instanceof AEItemKey item && item.getItem() == Items.NETHERITE_PICKAXE; }
            public AEKey getRemainingKey(AEKey key) {
                int damage = ((AEItemKey) key).toStack().getDamageValue();
                return damage < 2 ? tool(damage + 1) : null;
            }
        };
        var output = AEItemKey.of(Items.DIAMOND);
        for (var order : List.of(BigInteger.ONE, BigInteger.TWO, BigInteger.valueOf(Long.MAX_VALUE),
                BigInteger.ONE.shiftLeft(63), BigInteger.TEN.pow(64))) {
            var requiredTools = order.add(BigInteger.TWO).divide(BigInteger.valueOf(3));
            for (var tools : List.of(BigInteger.ZERO, BigInteger.ONE, requiredTools)) {
                var capture = capture(output, List.of(pattern(output, input)), Map.of(tool(0), tools));
                var result = capture.plan(output, order, false);
                capture.validate();
                assertEquals(Map.of("vm-0", order), result.crafts());
                assertEquals(tools, result.used().getOrDefault(tool(0), BigInteger.ZERO));
                assertEquals(requiredTools.subtract(tools), result.missing().getOrDefault(tool(0), BigInteger.ZERO));
            }
        }
    }

    @Test void livePredicatesRunOnlyOnServerInBoundedBatches() throws Exception {
        try (var server = new ServerHarness()) {
            var input = serverInput(server, false);
            var output = AEItemKey.of(Items.DIAMOND);
            var stock = new java.util.LinkedHashMap<AEKey, BigInteger>();
            for (int i = 0; i < 1024; i++) stock.put(tool(i), BigInteger.ONE);
            var capture = capture(output, List.of(pattern(output, input)), stock, server.server);
            var result = capture.plan(output, BigInteger.valueOf(512), false);
            capture.validate();
            assertEquals(BigInteger.valueOf(512), result.used().values().stream().reduce(BigInteger.ZERO, BigInteger::add));
            assertTrue(result.missing().isEmpty());
            assertTrue(server.calls.get() >= 1024, "Includes final predicate revalidation");
            assertTrue(server.maxBatch.get() <= 128, "Live callbacks must yield inside candidate families");
            assertTrue(server.handoffs.get() < 100, "No per-candidate server round trip");
        }
    }

    @Test void cancellationStopsInsideAnObservationBatch() throws Exception {
        try (var server = new ServerHarness()) {
            var input = serverInput(server, true);
            var output = AEItemKey.of(Items.DIAMOND);
            var stock = new java.util.LinkedHashMap<AEKey, BigInteger>();
            for (int i = 0; i < 1024; i++) stock.put(tool(i), BigInteger.ONE);
            var capture = capture(output, List.of(pattern(output, input)), stock, server.server);
            try {
                assertThrows(java.util.concurrent.CancellationException.class,
                        () -> capture.plan(output, BigInteger.valueOf(512), false));
                assertTrue(Thread.currentThread().isInterrupted());
                assertTrue(server.calls.get() <= 5, "Stop callbacks as soon as the worker is cancelled");
            } finally { Thread.interrupted(); server.releaseCallback.countDown(); }
            server.executor.submit(() -> { }).get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(5, server.calls.get(), "Cancellation stays latched after the worker clears its interrupt");
        }
    }

    private static IPatternDetails.IInput serverInput(ServerHarness server, boolean cancel) {
        var worker = Thread.currentThread();
        return new IPatternDetails.IInput() {
            public GenericStack[] getPossibleInputs() { return new GenericStack[]{new GenericStack(tool(0), 1)}; }
            public long getMultiplier() { return 1; }
            public boolean isValid(AEKey key, Level level) {
                assertSame(server.owner.get(), Thread.currentThread());
                int calls = server.calls.incrementAndGet();
                if (cancel && calls == 5) {
                    worker.interrupt();
                    try { assertTrue(server.releaseCallback.await(10, java.util.concurrent.TimeUnit.SECONDS)); }
                    catch (InterruptedException interrupted) { throw new AssertionError(interrupted); }
                }
                return true;
            }
            public AEKey getRemainingKey(AEKey key) {
                assertSame(server.owner.get(), Thread.currentThread());
                return null;
            }
        };
    }

    private static final class ServerHarness implements AutoCloseable {
        final MinecraftServer server = mock(MinecraftServer.class);
        final java.util.concurrent.atomic.AtomicReference<Thread> owner = new java.util.concurrent.atomic.AtomicReference<>();
        final AtomicInteger calls = new AtomicInteger(), maxBatch = new AtomicInteger(), handoffs = new AtomicInteger();
        final java.util.concurrent.CountDownLatch releaseCallback = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        @SuppressWarnings("unchecked")
        ServerHarness() {
            when(server.isSameThread()).thenAnswer(call -> Thread.currentThread() == owner.get());
            when(server.submit(any(java.util.function.Supplier.class))).thenAnswer(call -> {
                java.util.function.Supplier<?> action = call.getArgument(0);
                return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    owner.set(Thread.currentThread());
                    handoffs.incrementAndGet();
                    int before = calls.get();
                    try { return action.get(); }
                    finally { maxBatch.accumulateAndGet(calls.get() - before, Math::max); }
                }, executor);
            });
        }
        public void close() throws Exception {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS));
        }
    }

    private static IPatternDetails.IInput longLivedTool(AtomicInteger calls) {
        return new IPatternDetails.IInput() {
            public GenericStack[] getPossibleInputs() { return new GenericStack[]{new GenericStack(tool(0), 1)}; }
            public long getMultiplier() { return 1; }
            public boolean isValid(AEKey key, Level level) { return key instanceof AEItemKey item && item.getItem() == Items.NETHERITE_PICKAXE; }
            public AEKey getRemainingKey(AEKey key) {
                // Fail the old eager closure cheaply instead of allocating one million states.
                if (calls.incrementAndGet() > 128) throw new AssertionError("Speculative return closure expanded");
                return tool(((AEItemKey) key).toStack().getDamageValue() + 1);
            }
        };
    }

    private static AEItemKey tool(int damage) {
        var stack = new ItemStack(Items.NETHERITE_PICKAXE);
        stack.setDamageValue(damage);
        return AEItemKey.of(stack);
    }

    private static IPatternDetails pattern(AEKey output, IPatternDetails.IInput input) {
        var pattern = mock(IPatternDetails.class);
        when(pattern.getDefinition()).thenReturn(AEItemKey.of(Items.PAPER));
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[]{input});
        when(pattern.getOutputs()).thenReturn(new GenericStack[]{new GenericStack(output, 1)});
        return pattern;
    }

    private static NativeVmCapture capture(AEKey root, List<IPatternDetails> recipes, Map<AEKey, BigInteger> stock) {
        var server = mock(MinecraftServer.class);
        when(server.isSameThread()).thenReturn(true);
        return capture(root, recipes, stock, server);
    }

    private static NativeVmCapture capture(AEKey root, List<IPatternDetails> recipes, Map<AEKey, BigInteger> stock, MinecraftServer server) {
        var grid = mock(IGrid.class);
        var service = mock(ICraftingService.class);
        when(grid.getCraftingService()).thenReturn(service);
        when(service.getCraftingFor(root)).thenReturn(recipes);
        var level = mock(Level.class);
        when(level.getServer()).thenReturn(server);
        return new NativeVmCapture(grid, level, new VmAccounting() {
            public BigInteger maximumCount() { return BigInteger.ONE.shiftLeft(4096); }
            public Stock exactStock(IGrid ignored) { return new Stock(stock, true, stock.keySet()); }
            public ICraftingPlan wideResult(IGrid ignored, Level world, NativeVmResult result) { throw new AssertionError(); }
        });
    }
}
