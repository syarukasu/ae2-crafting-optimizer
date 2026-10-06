package com.ae2vm.addon.nativeengine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** #218: ordered template batching, not per-template server round trips. */
class NativeVmTemplateBatchTest {
    private static AEKey OUTPUT;
    @BeforeAll static void bootstrap() throws Exception {
        NativeVmCaptureTest.bootstrap();
        OUTPUT = AEItemKey.of(Items.DIAMOND);
    }

    @Test void singleCandidateTemplatesUseBoundedCrossTemplateBatches() throws Exception {
        try (var fixture = new Fixture()) {
            var keys = itemKeys(384);
            var templates = keys.stream().map(key -> new GenericStack(key, 1)).toArray(GenericStack[]::new);
            var pattern = pattern(fixture.input(templates, key -> true));
            Map<AEKey, BigInteger> stock = new LinkedHashMap<>();
            keys.forEach(key -> stock.put(key, BigInteger.ONE));
            var capture = fixture.capture(pattern, stock);
            var result = capture.plan(OUTPUT, BigInteger.valueOf(keys.size()), false);
            capture.validate();
            assertEquals(stock, result.used());
            assertTrue(result.missing().isEmpty());
            assertEquals(Map.of("vm-0", BigInteger.valueOf(keys.size())), result.crafts());
            long calls = inputCalls(fixture.timing);
            System.out.println("Issue218 template batching: templates=" + pattern.getInputs()[0].getPossibleInputs().length
                    + " inputHandoffs=" + calls + " allHandoffs=" + fixture.handoffs);
            assertTrue(calls <= 4, "384 stocked alternatives must not cause one handoff each: " + calls);
        }
    }

    @Test void variableUnitsStayExactForEmptyPartialAndFullStockAtWideOrders() throws Exception {
        var keys = itemKeys(260);
        var templates = new GenericStack[keys.size() + 1];
        AEKey primary = AEItemKey.of(Items.NETHERITE_INGOT), accepted = keys.get(keys.size() - 1);
        templates[0] = new GenericStack(primary, 2);
        for (int i = 0; i < keys.size(); i++) templates[i + 1] = new GenericStack(keys.get(i), 3 + i % 5);
        var acceptedUnit = BigInteger.valueOf(templates[templates.length - 1].amount());
        for (var order : List.of(BigInteger.ONE, BigInteger.TWO, BigInteger.valueOf(Long.MAX_VALUE),
                BigInteger.ONE.shiftLeft(63), BigInteger.TEN.pow(1024).subtract(BigInteger.ONE))) {
            for (var supplied : List.of(BigInteger.ZERO, order.divide(BigInteger.TWO), order)) {
                try (var fixture = new Fixture()) {
                    var input = fixture.input(templates, key -> key.equals(primary) || key.equals(accepted));
                    Map<AEKey, BigInteger> stock = new LinkedHashMap<>();
                    for (int i = 0; i < keys.size() - 1; i++) stock.put(keys.get(i), BigInteger.TEN);
                    stock.put(accepted, supplied.multiply(acceptedUnit));
                    var capture = fixture.capture(pattern(input), stock);
                    var result = capture.plan(OUTPUT, order, false);
                    capture.validate();
                    assertEquals(count(accepted, supplied.multiply(acceptedUnit)), result.used());
                    assertEquals(count(primary, order.subtract(supplied).multiply(BigInteger.TWO)), result.missing());
                    assertEquals(Map.of("vm-0", order), result.crafts());
                    assertTrue(inputCalls(fixture.timing) <= 3, fixture.timing.summary());
                    assertTrue(fixture.maxCallbacks.get() <= 128);
                }
            }
        }
    }

    @Test void templatePriorityItemFluidUnitsAndNbtRemainDistinct() throws Exception {
        try (var fixture = new Fixture()) {
            AEKey iron = AEItemKey.of(Items.IRON_INGOT), gold = AEItemKey.of(Items.GOLD_INGOT),
                    water = AEFluidKey.of(Fluids.WATER);
            var stack = new ItemStack(Items.PAPER);
            stack.getOrCreateTag().putString("variant", "required");
            var paper = AEItemKey.of(stack);
            stack.getOrCreateTag().putString("variant", "wrong");
            var wrong = AEItemKey.of(stack);
            var templates = new GenericStack[]{new GenericStack(iron, 2), new GenericStack(gold, 3),
                    new GenericStack(water, 1000), new GenericStack(paper, 1)};
            var input = fixture.input(templates, key -> !key.equals(wrong));
            var capture = fixture.capture(pattern(input), Map.of(iron, BigInteger.valueOf(3),
                    gold, BigInteger.valueOf(5), water, BigInteger.valueOf(2000), wrong, BigInteger.TEN));
            var result = capture.plan(OUTPUT, BigInteger.valueOf(5), false);
            capture.validate();
            assertEquals(Map.of(iron, BigInteger.TWO, gold, BigInteger.valueOf(3), water, BigInteger.valueOf(2000)), result.used());
            assertEquals(Map.of(iron, BigInteger.TWO), result.missing());
        }
    }

