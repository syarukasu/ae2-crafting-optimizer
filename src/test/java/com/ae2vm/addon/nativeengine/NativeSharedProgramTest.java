package com.ae2vm.addon.nativeengine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NativeSharedProgramTest {
    @BeforeAll static void bootstrap() throws Exception { NativeVmCaptureTest.bootstrap(); }

    @Test void compileOnWorkerOnceAndShareConstantsButNeverInventoryOrEmitterState() throws Exception {
        var f = new Fixture();
        var pattern = recipe(f.goal, f.iron, 2);
        f.mount(pattern);
        var program = f.program(f.goal);
        assertFalse(program.compiled());
        var first = f.capture(BigInteger.TWO);
        when(f.service.canEmitFor(f.iron)).thenReturn(true);
        var second = f.capture(BigInteger.ZERO);
        assertFalse(program.compiled(), "T0 must not compile the graph on the server");
        verify(f.service, times(1)).getCraftingFor(f.goal);
        verify(f.service, times(1)).getCraftingFor(f.iron);
        clearInvocations(f.service, f.level, f.grid, f.requester);
        var worker = Executors.newSingleThreadExecutor();
        try {
            var a = worker.submit(() -> NativeGraphCalculation.plan(first, BigInteger.ONE, false)).get();
            var b = worker.submit(() -> NativeGraphCalculation.plan(second, BigInteger.ONE, false)).get();
            assertEquals(Map.of(f.iron, BigInteger.TWO), a.quantities().used());
            assertEquals(Map.of(f.iron, BigInteger.TWO), b.quantities().emitted());
            assertTrue(b.quantities().used().isEmpty());
            assertSame(first.graph(), second.graph());
        } finally { worker.shutdownNow(); }
        verifyNoInteractions(f.service, f.level, f.grid, f.requester);
    }

    @Test void sameTickReplacementInvalidatesOnlyDependentRootsAndRetainsCapturedOrder() {
        var f = new Fixture();
        var original = recipe(f.goal, f.iron, 2);
        var otherGoal = AEItemKey.of(Items.EMERALD);
        var independent = recipe(otherGoal, AEItemKey.of(Items.COAL), 1);
        var node = f.mount(original);
        f.mount(independent);
        var old = f.capture(BigInteger.TWO);
        var oldProgram = f.program(f.goal);
        var otherProgram = f.program(otherGoal);
        var replacement = recipe(f.goal, f.copper, 3);
        f.replace(node, replacement);
        assertNotSame(oldProgram, f.program(f.goal));
        assertSame(otherProgram, f.program(otherGoal));
        assertEquals(Map.of(f.iron, BigInteger.TWO), NativeGraphCalculation.plan(old, BigInteger.ONE, false).quantities().used());
        assertEquals(Map.of(f.copper, BigInteger.valueOf(3)),
                NativeGraphCalculation.plan(f.capture(BigInteger.TWO), BigInteger.ONE, false).quantities().missing());
        when(f.service.getCraftingFor(f.goal)).thenReturn(List.of());
        f.catalog.providerChanged(node, true);
        assertNull(f.program(f.goal));
        assertSame(otherProgram, f.program(otherGoal));
    }

    @Test void addingProducerForPreviouslyRawInputInvalidatesTheTransitiveProgram() {
        var f = new Fixture();
        f.mount(recipe(f.goal, f.iron, 2));
        var before = f.program(f.goal);
        f.mount(recipe(f.iron, f.copper, 3));
        var after = f.program(f.goal);
        assertNotSame(before, after);
        var result = NativeGraphCalculation.plan(f.capture(BigInteger.ZERO), BigInteger.ONE, false);
        assertEquals(Map.of(f.copper, BigInteger.valueOf(6)), result.quantities().missing());
        assertEquals(2, result.quantities().crafts().size());
    }

    @Test void providerPriorityChangeUsesNewOrderWithoutChangingPreviousSnapshot() {
        var f = new Fixture();
        var iron = recipe(f.goal, f.iron, 1);
        var copper = recipe(f.goal, f.copper, 1);
        var node = f.mount(iron, copper);
        var stock = new VmAccounting.Stock(Map.of(f.iron, BigInteger.ONE, f.copper, BigInteger.ONE), true, Set.of());
        var first = NativeOrderSnapshot.capture(f.grid, f.level, f.requester, f.goal, stock, new NativeVmTiming(), () -> false);
        f.replace(node, copper, iron);
        var second = NativeOrderSnapshot.capture(f.grid, f.level, f.requester, f.goal, stock, new NativeVmTiming(), () -> false);
        assertEquals(Map.of(f.iron, BigInteger.ONE), NativeGraphCalculation.plan(first, BigInteger.ONE, false).quantities().used());
        assertEquals(Map.of(f.copper, BigInteger.ONE), NativeGraphCalculation.plan(second, BigInteger.ONE, false).quantities().used());
    }

    @Test void refreshCapturesChangedNativeCoefficientEvenIfAnAddonRetainsPatternIdentity() {
        var f = new Fixture();
        var pattern = recipe(f.goal, f.iron, 1);
        var node = f.mount(pattern);
        var before = f.capture(BigInteger.ZERO);
        pattern.getInputs()[0].getPossibleInputs()[0] = new GenericStack(f.iron, 3);
        f.catalog.providerChanged(node, false);
        var after = f.capture(BigInteger.ZERO);
        assertEquals(Map.of(f.iron, BigInteger.ONE), NativeGraphCalculation.plan(before, BigInteger.ONE, false).quantities().missing());
        assertEquals(Map.of(f.iron, BigInteger.valueOf(3)), NativeGraphCalculation.plan(after, BigInteger.ONE, false).quantities().missing());
    }

    @Test void sameProgramHandlesEmptyPartialAndSufficientStockBeyondLongAndAqeCapacityBits() {
        var f = new Fixture();
        f.mount(recipe(f.goal, f.iron, 2));
        var program = f.program(f.goal);
        for (var quantity : List.of(BigInteger.ONE, BigInteger.TWO, BigInteger.valueOf(Long.MAX_VALUE),
                BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE), BigInteger.TEN.pow(1024))) {
            var required = quantity.multiply(BigInteger.TWO);
            for (var available : List.of(BigInteger.ZERO, required.subtract(BigInteger.ONE), required)) {
                var snapshot = f.capture(available);
                var result = NativeGraphCalculation.plan(snapshot, quantity, false).quantities();
                assertEquals(quantity, result.requested());
                assertEquals(required.subtract(available), result.missing().getOrDefault(f.iron, BigInteger.ZERO));
                assertEquals(available, result.used().getOrDefault(f.iron, BigInteger.ZERO));
                assertSame(program, f.program(f.goal));
                assertSame(program.graph(), snapshot.graph());
            }
        }
    }

    private static AEProcessingPattern recipe(AEKey output, AEKey input, long amount) {
        var encoded = new ItemStack(Items.PAPER);
        var inputs = new net.minecraft.nbt.ListTag(); inputs.add(GenericStack.writeTag(com.ae2vm.addon.PortStacks.REGISTRIES, new GenericStack(input, amount)));
        var outputs = new net.minecraft.nbt.ListTag(); outputs.add(GenericStack.writeTag(com.ae2vm.addon.PortStacks.REGISTRIES, new GenericStack(output, 1)));
        com.ae2vm.addon.PortStacks.put(encoded, "in", inputs); com.ae2vm.addon.PortStacks.put(encoded, "out", outputs);
        return new AEProcessingPattern(AEItemKey.of(encoded));
    }

    private static final class Fixture {
        final AEKey goal = AEItemKey.of(Items.DIAMOND), iron = AEItemKey.of(Items.IRON_INGOT), copper = AEItemKey.of(Items.COPPER_INGOT);
        final NativePatternCatalog catalog = new NativePatternCatalog();
        final IGrid grid = mock(IGrid.class);
        final Level level = mock(Level.class);
        final ICraftingService service = mock(ICraftingService.class, withSettings().extraInterfaces(NativeOrderSnapshot.CatalogSource.class));
        final ICraftingSimulationRequester requester = mock(ICraftingSimulationRequester.class);
        Fixture() {
            var node = mock(IGridNode.class);
            when(requester.getGridNode()).thenReturn(node); when(node.getGrid()).thenReturn(grid);
            when(grid.getCraftingService()).thenReturn(service);
            when(((NativeOrderSnapshot.CatalogSource) service).catalog()).thenReturn(catalog);
        }
        IGridNode mount(IPatternDetails... patterns) {
            var node = mock(IGridNode.class);
            when(node.getService(ICraftingProvider.class)).thenReturn(mock(ICraftingProvider.class));
            replace(node, patterns);
            return node;
        }
        void replace(IGridNode node, IPatternDetails... patterns) {
            when(node.getService(ICraftingProvider.class).getAvailablePatterns()).thenReturn(List.of(patterns));
            var byOutput = new java.util.LinkedHashMap<AEKey, java.util.ArrayList<IPatternDetails>>();
            for (var pattern : patterns) for (var output : pattern.getOutputs())
                byOutput.computeIfAbsent(output.what(), ignored -> new java.util.ArrayList<>()).add(pattern);
            byOutput.forEach((key, entries) -> when(service.getCraftingFor(key)).thenReturn(List.copyOf(entries)));
            catalog.providerChanged(node, false);
        }
        NativePatternCatalog.Program program(AEKey root) { return catalog.program(root, service, () -> false); }
        NativeOrderSnapshot capture(BigInteger amount) {
            return NativeOrderSnapshot.capture(grid, level, requester, goal,
                    new VmAccounting.Stock(Map.of(iron, amount), true, Set.of()), new NativeVmTiming(), () -> false);
        }
    }
}
