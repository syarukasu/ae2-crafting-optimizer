package com.ae2vm.addon.nativeengine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NativeVmLifecycleTest {
    @BeforeAll static void bootstrap() throws Exception { NativeVmCaptureTest.bootstrap(); }

    @Test void cancellingQueuedCreationNeverCapturesOrStartsTheJob() {
        var server = mock(MinecraftServer.class);
        var level = mock(Level.class);
        when(level.getServer()).thenReturn(server);
        var queued = new AtomicReference<Runnable>();
        doAnswer(call -> { queued.set(call.getArgument(0)); return null; }).when(server).execute(any(Runnable.class));
        var grid = mock(IGrid.class);
        var requester = mock(ICraftingSimulationRequester.class);
        var task = NativeVm.calculate(grid, level, requester, AEItemKey.of(Items.DIAMOND),
                BigInteger.ONE.shiftLeft(63), CalculationStrategy.REPORT_MISSING_ITEMS);
        assertNotNull(queued.get());
        assertTrue(task.cancel(true));
        queued.get().run();
        assertTrue(task.isCancelled());
        verifyNoInteractions(grid, requester);
    }

    @Test void creationReadsTheStockAndRequesterOnceThenUsesAe2Pool() {
        var server = mock(MinecraftServer.class);
        var level = mock(Level.class);
        when(level.getServer()).thenReturn(server);
        when(server.isSameThread()).thenReturn(true);
        var grid = mock(IGrid.class);
        var service = mock(ICraftingService.class, withSettings().extraInterfaces(NativeVmHook.class));
        when(grid.getCraftingService()).thenReturn(service);
        var storage = mock(IStorageService.class);
        when(grid.getStorageService()).thenReturn(storage);
        when(storage.getCachedInventory()).thenReturn(new KeyCounter());
        var requester = mock(ICraftingSimulationRequester.class);
        when(requester.getActionSource()).thenReturn(mock(IActionSource.class));
        var task = NativeVm.calculate(grid, level, requester, AEItemKey.of(Items.DIAMOND),
                BigInteger.TEN.pow(1024).subtract(BigInteger.ONE), CalculationStrategy.REPORT_MISSING_ITEMS);
        try {
            assertFalse(task.isDone());
            verify(requester, times(1)).getActionSource();
            verify(storage, times(1)).getCachedInventory();
            verify((NativeVmHook) service, times(1)).vm$execute(any());
        } finally { task.cancel(true); }
    }

    @Test void creationDoesNotEnumeratePatternsBeforeSubmittingToAe2Pool() {
        var server = mock(MinecraftServer.class);
        var level = mock(Level.class);
        when(level.getServer()).thenReturn(server);
        when(server.isSameThread()).thenReturn(true);
        var grid = mock(IGrid.class);
        var service = mock(ICraftingService.class, withSettings().extraInterfaces(NativeVmHook.class));
        when(grid.getCraftingService()).thenReturn(service);
        var requester = mock(ICraftingSimulationRequester.class);
        var node = mock(IGridNode.class);
        when(requester.getGridNode()).thenReturn(node);
        when(node.getGrid()).thenReturn(grid);
        var output = AEItemKey.of(Items.DIAMOND);
        var task = NativeVm.calculate(grid, level, requester, output, BigInteger.ONE,
                CalculationStrategy.REPORT_MISSING_ITEMS);
        try {
            assertFalse(task.isDone());
            verify(service, never()).getCraftables(any());
            verify(service, times(1)).getCraftingFor(output);
            verify(service, never()).getCraftingFor(AEItemKey.of(Items.GOLD_INGOT));
            verify((NativeVmHook) service).vm$execute(any());
        } finally { task.cancel(true); }
    }

    @Test void serverStopCancelsItsQueuedOrdersOnly() {
        var server = mock(MinecraftServer.class);
        var otherServer = mock(MinecraftServer.class);
        var level = mock(Level.class);
        var otherLevel = mock(Level.class);
        when(level.getServer()).thenReturn(server);
        when(otherLevel.getServer()).thenReturn(otherServer);
        var grid = mock(IGrid.class);
        var requester = mock(ICraftingSimulationRequester.class);
        var key = AEItemKey.of(Items.DIAMOND);
        var task = NativeVm.calculate(grid, level, requester, key, BigInteger.ONE,
                CalculationStrategy.REPORT_MISSING_ITEMS);
        var other = NativeVm.calculate(grid, otherLevel, requester, key, BigInteger.ONE,
                CalculationStrategy.REPORT_MISSING_ITEMS);
        try {
            NativeVm.cancelServer(server);
            assertTrue(task.isCancelled());
            assertFalse(other.isCancelled());
            verifyNoInteractions(grid, requester);
        } finally { other.cancel(true); }
    }

    @Test void liveInputIsEvaluatedOnceWithoutServerRoundTripsOrFinalRevalidation() {
        var server = mock(MinecraftServer.class);
        var level = mock(Level.class);
        when(level.getServer()).thenReturn(server);
        var grid = mock(IGrid.class);
        var service = mock(ICraftingService.class);
        when(grid.getCraftingService()).thenReturn(service);
        var requester = mock(ICraftingSimulationRequester.class);
        var node = mock(IGridNode.class);
        when(requester.getGridNode()).thenReturn(node);
        when(node.getGrid()).thenReturn(grid);
        var raw = AEItemKey.of(Items.IRON_INGOT);
        var output = AEItemKey.of(Items.DIAMOND);
        var input = mock(IPatternDetails.IInput.class);
        when(input.getPossibleInputs()).thenReturn(new GenericStack[] {new GenericStack(raw, 2)});
        when(input.getMultiplier()).thenReturn(1L);
        when(input.isValid(raw, level)).thenReturn(true);
        var pattern = mock(IPatternDetails.class);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[] {input});
        when(pattern.getOutputs()).thenReturn(new GenericStack[] {new GenericStack(output, 1)});
        when(pattern.getDefinition()).thenReturn(AEItemKey.of(Items.PAPER));
        when(service.getCraftingFor(output)).thenReturn(List.of(pattern));
        var timing = new NativeVmTiming();
        // The requested output is deliberately ignored by AE2, even when its stored quantity is unknown.
        var stock = new VmAccounting.Stock(Map.of(raw, BigInteger.TWO), false, java.util.Set.of(raw));
        var context = new NativeVmPlanningContext(grid, level, requester, stock, new NativeVmAccess(), timing);
        var plan = context.plan(output, BigInteger.ONE, false, ignored -> { });
        assertTrue(plan.missing().isEmpty());
        assertEquals(Map.of(raw, BigInteger.TWO), plan.used());
        verify(input, times(1)).isValid(raw, level);
        verify(input, times(1)).getMultiplier();
        verifyNoInteractions(server);
        assertTrue(timing.summary().contains("handoffs=0"));
        assertTrue(timing.summary().contains("validateMs=0"));
    }
}