    @Test void unusedLaterTemplateFailureDoesNotRejectAnEarlierMatch() throws Exception {
        try (var fixture = new Fixture()) {
            var primary = AEItemKey.of(Items.IRON_INGOT);
            var accepted = AEItemKey.of(Items.GOLD_INGOT);
            var unused = AEItemKey.of(Items.COPPER_INGOT);
            var input = fixture.input(new GenericStack[]{new GenericStack(primary, 1), new GenericStack(accepted, 1), new GenericStack(unused, 1)}, key -> {
                if (key.equals(unused)) throw new IllegalStateException("unused candidate failure");
                return true;
            });
            var capture = fixture.capture(pattern(input), Map.of(accepted, BigInteger.ONE, unused, BigInteger.ONE));
            var result = capture.plan(OUTPUT, BigInteger.ONE, false);
            assertEquals(Map.of(accepted, BigInteger.ONE), result.used());
            assertTrue(result.missing().isEmpty());
            assertEquals(3, fixture.callbacks.get(), "Exercise both uncached candidates and the captured primary");
            capture.validate();
        }
    }

    @Test void cachedPrimarySatisfiesOrderWithoutSpeculativeHandoff() throws Exception {
        try (var fixture = new Fixture()) {
            var keys = itemKeys(384);
            var input = fixture.input(keys.stream().map(k -> new GenericStack(k, 1)).toArray(GenericStack[]::new), key -> true);
            var stock = new LinkedHashMap<AEKey, BigInteger>();
            keys.forEach(key -> stock.put(key, BigInteger.ONE));
            stock.put(keys.get(0), BigInteger.ONE.shiftLeft(63));
            var capture = fixture.capture(pattern(input), stock);
            var result = capture.plan(OUTPUT, BigInteger.ONE.shiftLeft(63), false);
            assertEquals(Map.of(keys.get(0), BigInteger.ONE.shiftLeft(63)), result.used());
            assertEquals(1, fixture.callbacks.get(), "Captured primary already proves this order");
            assertEquals(0, inputCalls(fixture.timing), "Enough primary stock must not introduce another handoff");
            capture.validate();
        }
    }

    @Test void demandedLaterTemplateFailureIsNotHidden() throws Exception {
        try (var fixture = new Fixture()) {
            var primary = AEItemKey.of(Items.IRON_INGOT);
            var demanded = AEItemKey.of(Items.GOLD_INGOT);
            var failure = new IllegalStateException("required candidate failure");
            var input = fixture.input(new GenericStack[]{new GenericStack(primary, 1), new GenericStack(demanded, 1)}, key -> {
                if (key.equals(demanded)) throw failure;
                return true;
            });
            var capture = fixture.capture(pattern(input), Map.of(demanded, BigInteger.ONE));
            assertSame(failure, assertThrows(IllegalStateException.class, () -> capture.plan(OUTPUT, BigInteger.ONE, false)));
        }
    }

    @Test void onlyConsultedCandidateObservationsInvalidateThePlan() throws Exception {
        for (boolean changeUsed : new boolean[]{false, true}) {
            try (var fixture = new Fixture()) {
                var primary = AEItemKey.of(Items.IRON_INGOT);
                var accepted = AEItemKey.of(Items.GOLD_INGOT);
                var unused = AEItemKey.of(Items.COPPER_INGOT);
                var changed = new AtomicBoolean();
                var input = fixture.input(new GenericStack[]{new GenericStack(primary, 1), new GenericStack(accepted, 1), new GenericStack(unused, 1)},
                        key -> !changed.get() || !key.equals(changeUsed ? accepted : unused));
                var capture = fixture.capture(pattern(input), Map.of(accepted, BigInteger.ONE, unused, BigInteger.ONE));
                capture.plan(OUTPUT, BigInteger.ONE, false);
                assertEquals(3, fixture.callbacks.get(), "Unused observation must actually be captured");
                changed.set(true);
                if (changeUsed) assertThrows(LegacyVmState.Changed.class, capture::validate);
                else assertDoesNotThrow(capture::validate);
            }
        }
    }

