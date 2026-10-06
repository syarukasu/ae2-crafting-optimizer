package com.ae2vm.addon.nativeengine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NativeVmRequesterTest {
    @BeforeAll static void bootstrap() throws Exception { NativeVmCaptureTest.bootstrap(); }

    @Test void absentRequesterCannotUseNetworkPatternsOrInventory() throws Exception {
        var fixture = new Fixture(false, false);
        var plan = fixture.calculate();
        assertEquals(BigInteger.ONE, plan.missing().get(fixture.output));
        assertTrue(plan.crafts().isEmpty());
        assertTrue(plan.used().isEmpty());
        verify(fixture.storage, never()).getInventory();
    }

    @Test void absentActionSourceUsesCachedStockLikeAe2Nineteen() throws Exception {
        var fixture = new Fixture(true, false);
        var plan = fixture.calculate();
        assertTrue(plan.missing().isEmpty());
        assertEquals(BigInteger.ONE, plan.used().get(fixture.raw));
        verify(fixture.storage, never()).getInventory();
    }

    @Test void attachedRequesterStillCalculatesNormally() throws Exception {
        var fixture = new Fixture(true, true);
        var plan = fixture.calculate();
        assertTrue(plan.missing().isEmpty());
        assertEquals(BigInteger.ONE, plan.used().get(fixture.raw));
        assertEquals(BigInteger.ONE, plan.crafts().get("vm-0"));
    }

    @Test void alreadyExploredProducerIsNotDiscardedOrRecapturedWhenRequesterDetaches() throws Exception {
        var fixture = new Fixture(true, true);
        when(fixture.pattern.getInputs()).thenAnswer(call -> {
            when(fixture.requester.getGridNode()).thenReturn(null);
            return new IPatternDetails.IInput[]{fixture.input};
        });
        var plan = fixture.calculate();
        assertTrue(plan.missing().isEmpty());
        assertEquals(BigInteger.ONE, plan.crafts().get("vm-0"));
        verify(fixture.pattern, times(1)).getInputs();
    }

    private static final class Fixture {
        final IGrid grid = mock(IGrid.class);
        final ICraftingSimulationRequester requester = mock(ICraftingSimulationRequester.class);
        final IStorageService storage = mock(IStorageService.class);
        final IPatternDetails pattern = mock(IPatternDetails.class);
        final IPatternDetails.IInput input = mock(IPatternDetails.IInput.class);
        final AEItemKey output = AEItemKey.of(Items.DIAMOND), raw = AEItemKey.of(Items.IRON_INGOT);
        final Level level = mock(Level.class);

        Fixture(boolean attached, boolean authorized) {
            var service = mock(ICraftingService.class);
            when(grid.getCraftingService()).thenReturn(service);
            when(grid.getStorageService()).thenReturn(storage);
            var inventory = mock(MEStorage.class);
            when(storage.getInventory()).thenReturn(inventory);
            var available = new KeyCounter();
            available.add(raw, 10);
            when(inventory.getAvailableStacks()).thenReturn(available);
            when(storage.getCachedInventory()).thenReturn(available);
            when(input.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(raw, 1)});
            when(input.getMultiplier()).thenReturn(1L);
            when(input.isValid(any(), any())).thenAnswer(call -> raw.equals(call.getArgument(0)));
            when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[]{input});
            when(pattern.getOutputs()).thenReturn(java.util.Arrays.asList(new GenericStack[]{new GenericStack(output, 1)}));
            when(pattern.getDefinition()).thenReturn(AEItemKey.of(Items.PAPER));
            when(service.getCraftingFor(output)).thenReturn(List.of(pattern));
            if (attached) {
                var node = mock(IGridNode.class);
                when(node.getGrid()).thenReturn(grid);
                when(requester.getGridNode()).thenReturn(node);
            }
            if (authorized) when(requester.getActionSource()).thenReturn(mock(IActionSource.class));
            var server = mock(MinecraftServer.class);
            when(level.getServer()).thenReturn(server);
            when(server.isSameThread()).thenReturn(true);
            clearInvocations(storage);
        }

        VmQuantities calculate() {
            var stock = NativeVm.requestStock(grid, requester, null);
            var context = new NativeVmPlanningContext(grid, level, requester, stock, new NativeVmAccess(), new NativeVmTiming());
            return context.plan(output, BigInteger.ONE, false, ignored -> { });
        }
    }
}
