package com.ae2vm.addon.nativeengine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NativeVmReferenceBatchTest {
    @BeforeAll static void bootstrap() throws Exception { NativeVmCaptureTest.bootstrap(); }

    @Test void fixedRecipeBalanceOracleRejectsOverspendingAndUnfundedCrafts() {
        var f = new Fixture();
        var counts = Map.of(f.pattern, BigInteger.ONE);
        var stock = Map.of(f.raw, 1L);
        var valid = new VmQuantities(f.output, BigInteger.ONE, Map.of("root", BigInteger.ONE),
                Map.of(f.raw, BigInteger.ONE), Map.of(), Map.of(), BigInteger.valueOf(64), Map.of(),
                false, 0, 0, BigInteger.ZERO);
        assertDoesNotThrow(() -> NativeVmOracleHarness.assertFixedRecipeBalance(f.service, stock, valid, counts));
        assertThrows(AssertionError.class,
                () -> NativeVmOracleHarness.assertFixedRecipeBalance(f.service, Map.of(), valid, counts));
        var unfunded = new VmQuantities(f.output, BigInteger.ONE, Map.of("root", BigInteger.ONE),
                Map.of(), Map.of(), Map.of(), BigInteger.valueOf(64), Map.of(), false, 0, 0, BigInteger.ZERO);
        assertThrows(AssertionError.class,
                () -> NativeVmOracleHarness.assertFixedRecipeBalance(f.service, stock, unfunded, counts));
        var missing = new VmQuantities(f.output, BigInteger.ONE, Map.of("root", BigInteger.ONE),
                Map.of(), Map.of(), Map.of(f.raw, BigInteger.ONE), BigInteger.valueOf(64), Map.of(),
                false, 0, 0, BigInteger.ZERO);
        assertDoesNotThrow(() -> NativeVmOracleHarness.assertFixedRecipeBalance(f.service, Map.of(), missing, counts));
    }

    @Test void capturedProcessingAlternativesUseGoldEvenWhenAnotherIngredientIsMissing() throws Exception {
        var f = new Fixture();
        var circuit = AEItemKey.of(Items.PAPER);
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        var unavailable = AEItemKey.of(Items.IRON_INGOT);
        var absent = AEItemKey.of(Items.EMERALD);
        var root = processing(f.output, new GenericStack(absent, 1), new GenericStack(circuit, 12480));
        var first = processing(circuit, new GenericStack(unavailable, 1));
        var funded = processing(circuit, new GenericStack(gold, 1));
        when(f.service.getCraftingFor(f.output)).thenReturn(List.of(root));
        when(f.service.getCraftingFor(circuit)).thenReturn(List.of(first, funded));
        var stock = Map.<AEKey, BigInteger>of(circuit, BigInteger.valueOf(12000), gold, BigInteger.valueOf(480));
        var access = new NativeVmAccess();
        var context = new NativeVmPlanningContext(f.grid, f.level, f.requester,
                new VmAccounting.Stock(stock, true, stock.keySet()), access, new NativeVmTiming());
        context.prepare(f.output);
        access.finish();
        assertFalse(context.requiresLiveAccess());
        var worker = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var result = worker.submit(() -> context.plan(f.output, BigInteger.ONE, false, ignored -> { }))
                    .get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(Map.of(absent, BigInteger.ONE), result.missing());
            assertEquals(stock, result.used());
            var counts = new java.util.LinkedHashMap<IPatternDetails, BigInteger>();
            result.crafts().forEach((id, count) -> counts.put(context.bindings().get(id), count));
            assertEquals(Map.of(root, BigInteger.ONE, funded, BigInteger.valueOf(480)), counts);
        } finally { worker.shutdownNow(); }
    }

    @Test void capturedRequesterSearchUsesFreshStockAndNoPostCaptureWorldReads() {
        var f = new Fixture();
        var circuit = AEItemKey.of(Items.PAPER);
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        var root = processing(f.output, new GenericStack(circuit, 2));
        var child = processing(circuit, new GenericStack(gold, 3));
        when(f.service.getCraftingFor(f.output)).thenReturn(List.of(root));
        when(f.service.getCraftingFor(circuit)).thenReturn(List.of(child));
        var order = BigInteger.TEN.pow(1024).subtract(BigInteger.ONE);
        for (var funded : List.of(order, BigInteger.ZERO, order.divide(BigInteger.TWO), BigInteger.ONE)) {
            var available = funded.multiply(BigInteger.valueOf(6));
            var stock = Map.<AEKey, BigInteger>of(gold, available);
            var access = new NativeVmAccess();
            var context = new NativeVmPlanningContext(f.grid, f.level, f.requester,
                    new VmAccounting.Stock(stock, true, stock.keySet()), access, new NativeVmTiming());
            context.prepare(f.output);
            access.finish();
            assertFalse(context.requiresLiveAccess());
            var result = context.plan(f.output, order, true, ignored -> { });
            var planned = funded.signum() == 0 ? order : funded;
            assertEquals(planned, result.requested());
            assertEquals(available.signum() == 0 ? Map.of() : stock, result.used());
            assertEquals(funded.signum() == 0 ? Map.of(gold, order.multiply(BigInteger.valueOf(6))) : Map.of(), result.missing());
            var counts = new java.util.LinkedHashMap<IPatternDetails, BigInteger>();
            result.crafts().forEach((id, count) -> counts.put(context.bindings().get(id), count));
            assertEquals(Map.of(root, planned, child, planned.multiply(BigInteger.TWO)), counts);
        }
    }

    private static IPatternDetails processing(AEKey output, GenericStack... inputs) {
        var encoded = new ItemStack(Items.PAPER);
        var in = new net.minecraft.nbt.ListTag();
        for (var stack : inputs) in.add(GenericStack.writeTag(stack));
        var out = new net.minecraft.nbt.ListTag();
        out.add(GenericStack.writeTag(new GenericStack(output, 1)));
        encoded.getOrCreateTag().put("in", in);
        encoded.getOrCreateTag().put("out", out);
        return new appeng.crafting.pattern.AEProcessingPattern(AEItemKey.of(encoded));
    }

    @Test void encounteredReturnedKeyIsObservedAcrossInputsInOneBoundary() throws Exception {
        assertEquals(1, encounteredKeyScenario(0).reads(), "One actual returned key must not cross the boundary per input");
    }

    @Test void slowPeersYieldAtAe2sExistingReadWindow() throws Exception {
        var metrics = encounteredKeyScenario(5_000_000);
        assertEquals(31, metrics.intervals(), "Each slow callback ends the existing read window before the next peer");
    }

    private NativeVmAccess.Metrics encounteredKeyScenario(long callbackNanos) throws Exception {
        var f = new Fixture();
        var clock = new java.util.concurrent.atomic.AtomicLong();
        var stack = ((AEItemKey) f.raw).toStack();
        stack.getOrCreateTag().putInt("uses", 1);
        var returned = AEItemKey.of(stack);
        var rootInputs = new java.util.ArrayList<IPatternDetails.IInput>();
        var consumers = new java.util.ArrayList<IPatternDetails.IInput>();
        for (int i = 0; i < 32; i++) {
            var item = new ItemStack(Items.BOOK);
            item.getOrCreateTag().putInt("stage", i);
            var stage = AEItemKey.of(item);
            var demand = mock(IPatternDetails.IInput.class);
            when(demand.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(stage, 1)});
            when(demand.getMultiplier()).thenReturn(1L);
            when(demand.isValid(any(), any())).thenAnswer(call -> stage.equals(call.getArgument(0)));
            rootInputs.add(demand);
            var tool = mock(IPatternDetails.IInput.class);
            when(tool.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(f.raw, 1)});
            when(tool.getMultiplier()).thenReturn(1L);
            when(tool.isValid(any(), any())).thenAnswer(call -> {
                if (returned.equals(call.getArgument(0))) clock.addAndGet(callbackNanos);
                return f.raw.equals(call.getArgument(0)) || returned.equals(call.getArgument(0));
            });
            when(tool.getRemainingKey(any())).thenReturn(returned);
            consumers.add(tool);
            var pattern = mock(IPatternDetails.class);
            when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[]{tool});
            when(pattern.getOutputs()).thenReturn(new GenericStack[]{new GenericStack(stage, 1)});
            when(f.service.getCraftingFor(stage)).thenReturn(List.of(pattern));
        }
        when(f.pattern.getInputs()).thenReturn(rootInputs.toArray(IPatternDetails.IInput[]::new));
        var access = new NativeVmAccess(clock::get);
        var context = new NativeVmPlanningContext(f.grid, f.level, f.requester,
                new VmAccounting.Stock(Map.of(f.raw, BigInteger.ONE), true, Set.of()), access, new NativeVmTiming());
        context.prepare(f.output);
        for (var input : consumers) verify(input, never()).getRemainingKey(returned);
        var worker = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var result = worker.submit(() -> {
                try { return context.plan(f.output, BigInteger.ONE, false, ignored -> { }); }
                finally { access.finish(); }
            });
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            while (!result.isDone() && System.nanoTime() < deadline) {
                access.simulateFor(1000);
                Thread.yield();
            }
            var plan = result.get(1, java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(plan.missing().isEmpty());
            assertEquals(Map.of(f.raw, BigInteger.ONE), plan.used());
            assertEquals(33, plan.crafts().size());
            for (var input : consumers) verify(input, atMost(1)).isValid(returned, f.level);
            System.out.println("Encountered returned key: world reads=" + access.metrics().reads());
            return access.metrics();
        } finally { access.finish(); worker.shutdownNow(); }
    }

    @Test void repeatedTaggedCatalystUsesOneObservationPerOrder() {
        var f = new Fixture();
        when(f.input.getRemainingKey(f.raw)).thenReturn(f.raw);
        var amount = BigInteger.ONE.shiftLeft(80).add(BigInteger.ONE);
        var result = f.context(Map.of(f.raw, BigInteger.ONE)).plan(f.output, amount, false, ignored -> { });
        assertEquals(Map.of(f.raw, BigInteger.ONE), result.used());
        assertTrue(result.missing().isEmpty());
        assertEquals(amount, result.crafts().get("vm-0"));
        verify(f.input, times(1)).isValid(f.raw, f.level);
        verify(f.input, times(1)).getRemainingKey(f.raw);
    }

    @Test void observationsAreNotSharedWithTheNextOrder() {
        var f = new Fixture();
        var first = f.context(Map.of(f.raw, BigInteger.ONE)).plan(f.output, BigInteger.ONE, false, ignored -> { });
        assertTrue(first.missing().isEmpty());
        when(f.input.isValid(f.raw, f.level)).thenReturn(false);
        var second = f.context(Map.of(f.raw, BigInteger.ONE)).plan(f.output, BigInteger.ONE, false, ignored -> { });
        assertEquals(Map.of(f.raw, BigInteger.ONE), second.missing());
        assertTrue(second.used().isEmpty());
    }

    @Test void preparedNbtAlternativesCompleteOnVmWithoutAnyWorldRead() throws Exception {
        for (var amount : List.of(BigInteger.ONE, BigInteger.TWO, BigInteger.valueOf(Long.MAX_VALUE),
                BigInteger.TEN.pow(1024).subtract(BigInteger.ONE))) {
            var f = new Fixture();
            var stack = ((AEItemKey) f.raw).toStack();
            stack.getOrCreateTag().putInt("variant", 2);
            AEKey alternative = AEItemKey.of(stack);
            when(f.input.isValid(alternative, f.level)).thenReturn(true);
            for (var supplied : List.of(BigInteger.ZERO, amount.divide(BigInteger.TWO), amount)) {
                var access = new NativeVmAccess();
                var stock = new VmAccounting.Stock(Map.of(alternative, supplied), true, Set.of());
                var context = new NativeVmPlanningContext(f.grid, f.level, f.requester, stock, access, new NativeVmTiming());
                context.prepare(f.output);
                assertFalse(context.requiresLiveAccess());
                // Prohibit even a single exclusive callback after the T0 capture.
                access.finish();
                var pool = java.util.concurrent.Executors.newSingleThreadExecutor();
                try {
                    var result = pool.submit(() -> context.plan(f.output, amount, false, ignored -> { }))
                            .get(5, java.util.concurrent.TimeUnit.SECONDS);
                    assertEquals(supplied, result.used().getOrDefault(alternative, BigInteger.ZERO));
                    assertEquals(amount.subtract(supplied), result.missing().getOrDefault(f.raw, BigInteger.ZERO));
                    assertEquals(amount, result.crafts().get("vm-0"));
                } finally { pool.shutdownNow(); }
            }
        }
    }

    @Test void preparingOneCraftDoesNotEnumerateTheToolsFutureNbtStates() throws Exception {
        var f = new Fixture();
        when(f.input.isValid(any(), any())).thenReturn(true);
        when(f.input.getRemainingKey(any())).thenAnswer(call -> {
            var stack = ((AEItemKey) call.getArgument(0)).toStack();
            stack.getOrCreateTag().putInt("uses", stack.getOrCreateTag().getInt("uses") + 1);
            return AEItemKey.of(stack);
        });
        var context = f.context(Map.of(f.raw, BigInteger.ONE));
        context.prepare(f.output);
        var result = context.plan(f.output, BigInteger.ONE, false, ignored -> { });
        assertTrue(result.missing().isEmpty());
        assertEquals(Map.of(f.raw, BigInteger.ONE), result.used());
        verify(f.input, times(1)).getRemainingKey(any());
        verify(f.service, never()).getCraftables(any());
    }

    @Test void finiteReturnedToolIsReusedAndOnlyBrokenToolsAreManufactured() {
        long started = System.nanoTime(), maximumWork = 0;
        for (int uses : List.of(32, 65, 257)) for (int variant : List.of(1, 2, 3, 7, 31, 127, 1024, 65535)) {
        for (var order : List.of(BigInteger.ONE, BigInteger.TWO, BigInteger.valueOf(1000),
                BigInteger.ONE.shiftLeft(63), BigInteger.TEN.pow(1024).subtract(BigInteger.ONE))) {
            var required = order.add(BigInteger.valueOf(uses - 1)).divide(BigInteger.valueOf(uses));
            for (var existing : List.of(BigInteger.ZERO, required.divide(BigInteger.TWO), required)) {
                var f = new Fixture(variant);
                var metal = AEItemKey.of(Items.IRON_INGOT);
                var replacement = mock(IPatternDetails.class);
                var metalInput = mock(IPatternDetails.IInput.class);
                when(metalInput.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(metal, 3)});
                when(metalInput.getMultiplier()).thenReturn(1L);
                when(metalInput.isValid(any(), any())).thenAnswer(call -> metal.equals(call.getArgument(0)));
                when(replacement.getInputs()).thenReturn(new IPatternDetails.IInput[]{metalInput});
                when(replacement.getOutputs()).thenReturn(new GenericStack[]{new GenericStack(f.raw, 1)});
                when(f.service.getCraftingFor(f.raw)).thenReturn(List.of(replacement));
                when(f.input.isValid(any(), any())).thenAnswer(call -> call.getArgument(0) instanceof AEItemKey item
                        && item.getItem() == Items.PAPER);
                when(f.input.getRemainingKey(any())).thenAnswer(call -> {
                    var stack = ((AEItemKey) call.getArgument(0)).toStack();
                    int used = stack.getOrCreateTag().getInt("uses");
                    if (used + 1 == uses) return null;
                    stack.getOrCreateTag().putInt("uses", used + 1);
                    return AEItemKey.of(stack);
                });
                var manufactured = required.subtract(existing);
                var context = f.context(Map.of(f.raw, existing, metal, manufactured.multiply(BigInteger.valueOf(3))));
                context.prepare(f.output);
                verify(f.input, times(1)).getRemainingKey(any());
                var result = context.plan(f.output, order, false, work -> assertTrue(work < 65536,
                        "Finite-tool work must not grow with order quantity: bits=" + order.bitLength()
                                + " existing=" + existing + " variant=" + variant + " uses=" + uses + " work=" + work));
                maximumWork = Math.max(maximumWork, result.work());
                assertTrue(result.missing().isEmpty());
                assertEquals(existing, result.used().getOrDefault(f.raw, BigInteger.ZERO));
                assertEquals(manufactured.multiply(BigInteger.valueOf(3)), result.used().getOrDefault(metal, BigInteger.ZERO));
                BigInteger replacementCount = result.crafts().entrySet().stream()
                        .filter(entry -> context.bindings().get(entry.getKey()) == replacement)
                        .map(Map.Entry::getValue).reduce(BigInteger.ZERO, BigInteger::add);
                assertEquals(manufactured, replacementCount);
                verify(f.input, atMost(uses)).getRemainingKey(any());
                verify(f.input, atMost(uses)).isValid(any(), any());
            }
        }
        }
        System.out.printf("Issue218 finite tool matrix: maxWork=%d elapsedMs=%.3f%n",
                maximumWork, (System.nanoTime() - started) / 1_000_000.0);
    }

    @Test void producedNbtAlternativeIsCapturedBeforeArithmeticAndNetworkChanges() throws Exception {
        var f = new Fixture();
        var stack = ((AEItemKey) f.raw).toStack();
        stack.getOrCreateTag().putInt("variant", 2);
        var alternate = AEItemKey.of(stack);
        var intermediate = AEItemKey.of(Items.BOOK);
        var raw = AEItemKey.of(Items.GOLD_INGOT);
        var producer = mock(IPatternDetails.class);
        var first = mock(IPatternDetails.IInput.class);
        var rawInput = mock(IPatternDetails.IInput.class);
        when(first.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(intermediate, 1)});
        when(first.getMultiplier()).thenReturn(1L);
        when(first.isValid(any(), any())).thenAnswer(call -> intermediate.equals(call.getArgument(0)));
        when(rawInput.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(raw, 1)});
        when(rawInput.getMultiplier()).thenReturn(1L);
        when(rawInput.isValid(any(), any())).thenAnswer(call -> raw.equals(call.getArgument(0)));
        when(producer.getInputs()).thenReturn(new IPatternDetails.IInput[]{rawInput});
        when(producer.getOutputs()).thenReturn(new GenericStack[]{
                new GenericStack(intermediate, 1), new GenericStack(alternate, 1)});
        when(f.service.getCraftingFor(intermediate)).thenReturn(List.of(producer));
        when(f.pattern.getInputs()).thenReturn(new IPatternDetails.IInput[]{first, f.input});
        when(f.input.isValid(alternate, f.level)).thenReturn(true);
        var access = new NativeVmAccess();
        var context = new NativeVmPlanningContext(f.grid, f.level, f.requester,
                new VmAccounting.Stock(Map.of(raw, BigInteger.ONE), true, Set.of()), access, new NativeVmTiming());
        context.prepare(f.output);
        when(f.input.isValid(alternate, f.level)).thenReturn(false);
        when(f.service.getCraftingFor(intermediate)).thenReturn(List.of());
        access.finish();
        assertFalse(context.requiresLiveAccess());
        var pool = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var result = pool.submit(() -> context.plan(f.output, BigInteger.ONE, false, ignored -> { }))
                    .get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(Map.of(raw, BigInteger.ONE), result.used());
            assertTrue(result.missing().isEmpty());
            assertEquals(BigInteger.TWO, result.crafts().values().stream().reduce(BigInteger.ZERO, BigInteger::add));
        } finally { pool.shutdownNow(); }
    }

    @Test void invalidPrimaryMissingPlanDoesNotNeedAWorldRemainderCallback() throws Exception {
        var f = new Fixture();
        when(f.input.isValid(f.raw, f.level)).thenReturn(false);
        var access = new NativeVmAccess();
        var context = new NativeVmPlanningContext(f.grid, f.level, f.requester,
                new VmAccounting.Stock(Map.of(f.raw, BigInteger.ONE), true, Set.of()), access, new NativeVmTiming());
        context.prepare(f.output);
        access.finish();
        assertFalse(context.requiresLiveAccess());
        var pool = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var result = pool.submit(() -> context.plan(f.output, BigInteger.ONE, false, ignored -> { }))
                    .get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(Map.of(f.raw, BigInteger.ONE), result.missing());
            assertTrue(result.used().isEmpty());
        } finally { pool.shutdownNow(); }
    }

    @Test void nbtAlternativeReturnedAsADeclaredOutputNeedsOnlyOneSeed() throws Exception {
        var f = new Fixture();
        var stack = ((AEItemKey) f.raw).toStack();
        stack.getOrCreateTag().putInt("variant", 2);
        var alternate = AEItemKey.of(stack);
        when(f.pattern.getOutputs()).thenReturn(new GenericStack[]{
                new GenericStack(f.output, 1), new GenericStack(alternate, 1)});
        when(f.input.isValid(alternate, f.level)).thenReturn(true);
        var access = new NativeVmAccess();
        var context = new NativeVmPlanningContext(f.grid, f.level, f.requester,
                new VmAccounting.Stock(Map.of(alternate, BigInteger.ONE), true, Set.of()), access, new NativeVmTiming());
        context.prepare(f.output);
        access.finish();
        var worker = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var order = BigInteger.TEN.pow(1024).subtract(BigInteger.ONE);
            var result = worker.submit(() -> context.plan(f.output, order, false, ignored -> { }))
                    .get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(Map.of(alternate, BigInteger.ONE), result.used());
            assertTrue(result.missing().isEmpty());
            assertEquals(order, result.crafts().get("vm-0"));
        } finally { worker.shutdownNow(); }
    }

    @Test void unusedAlternativeExceptionIsNotPromotedToOrderFailure() {
        var f = new Fixture();
        var alternate = AEItemKey.of(Items.BOOK);
        when(f.input.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(f.raw, 1), new GenericStack(alternate, 1)});
        when(f.input.isValid(alternate, f.level)).thenThrow(new IllegalStateException("unused candidate"));
        var context = f.context(Map.of(f.raw, BigInteger.ONE, alternate, BigInteger.ONE));
        context.prepare(f.output);
        assertTrue(context.plan(f.output, BigInteger.ONE, false, ignored -> { }).missing().isEmpty());
        var demanded = f.context(Map.of(alternate, BigInteger.ONE));
        demanded.prepare(f.output);
        assertThrows(IllegalStateException.class, () -> demanded.plan(f.output, BigInteger.ONE, false, ignored -> { }));
    }

    private static final class Fixture {
        final AEKey raw, output = AEItemKey.of(Items.DIAMOND);
        final Level level = mock(Level.class);
        final IGrid grid = mock(IGrid.class);
        final ICraftingService service = mock(ICraftingService.class);
        final ICraftingSimulationRequester requester = mock(ICraftingSimulationRequester.class);
        final IPatternDetails pattern = mock(IPatternDetails.class);
        final IPatternDetails.IInput input = mock(IPatternDetails.IInput.class);
        Fixture() { this(1); }
        Fixture(int variant) {
            var stack = new ItemStack(Items.PAPER);
            stack.getOrCreateTag().putInt("variant", variant);
            raw = AEItemKey.of(stack);
            var node = mock(IGridNode.class);
            when(requester.getGridNode()).thenReturn(node);
            when(node.getGrid()).thenReturn(grid);
            when(grid.getCraftingService()).thenReturn(service);
            when(service.getCraftingFor(output)).thenReturn(List.of(pattern));
            when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[]{input});
            when(pattern.getOutputs()).thenReturn(new GenericStack[]{new GenericStack(output, 1)});
            when(input.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(raw, 1)});
            when(input.getMultiplier()).thenReturn(1L);
            when(input.isValid(raw, level)).thenReturn(true);
        }
        NativeVmPlanningContext context(Map<AEKey, BigInteger> stock) {
            return new NativeVmPlanningContext(grid, level, requester, new VmAccounting.Stock(stock, true, Set.of()),
                    new NativeVmAccess(), new NativeVmTiming());
        }
    }
}