    @Test void duplicateTemplatesRetainTheirOwnUnits() throws Exception {
        try (var fixture = new Fixture()) {
            var iron = AEItemKey.of(Items.IRON_INGOT);
            var input = fixture.input(new GenericStack[]{new GenericStack(iron, 3), new GenericStack(iron, 1)}, key -> true);
            var capture = fixture.capture(pattern(input), Map.of(iron, BigInteger.valueOf(5)));
            var result = capture.plan(OUTPUT, BigInteger.valueOf(3), false);
            capture.validate();
            assertEquals(Map.of(iron, BigInteger.valueOf(5)), result.used());
            assertTrue(result.missing().isEmpty());
        }
    }

    @Test void elapsedSliceBudgetYieldsBeforeTheCountLimit() throws Exception {
        try (var fixture = new Fixture()) {
            var keys = itemKeys(16);
            var input = fixture.input(keys.stream().map(k -> new GenericStack(k, 1)).toArray(GenericStack[]::new), key -> {
                fixture.clock.addAndGet(1_000_000L);
                return true;
            });
            var stock = new LinkedHashMap<AEKey, BigInteger>();
            keys.forEach(key -> stock.put(key, BigInteger.ONE));
            var capture = fixture.capture(pattern(input), stock);
            var result = capture.plan(OUTPUT, BigInteger.valueOf(16), false);
            assertEquals(stock, result.used());
            assertEquals(8, inputCalls(fixture.timing), "15 uncached checks, at most two per 2ms slice");
            assertTrue(fixture.maxCallbacks.get() <= 2, "One slow input set must yield the server");
            capture.validate();
        }
    }

    @Test void cancellationStopsInsideCrossTemplateBatch() throws Exception {
        try (var fixture = new Fixture()) {
            var keys = itemKeys(300);
            var worker = Thread.currentThread();
            var release = new CountDownLatch(1);
            var input = fixture.input(keys.stream().map(k -> new GenericStack(k, 1)).toArray(GenericStack[]::new), key -> {
                if (fixture.callbacks.get() == 5) {
                    worker.interrupt();
                    try { assertTrue(release.await(10, TimeUnit.SECONDS)); }
                    catch (InterruptedException failure) { throw new AssertionError(failure); }
                }
                return true;
            });
            var stock = new LinkedHashMap<AEKey, BigInteger>();
            keys.forEach(key -> stock.put(key, BigInteger.ONE));
            var capture = fixture.capture(pattern(input), stock);
            try {
                assertThrows(CancellationException.class, () -> capture.plan(OUTPUT, BigInteger.valueOf(300), false));
                assertTrue(worker.isInterrupted());
            } finally { Thread.interrupted(); release.countDown(); }
            fixture.executor.submit(() -> { }).get(10, TimeUnit.SECONDS);
            assertEquals(5, fixture.callbacks.get(), "No callbacks after cancellation");
        }
    }

    @Test void speculativeControlFailuresStayImmediate() throws Exception {
        for (var failure : List.of(new CancellationException("cancelled"), new LegacyVmState.Changed("changed"),
                new AssertionError("fatal"))) {
            try (var fixture = new Fixture()) {
                var primary = AEItemKey.of(Items.IRON_INGOT);
                var accepted = AEItemKey.of(Items.GOLD_INGOT);
                var unused = AEItemKey.of(Items.COPPER_INGOT);
                var input = fixture.input(new GenericStack[]{new GenericStack(primary, 1), new GenericStack(accepted, 1), new GenericStack(unused, 1)}, key -> {
                    if (key.equals(unused)) {
                        if (failure instanceof Error error) throw error;
                        throw (RuntimeException) failure;
                    }
                    return true;
                });
                var capture = fixture.capture(pattern(input), Map.of(accepted, BigInteger.ONE, unused, BigInteger.ONE));
                assertSame(failure, assertThrows(failure.getClass(), () -> capture.plan(OUTPUT, BigInteger.ONE, false)));
            }
        }
    }

    @Test void returnedContainerObservationsStayExactAndAreRevalidated() throws Exception {
        for (boolean failUnused : new boolean[]{false, true}) {
            try (var fixture = new Fixture()) {
                var primary = AEItemKey.of(Items.MILK_BUCKET);
                var accepted = AEItemKey.of(Items.WATER_BUCKET);
                var unused = AEItemKey.of(Items.GOLD_INGOT);
                var bucket = AEItemKey.of(Items.BUCKET);
                var changed = new AtomicBoolean();
                var input = fixture.input(new GenericStack[]{new GenericStack(primary, 1), new GenericStack(accepted, 1), new GenericStack(unused, 1)},
                        key -> true, key -> {
                            if (key.equals(unused) && failUnused) throw new IllegalStateException("unused return failure");
                            return (key.equals(primary) || key.equals(accepted)) && !changed.get() ? bucket : null;
                        });
                var capture = fixture.capture(pattern(input), Map.of(accepted, BigInteger.TWO, unused, BigInteger.ONE));
                var result = capture.plan(OUTPUT, BigInteger.TWO, false);
                assertEquals(Map.of(accepted, BigInteger.TWO), result.used());
                assertTrue(result.missing().isEmpty());
                assertEquals(3, fixture.callbacks.get(), "Include the unused return in lookahead");
                capture.validate();
                changed.set(true);
                assertThrows(LegacyVmState.Changed.class, capture::validate);
            }
        }
    }

