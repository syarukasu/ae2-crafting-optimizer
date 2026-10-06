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
import appeng.crafting.pattern.AECraftingPattern;
import appeng.crafting.pattern.AEProcessingPattern;
import com.ae2vm.addon.compiler.PatternCompiler;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** #218: production wrappers, not the historical snapshot/catalog fixtures. */
@Timeout(15)
class NativeVmDeclaredInputTest {
    @BeforeAll static void bootstrap() throws Exception { NativeVmCaptureTest.bootstrap(); }

    @Test void nativeProcessingQueriesFinishWithoutFurtherTicks() throws Exception {
        assertContinuous(processing());
    }

    @Test void inheritedNativeProcessingInputQueriesFinishWithoutFurtherTicks() throws Exception {
        assertContinuous(processingSubclass());
    }

    @Test void nativeProcessingPrimaryDoesNotSearchForAnotherProducer() throws Exception {
        var fixture = new Fixture(processing(), Map.of());
        assertEquals(key(0), fixture.input().exactKey());
        verify(fixture.service, never()).getCraftingFor(key(0));
    }

    @Test void nativeProcessingDeclarationMatchesTheRealAe2Input() throws Exception {
        var pattern = processing();
        var source = pattern.getInputs()[0];
        assertEquals(1, source.getPossibleInputs().length);
        assertEquals(key(0), source.getPossibleInputs()[0].what());
        for (var candidate : List.of(key(0), key(1), key(2))) {
            assertEquals(key(0).equals(candidate), source.isValid(candidate, null));
            assertNull(source.getRemainingKey(candidate));
        }
        var fixture = new Fixture(pattern, Map.of());
        assertEquals(key(0), fixture.input().exactKey());
    }

    @Test void nativeFixedCraftingQueriesFinishWithoutFurtherTicks() throws Exception {
        assertContinuous(crafting(new AtomicReference<>(), false));
    }

    @Test void nativeFixedCraftingDoesNotSearchForAnotherProducer() throws Exception {
        var fixture = new Fixture(crafting(new AtomicReference<>(), false), Map.of());
        assertEquals(key(0), fixture.input().exactKey());
        verify(fixture.service, never()).getCraftingFor(key(0));
        verify(fixture.service, never()).getFuzzyCraftable(any(), any());
    }

    private static void assertContinuous(IPatternDetails pattern) throws Exception {
        var fixture = new Fixture(pattern, Map.of());
        var input = fixture.input();
        assertEquals(key(0), input.exactKey());
        var task = new FutureTask<>(() -> {
            try {
                for (int i = 0; i < 10_000; i++) {
                    assertTrue(input.isValid(key(0), null));
                    assertFalse(input.isValid(key(1), null), "NBT variants are not interchangeable");
                    assertNull(input.getRemainingKey(key(0)));
                }
                return fixture.access.metrics();
            } finally { fixture.access.finish(); }
        });
        var thread = new Thread(task, "declared-input-worker");
        thread.start();
        try {
            var metrics = task.get(2, TimeUnit.SECONDS);
            assertEquals(0, metrics.reads(), "declared semantics must not enter live access");
            assertEquals(0, metrics.intervals());
        } finally {
            task.cancel(true);
            thread.join(2_000);
            assertFalse(thread.isAlive());
        }
    }

    @Test void repeatedQueriesDoNotSpendLiveReadBudget() throws Exception {
        var fixture = new Fixture(processing(), Map.of());
        var input = fixture.input();
        input.exactKey();
        var task = new FutureTask<>(() -> {
            try {
                for (int i = 0; i < 4096; i++) {
                    input.isValid(key(0), null);
                    input.getRemainingKey(key(0));
                }
                return fixture.access.metrics().reads();
            } finally { fixture.access.finish(); }
        });
        var thread = new Thread(task);
        thread.start();
        try {
            while (!task.isDone()) {
                fixture.access.simulateFor(5_000);
                Thread.yield();
            }
            long reads = task.get();
            System.out.println("Issue218 declared input repeated live reads: " + reads);
            assertEquals(0, reads);
        } finally {
            task.cancel(true);
            thread.join(2_000);
            assertFalse(thread.isAlive());
        }
    }

