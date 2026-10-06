package com.ae2vm.addon.nativeengine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import java.math.BigInteger;
import java.util.Map;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class VmRequestStockTest {
    @BeforeAll static void bootstrap() throws Exception { NativeVmCaptureTest.bootstrap(); }

    @Test void simulationUsesTheRequestersAccessibleAmountWithoutMutatingInventory() {
        var grid = mock(IGrid.class);
        var storage = mock(IStorageService.class);
        var inventory = mock(MEStorage.class);
        var source = mock(IActionSource.class);
        var key = AEItemKey.of(Items.IRON_INGOT);
        when(grid.getStorageService()).thenReturn(storage);
        when(storage.getInventory()).thenReturn(inventory);
        when(inventory.extract(key, 10L, Actionable.SIMULATE, source)).thenReturn(3L);
        assertEquals(BigInteger.valueOf(3), accounting(key, BigInteger.TEN).requestStock(grid, source, true).amount(key));
        verify(inventory).extract(key, 10L, Actionable.SIMULATE, source);
        verifyNoMoreInteractions(inventory);
    }

    @Test void defaultSnapshotKeepsWideValuesAndDoesNotProbeTheLongApi() {
        var grid = mock(IGrid.class);
        var source = mock(IActionSource.class);
        var key = AEItemKey.of(Items.IRON_INGOT);
        var wide = BigInteger.TEN.pow(64);
        assertEquals(wide, accounting(key, wide).requestStock(grid, source, false).amount(key));
        verifyNoInteractions(grid);
    }

    @Test void missingExactSimulationProofIsNotConvertedToALongFacade() {
        var grid = mock(IGrid.class);
        var source = mock(IActionSource.class);
        var key = AEItemKey.of(Items.IRON_INGOT);
        assertThrows(IllegalStateException.class,
                () -> accounting(key, BigInteger.ONE.shiftLeft(63)).requestStock(grid, source, true));
        verifyNoInteractions(grid);
    }

    private static VmAccounting accounting(AEKey key, BigInteger amount) {
        return new VmAccounting() {
            public BigInteger maximumCount() { return BigInteger.ONE.shiftLeft(4096); }
            public Stock exactStock(IGrid grid) { return new Stock(Map.of(key, amount), true, java.util.Set.of(key)); }
            public ICraftingPlan wideResult(IGrid grid, Level world, NativeVmResult result) { throw new AssertionError(); }
        };
    }
}