    private static long inputCalls(NativeVmTiming timing) {
        var matcher = java.util.regex.Pattern.compile("serverCalls=\\{[^}]*INPUTS:(\\d+)").matcher(timing.summary());
        return matcher.find() ? Long.parseLong(matcher.group(1)) : 0;
    }

    private static Map<AEKey, BigInteger> count(AEKey key, BigInteger value) {
        return value.signum() == 0 ? Map.of() : Map.of(key, value);
    }

    private static List<AEItemKey> itemKeys(int size) {
        var keys = BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR && item != Items.DIAMOND
                && item != Items.NETHERITE_INGOT && !new ItemStack(item).hasCraftingRemainingItem())
                .limit(size).map(AEItemKey::of).toList();
        assertEquals(size, keys.size());
        return keys;
    }

    private static IPatternDetails pattern(IPatternDetails.IInput input) {
        var pattern = mock(IPatternDetails.class);
        when(pattern.getDefinition()).thenReturn(AEItemKey.of(Items.PAPER));
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[]{input});
        when(pattern.getOutputs()).thenReturn(new GenericStack[]{new GenericStack(OUTPUT, 1)});
        return pattern;
    }

    private static final class Fixture implements AutoCloseable {
        final MinecraftServer server = mock(MinecraftServer.class);
        final Level level = mock(Level.class);
        final IGrid grid = mock(IGrid.class);
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final AtomicReference<Thread> owner = new AtomicReference<>();
        final AtomicInteger callbacks = new AtomicInteger(), maxCallbacks = new AtomicInteger(), handoffs = new AtomicInteger();
        final AtomicLong clock = new AtomicLong();
        final NativeVmTiming timing = new NativeVmTiming(clock::get);
        @SuppressWarnings("unchecked") Fixture() {
            when(level.getServer()).thenReturn(server);
            when(server.isSameThread()).thenAnswer(call -> Thread.currentThread() == owner.get());
            when(server.submit(any(java.util.function.Supplier.class))).thenAnswer(call -> {
                java.util.function.Supplier<?> action = call.getArgument(0);
                return CompletableFuture.supplyAsync(() -> {
                    owner.set(Thread.currentThread()); handoffs.incrementAndGet();
                    int before = callbacks.get();
                    try { return action.get(); }
                    finally { maxCallbacks.accumulateAndGet(callbacks.get() - before, Math::max); }
                }, executor);
            });
        }
        IPatternDetails.IInput input(GenericStack[] templates, java.util.function.Predicate<AEKey> predicate) {
            return input(templates, predicate, key -> null);
        }
        IPatternDetails.IInput input(GenericStack[] templates, java.util.function.Predicate<AEKey> predicate,
                java.util.function.Function<AEKey, AEKey> remainder) {
            return new IPatternDetails.IInput() {
                public GenericStack[] getPossibleInputs() { return templates; }
                public long getMultiplier() { return 1; }
                public boolean isValid(AEKey key, Level world) {
                    assertSame(owner.get(), Thread.currentThread());
                    callbacks.incrementAndGet(); return predicate.test(key);
                }
                public AEKey getRemainingKey(AEKey key) {
                    assertSame(owner.get(), Thread.currentThread()); return remainder.apply(key);
                }
            };
        }
        NativeVmCapture capture(IPatternDetails pattern, Map<AEKey, BigInteger> stock) {
            var service = mock(ICraftingService.class);
            when(grid.getCraftingService()).thenReturn(service);
            when(service.getCraftingFor(OUTPUT)).thenReturn(List.of(pattern));
            return new NativeVmCapture(grid, level, new VmAccounting() {
                public BigInteger maximumCount() { return BigInteger.ONE.shiftLeft(4096); }
                public Stock exactStock(IGrid ignored) { assertSame(owner.get(), Thread.currentThread()); return new Stock(stock, true, stock.keySet()); }
                public ICraftingPlan wideResult(IGrid ignored, Level world, NativeVmResult result) { throw new AssertionError(); }
            }, ignored -> { }, timing);
        }
        public void close() throws Exception {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