    @Test void selectedPatternCapturesTemplatesWithoutEvaluatingPredicates() throws Exception {
        var fixture = new Fixture(processing(), Map.of());
        var task = new FutureTask<>(() -> {
            try {
                fixture.input();
                return fixture.access.readKindSummary();
            } finally {
                fixture.access.finish();
            }
        });
        var worker = new Thread(task, "pattern-metadata-worker");
        worker.start();
        try {
            while (!task.isDone()) fixture.access.simulateFor(5_000);
            var reads = task.get(2, TimeUnit.SECONDS);
            assertTrue(reads.contains("PATTERN:1"), reads);
            assertTrue(reads.contains("INPUT_METADATA:1"), reads);
            assertFalse(reads.contains("EMITTER:"), reads);
            assertFalse(reads.contains("INPUT_REMAINDER:"), reads);
        } finally {
            task.cancel(true);
            worker.join(2_000);
            assertFalse(worker.isAlive());
        }
    }

    @Test void fourInputTemplatesUseOneBoundedMetadataRead() throws Exception {
        var inputs = new IPatternDetails.IInput[4];
        for (int i = 0; i < inputs.length; i++) {
            var input = mock(IPatternDetails.IInput.class);
            when(input.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(key(i), 1)});
            when(input.getMultiplier()).thenReturn(1L);
            inputs[i] = input;
        }
        var pattern = mock(IPatternDetails.class);
        when(pattern.getInputs()).thenReturn(inputs);
        when(pattern.getOutputs()).thenReturn(java.util.Arrays.asList(new GenericStack[]{new GenericStack(output(), 1)}));
        var fixture = new Fixture(pattern, Map.of());
        var task = new FutureTask<>(() -> {
            try {
                fixture.input();
                return fixture.access.readKindSummary();
            } finally { fixture.access.finish(); }
        });
        var worker = new Thread(task, "four-input-metadata-worker");
        worker.start();
        try {
            while (!task.isDone()) fixture.access.simulateFor(5_000);
            var reads = task.get(2, TimeUnit.SECONDS);
            assertTrue(reads.contains("PATTERN:1"), reads);
            assertTrue(reads.contains("INPUT_METADATA:1"), reads);
            assertFalse(reads.contains("EMITTER:"), reads);
            assertFalse(reads.contains("INPUT_REMAINDER:"), reads);
            for (var input : inputs) verify(input, times(1)).getPossibleInputs();
        } finally {
            task.cancel(true);
            worker.join(2_000);
            assertFalse(worker.isAlive());
        }
    }

    @Test void inputValidityAndReturnsBothUseTheOrderSnapshot() throws Exception {
        var valid = new AtomicBoolean(true);
        var remainder = new AtomicReference<AEKey>();
        var source = mock(IPatternDetails.IInput.class);
        when(source.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(key(0), 1)});
        when(source.getMultiplier()).thenReturn(1L);
        when(source.isValid(any(), any())).thenAnswer(call -> valid.get());
        when(source.getRemainingKey(any())).thenAnswer(call -> remainder.get());
        var pattern = mock(IPatternDetails.class);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[]{source});
        when(pattern.getOutputs()).thenReturn(java.util.Arrays.asList(new GenericStack[]{new GenericStack(output(), 1)}));
        var fixture = new Fixture(pattern, Map.of());
        var input = fixture.input();
        assertNull(input.exactKey());
        assertTrue(input.isValid(key(0), null));
        assertNull(input.getRemainingKey(key(0)));
        valid.set(false);
        remainder.set(key(2));
        assertTrue(input.isValid(key(0), null));
        assertNull(input.getRemainingKey(key(0)));
        verify(source, times(1)).getRemainingKey(key(0));
        var nextOrder = new Fixture(pattern, Map.of()).input();
        assertFalse(nextOrder.isValid(key(0), null));
        assertEquals(key(2), nextOrder.getRemainingKey(key(0)));
        verify(source, times(2)).getRemainingKey(key(0));
    }

    @Test void repeatedCustomReturnsUseOneReadPerNbtKey() throws Exception {
        var first = key(0);
        var second = key(1);
        var returned = key(2);
        var source = mock(IPatternDetails.IInput.class);
        when(source.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(first, 1)});
        when(source.getMultiplier()).thenReturn(1L);
        when(source.getRemainingKey(first)).thenReturn(null);
        when(source.getRemainingKey(second)).thenReturn(returned);
        var pattern = mock(IPatternDetails.class);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[]{source});
        when(pattern.getOutputs()).thenReturn(java.util.Arrays.asList(new GenericStack[]{new GenericStack(output(), 1)}));
        var fixture = new Fixture(pattern, Map.of());
        var input = fixture.input();
        var task = new FutureTask<>(() -> {
            try {
                for (int i = 0; i < 10_000; i++) {
                    assertNull(input.getRemainingKey(first));
                    assertEquals(returned, input.getRemainingKey(second));
                }
                return fixture.access.readKindSummary();
            } finally {
                fixture.access.finish();
            }
        });
        var worker = new Thread(task, "cached-remainder-worker");
        worker.start();
        try {
            while (!task.isDone()) fixture.access.simulateFor(5_000);
            assertTrue(task.get(2, TimeUnit.SECONDS).contains("INPUT_REMAINDER:1"),
                    "Primary remainder was already captured; only the new NBT key needs a callback");
            verify(source, times(1)).getRemainingKey(first);
            verify(source, times(1)).getRemainingKey(second);
        } finally {
            task.cancel(true);
            worker.join(2_000);
            assertFalse(worker.isAlive());
        }
    }

    @Test void nativeCraftingReturnsAreFixedPerOrderAndRefreshedForTheNextOrder() throws Exception {
        var remainder = new AtomicReference<AEKey>(key(2));
        var pattern = crafting(remainder, false);
        var fixture = new Fixture(pattern, Map.of());
        var input = fixture.input();
        assertNull(input.exactKey());
        assertEquals(key(2), input.getRemainingKey(key(0)));
        remainder.set(key(3));
        assertEquals(key(2), input.getRemainingKey(key(0)));
        assertEquals(key(3), new Fixture(pattern, Map.of()).input().getRemainingKey(key(0)));
        assertTrue(fixture.timing.summary().contains("remainderCalls=1"));
        assertTrue(fixture.timing.summary().contains("remainderNull=0"));
    }

    @Test void substitutionEnabledCraftingDoesNotGainAnExactDeclaration() throws Exception {
        var fixture = new Fixture(crafting(new AtomicReference<>(), true), Map.of());
        assertNull(fixture.input().exactKey());
    }

    @Test void nativeSubstituteCapturesValidityAndReturnInOneReferenceWindow() throws Exception {
        var remainder = key(2);
        var pattern = crafting(new AtomicReference<>(remainder), true);
        var field = AECraftingPattern.class.getDeclaredField("recipe");
        field.setAccessible(true);
        var recipe = (CraftingRecipe) field.get(pattern);
        when(recipe.matches(any(), any())).thenReturn(true);
        when(recipe.assemble(any(), any())).thenReturn(new ItemStack(Items.DIAMOND));
        set((AECraftingPattern) pattern, "output", new ItemStack(Items.DIAMOND));
        var fixture = new Fixture(pattern, Map.of());
        var input = fixture.input();
        input.exactKey();
        var task = new FutureTask<>(() -> {
            try {
                assertTrue(input.isValid(key(1), null));
                assertEquals(remainder, input.getRemainingKey(key(1)));
                return fixture.access.readKindSummary();
            } finally { fixture.access.finish(); }
        });
        var worker = new Thread(task, "native-substitute-worker");
        worker.start();
        try {
            while (!task.isDone()) fixture.access.simulateFor(5_000);
            var reads = task.get(2, TimeUnit.SECONDS);
            assertTrue(reads.contains("INPUT_VALIDITY:1"));
            assertFalse(reads.contains("INPUT_REMAINDER:"), "Validity and remainder share the same captured observation");
        } finally {
            task.cancel(true);
            worker.join(2_000);
            assertFalse(worker.isAlive());
        }
        assertTrue(fixture.timing.summary().contains("remainderCalls=2"));
    }

    @Test void nativeUntaggedValidityMatchesAe2OneResultPerInputKey() throws Exception {
        var pattern = crafting(new AtomicReference<>(), true);
        set((AECraftingPattern) pattern, "isValidCache", new Map[9]);
        var fixture = new Fixture(pattern, Map.of());
        var input = fixture.input();
        var candidate = AEItemKey.of(Items.BOOK);
        var task = new FutureTask<>(() -> {
            try {
                for (int i = 0; i < 1_000; i++) assertFalse(input.isValid(candidate, null));
                return fixture.access.readKindSummary();
            } finally { fixture.access.finish(); }
        });
        var worker = new Thread(task, "native-untagged-worker");
        worker.start();
        try {
            while (!task.isDone()) fixture.access.simulateFor(5_000);
            assertTrue(task.get(2, TimeUnit.SECONDS).contains("INPUT_VALIDITY:1"));
        } finally {
            task.cancel(true);
            worker.join(2_000);
            assertFalse(worker.isAlive());
        }
    }

    @Test void nativeUntaggedCandidatesShareOneBoundedReferenceWindow() throws Exception {
        var pattern = crafting(new AtomicReference<>(), true);
        set((AECraftingPattern) pattern, "isValidCache", new Map[9]);
        var fixture = new Fixture(pattern, Map.of());
        var input = fixture.input();
        var candidates = List.<AEKey>of(AEItemKey.of(Items.BOOK), AEItemKey.of(Items.STICK),
                AEItemKey.of(Items.APPLE), AEItemKey.of(Items.FLINT));
        var task = new FutureTask<>(() -> {
            try {
                input.prepareCandidates(candidates);
                for (var candidate : candidates) {
                    assertTrue(input.isCandidatePrepared(candidate));
                    assertFalse(input.isValid(candidate, null));
                }
                return fixture.access.readKindSummary();
            } finally { fixture.access.finish(); }
        });
        var worker = new Thread(task, "native-candidate-batch-worker");
        worker.start();
        try {
            while (!task.isDone()) fixture.access.simulateFor(5_000);
            assertTrue(task.get(2, TimeUnit.SECONDS).contains("INPUT_VALIDITY:1"));
        } finally {
            task.cancel(true);
            worker.join(2_000);
            assertFalse(worker.isAlive());
        }
    }

    @Test void nativeSubstitutionPrimaryValidityDoesNotWaitForAnotherTick() throws Exception {
        var fixture = new Fixture(crafting(new AtomicReference<>(), true), Map.of());
        var input = fixture.input();
        input.exactKey();
        var task = new FutureTask<>(() -> {
            try {
                for (int i = 0; i < 4096; i++) assertTrue(input.isValid(key(0), null));
                return fixture.access.metrics().reads();
            } finally { fixture.access.finish(); }
        });
        var worker = new Thread(task);
        worker.start();
        try { assertEquals(0, task.get(2, TimeUnit.SECONDS)); }
        finally { task.cancel(true); worker.join(2000); }
    }

    @Test void localQueriesStillHonorCancellation() throws Exception {
        var fixture = new Fixture(processing(), Map.of());
        var input = fixture.input();
        Thread.currentThread().interrupt();
        try {
            assertThrows(java.util.concurrent.CancellationException.class, () -> input.isValid(key(0), null));
            assertThrows(java.util.concurrent.CancellationException.class, () -> input.getRemainingKey(key(0)));
        } finally { Thread.interrupted(); }
    }

    @Test void emptyPartialFullStockIsExactFromOneTo3402Bits() throws Exception {
        for (var pattern : List.of(processing(), crafting(new AtomicReference<>(), false))) {
            for (var requested : List.of(BigInteger.ONE, BigInteger.TWO, BigInteger.valueOf(Long.MAX_VALUE),
                    BigInteger.ONE.shiftLeft(63), BigInteger.TEN.pow(1024).subtract(BigInteger.ONE))) {
                for (var supplied : List.of(BigInteger.ZERO, requested.divide(BigInteger.TWO), requested)) {
                    var fixture = new Fixture(pattern, Map.of(key(0), supplied, key(1), requested));
                    var result = fixture.context.plan(output(), requested, false, ignored -> { });
                    assertEquals(supplied, result.used().getOrDefault(key(0), BigInteger.ZERO));
                    assertEquals(requested.subtract(supplied), result.missing().getOrDefault(key(0), BigInteger.ZERO));
                    assertFalse(result.used().containsKey(key(1)));
                    assertEquals(Map.of("vm-0", requested), result.crafts());
                    assertTrue(result.emitted().isEmpty());
                    assertEquals(requested.multiply(BigInteger.valueOf(3)).add(BigInteger.valueOf(16)),
                            result.integerCharges(), "bytes include both nodes and pattern executions");
                }
            }
        }
    }

    @Test void demandLookupDoesNotEnumerateUnrelatedPatterns() {
        var service = mock(ICraftingService.class);
        var grid = mock(IGrid.class);
        when(grid.getCraftingService()).thenReturn(service);
        var requester = mock(ICraftingSimulationRequester.class);
        var node = mock(IGridNode.class);
        when(requester.getGridNode()).thenReturn(node);
        when(node.getGrid()).thenReturn(grid);
        var root = processing(key(0), output());
        var precursor = processing(AEItemKey.of(Items.IRON_INGOT), key(0));
        when(service.getCraftingFor(output())).thenReturn(List.of(root));
        when(service.getCraftingFor(key(0))).thenReturn(List.of(precursor));
        var context = new NativeVmPlanningContext(grid, mock(Level.class), requester,
                new VmAccounting.Stock(Map.of(AEItemKey.of(Items.IRON_INGOT), BigInteger.ONE), true,
                        java.util.Set.of(AEItemKey.of(Items.IRON_INGOT))),
                new NativeVmAccess(), new NativeVmTiming());
        var result = context.plan(output(), BigInteger.TWO, false, ignored -> { });
        assertEquals(BigInteger.valueOf(2), result.crafts().get("vm-0"));
        assertEquals(BigInteger.ONE, result.used().get(AEItemKey.of(Items.IRON_INGOT)));
        assertEquals(BigInteger.ONE, result.missing().get(AEItemKey.of(Items.IRON_INGOT)));
        verify(service, times(1)).getCraftingFor(output());
        verify(service, times(1)).getCraftingFor(key(0));
        verify(service, never()).getCraftingFor(AEItemKey.of(Items.GOLD_INGOT));
        verify(service, never()).getCraftables(any());
    }

    @Test void unusedPatternDoesNotRequireItsStockOrMetadata() {
        var service = mock(ICraftingService.class);
        var grid = mock(IGrid.class);
        when(grid.getCraftingService()).thenReturn(service);
        var requester = mock(ICraftingSimulationRequester.class);
        var node = mock(IGridNode.class);
        when(requester.getGridNode()).thenReturn(node);
        when(node.getGrid()).thenReturn(grid);
        var unrelated = AEItemKey.of(Items.GOLD_INGOT);
        when(service.getCraftingFor(output())).thenReturn(List.of(processing()));
        var stock = new VmAccounting.Stock(Map.of(key(0), BigInteger.ONE), false, java.util.Set.of(key(0)));
        var context = new NativeVmPlanningContext(grid, mock(Level.class), requester, stock,
                new NativeVmAccess(), new NativeVmTiming());
        assertTrue(context.plan(output(), BigInteger.ONE, false, ignored -> { }).missing().isEmpty());
        verify(service, never()).getCraftingFor(unrelated);
        verify(service, never()).getCraftables(any());
    }

    @Test void fundedFirstProducerDoesNotEvaluateUnusedAlternativeInputs() {
        var service = mock(ICraftingService.class);
        var grid = mock(IGrid.class);
        when(grid.getCraftingService()).thenReturn(service);
        var requester = mock(ICraftingSimulationRequester.class);
        var node = mock(IGridNode.class);
        when(requester.getGridNode()).thenReturn(node);
        when(node.getGrid()).thenReturn(grid);
        var unused = mock(IPatternDetails.IInput.class);
        var unusedKey = AEItemKey.of(Items.GOLD_INGOT);
        when(unused.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(unusedKey, 1)});
        when(unused.getMultiplier()).thenReturn(1L);
        var alternative = mock(IPatternDetails.class);
        when(alternative.getInputs()).thenReturn(new IPatternDetails.IInput[]{unused});
        when(alternative.getOutputs()).thenReturn(java.util.Arrays.asList(new GenericStack[]{new GenericStack(output(), 1)}));
        when(service.getCraftingFor(output())).thenReturn(List.of(processing(), alternative));
        var context = new NativeVmPlanningContext(grid, mock(Level.class), requester,
                new VmAccounting.Stock(Map.of(key(0), BigInteger.ONE), true, java.util.Set.of(key(0))),
                new NativeVmAccess(), new NativeVmTiming());

        var result = context.plan(output(), BigInteger.ONE, false, ignored -> { });

        assertEquals(Map.of("vm-0", BigInteger.ONE), result.crafts());
        assertEquals(Map.of(key(0), BigInteger.ONE), result.used());
        assertTrue(result.missing().isEmpty());
        verify(unused, never()).getPossibleInputs();
        verify(unused, never()).isValid(any(), any());
        verify(unused, never()).getRemainingKey(any());
        verify(service, never()).getCraftingFor(unusedKey);
        verify(service, never()).canEmitFor(unusedKey);
    }

    @Test void demandEmittersRemainOnTheCalculationGrid() {
        var grid = mock(IGrid.class);
        var emitterService = mock(ICraftingService.class);
        when(grid.getCraftingService()).thenReturn(emitterService);
        var requesterGrid = mock(IGrid.class);
        var producerService = mock(ICraftingService.class);
        when(requesterGrid.getCraftingService()).thenReturn(producerService);
        var requester = mock(ICraftingSimulationRequester.class);
        var node = mock(IGridNode.class);
        when(requester.getGridNode()).thenReturn(node);
        when(node.getGrid()).thenReturn(requesterGrid);
        when(emitterService.canEmitFor(output())).thenReturn(true);
        var context = new NativeVmPlanningContext(grid, mock(Level.class), requester,
                new VmAccounting.Stock(Map.of(), true, java.util.Set.of()),
                new NativeVmAccess(), new NativeVmTiming());
        var result = context.plan(output(), BigInteger.ONE, false, ignored -> { });
        assertEquals(Map.of(output(), BigInteger.ONE), result.emitted());
        verify(emitterService, times(1)).canEmitFor(output());
        verify(producerService, never()).canEmitFor(output());
        verify(emitterService, never()).getCraftables(any());
        verify(producerService, never()).getCraftables(any());
    }

    @Test void repeatedDemandKeepsTheFirstOrderedProducerList() throws Exception {
        var fixture = new Fixture(processing(), Map.of());
        fixture.input();
        doReturn(List.of()).when(fixture.service).getCraftingFor(output());
        fixture.input();
        verify(fixture.service, times(1)).getCraftingFor(output());
    }

    @Test void absentProducerAndEmitterAnswersAreAlsoMemoized() {
        var service = mock(ICraftingService.class);
        var grid = mock(IGrid.class);
        when(grid.getCraftingService()).thenReturn(service);
        var requester = mock(ICraftingSimulationRequester.class);
        var node = mock(IGridNode.class);
        when(requester.getGridNode()).thenReturn(node);
        when(node.getGrid()).thenReturn(grid);
        when(service.getCraftingFor(output())).thenReturn(List.of());
        var context = new NativeVmPlanningContext(grid, mock(Level.class), requester,
                new VmAccounting.Stock(Map.of(), true, java.util.Set.of()),
                new NativeVmAccess(), new NativeVmTiming());
        var first = context.plan(output(), BigInteger.ONE, false, ignored -> { });
        doReturn(List.of(processing())).when(service).getCraftingFor(output());
        doReturn(true).when(service).canEmitFor(output());
        var second = context.plan(output(), BigInteger.ONE, false, ignored -> { });
        assertEquals(first.missing(), second.missing());
        assertEquals(Map.of(output(), BigInteger.ONE), first.missing());
        verify(service, times(1)).getCraftingFor(output());
        verify(service, times(1)).canEmitFor(output());
    }

    private static AEItemKey output() { return AEItemKey.of(Items.DIAMOND); }
    private static AEItemKey key(int variant) {
        var stack = new ItemStack(Items.PAPER);
        com.ae2vm.addon.PortStacks.putInt(stack, "variant", variant);
        return AEItemKey.of(stack);
    }

    private static IPatternDetails processing() {
        return processing(key(0), output());
    }

    private static IPatternDetails processingSubclass() {
        var definition = new ItemStack(Items.PAPER);
        var in = new ListTag();
        var out = new ListTag();
        in.add(GenericStack.writeTag(com.ae2vm.addon.PortStacks.REGISTRIES, new GenericStack(key(0), 1)));
        out.add(GenericStack.writeTag(com.ae2vm.addon.PortStacks.REGISTRIES, new GenericStack(output(), 1)));
        com.ae2vm.addon.PortStacks.put(definition, "in", in);
        com.ae2vm.addon.PortStacks.put(definition, "out", out);
        return new AEProcessingPattern(AEItemKey.of(definition)) { };
    }

    private static IPatternDetails processing(AEKey input, AEKey result) {
        var definition = new ItemStack(Items.PAPER);
        var in = new ListTag();
        var out = new ListTag();
        in.add(GenericStack.writeTag(com.ae2vm.addon.PortStacks.REGISTRIES, new GenericStack(input, 1)));
        out.add(GenericStack.writeTag(com.ae2vm.addon.PortStacks.REGISTRIES, new GenericStack(result, 1)));
        com.ae2vm.addon.PortStacks.put(definition, "in", in);
        com.ae2vm.addon.PortStacks.put(definition, "out", out);
        return new AEProcessingPattern(AEItemKey.of(definition));
    }

    private static IPatternDetails crafting(AtomicReference<AEKey> remainder, boolean substitute) throws Exception {
        var pattern = mock(AECraftingPattern.class, CALLS_REAL_METHODS);
        var recipe = mock(CraftingRecipe.class);
        when(recipe.getIngredients()).thenReturn(NonNullList.of(net.minecraft.world.item.crafting.Ingredient.EMPTY,
                net.minecraft.world.item.crafting.Ingredient.of(Items.PAPER)));
        when(recipe.getRemainingItems(any())).thenAnswer(call -> {
            var items = NonNullList.withSize(9, ItemStack.EMPTY);
            if (remainder.get() != null) items.set(0, ((AEItemKey) remainder.get()).toStack());
            return items;
        });
        set(pattern, "recipe", recipe);
        set(pattern, "canSubstitute", substitute);
        set(pattern, "isValidCache", new Map[9]);
        set(pattern, "definition", AEItemKey.of(Items.PAPER));
        var sparse = new GenericStack[9];
        sparse[0] = new GenericStack(key(0), 1);
        set(pattern, "sparseInputs", java.util.Arrays.asList(sparse));
        var stacks = new java.util.ArrayList<>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
        stacks.set(0, key(0).toStack());
        set(pattern, "positionedPattern", net.minecraft.world.item.crafting.CraftingInput.ofPositioned(3, 3, stacks));
        var type = Class.forName("appeng.crafting.pattern.AECraftingPattern$Input");
        var constructor = type.getDeclaredConstructor(AECraftingPattern.class, int.class, GenericStack.class);
        constructor.setAccessible(true);
        IPatternDetails.IInput slot;
        // Forge's capability transformer is absent in headless JUnit.
        try (var ignored = mockStatic(appeng.api.behaviors.ContainerItemStrategies.class)) {
            slot = (IPatternDetails.IInput) constructor.newInstance(pattern, 0, sparse[0]);
        }
        doReturn(new IPatternDetails.IInput[]{slot}).when(pattern).getInputs();
        doReturn(List.of(new GenericStack(output(), 1))).when(pattern).getOutputs();
        return pattern;
    }

    private static void set(AECraftingPattern pattern, String name, Object value) throws Exception {
        var field = AECraftingPattern.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(pattern, value);
    }

    private static final class Fixture {
        final NativeVmAccess access = new NativeVmAccess();
        final NativeVmTiming timing = new NativeVmTiming();
        final ICraftingService service = mock(ICraftingService.class);
        final NativeVmPlanningContext context;

        Fixture(IPatternDetails pattern, Map<AEKey, BigInteger> amounts) {
            var grid = mock(IGrid.class);
            when(grid.getCraftingService()).thenReturn(service);
            when(service.getCraftingFor(output())).thenReturn(List.of(pattern));
            var requester = mock(ICraftingSimulationRequester.class);
            var node = mock(IGridNode.class);
            when(requester.getGridNode()).thenReturn(node);
            when(node.getGrid()).thenReturn(grid);
            context = new NativeVmPlanningContext(grid, mock(Level.class), requester,
                    new VmAccounting.Stock(amounts, true, amounts.keySet()), access, timing);
        }

        @SuppressWarnings("unchecked") PatternCompiler.DetachedInput input() throws Exception {
            var method = NativeVmPlanningContext.class.getDeclaredMethod("candidates", AEKey.class);
            method.setAccessible(true);
            var patterns = (List<IPatternDetails>) method.invoke(context, output());
            return (PatternCompiler.DetachedInput) patterns.get(0).getInputs()[0];
        }
    }
}
