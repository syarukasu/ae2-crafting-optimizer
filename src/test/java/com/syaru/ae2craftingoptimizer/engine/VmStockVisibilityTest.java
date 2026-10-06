package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import appeng.api.networking.IGrid;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.syaru.ae2craftingoptimizer.api.contract.ExactStorageAmountProvider;
import java.math.BigInteger;
import java.util.Map;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class VmStockVisibilityTest {
    @BeforeAll static void bootstrap() throws Exception { ReusableByproductAe2OracleTest.bootstrap(); }

    @Test void ordinaryStockUsesAe2CachedAmountAndExcludesUnlistedMountKeys() {
        var key = AEItemKey.of(Items.IRON_INGOT);
        var hidden = AEItemKey.of(Items.GOLD_INGOT);
        var inventory = mock(MEStorage.class);
        doAnswer(call -> {
            KeyCounter available = call.getArgument(0);
            available.add(key, 10);
            available.add(hidden, 5);
            return null;
        }).when(inventory).getAvailableStacks(any(KeyCounter.class));
        var cached = new KeyCounter();
        cached.add(key, 3);
        var stock = new VmBigIntegerAccounting().exactStock(grid(inventory, cached));
        assertEquals(BigInteger.valueOf(3), stock.amount(key));
        assertEquals(BigInteger.ZERO, stock.amount(hidden));
        verifyNoInteractions(inventory);
    }

    @Test void wideStockKeepsProvenExactAmountForAnAe2VisibleKey() {
        var key = AEItemKey.of(Items.IRON_INGOT);
        var wide = BigInteger.ONE.shiftLeft(64);
        var inventory = mock(MEStorage.class,
                Mockito.withSettings().extraInterfaces(ExactStorageAmountProvider.class));
        doAnswer(call -> {
            ((KeyCounter) call.getArgument(0)).set(key, Long.MAX_VALUE);
            return null;
        }).when(inventory).getAvailableStacks(any(KeyCounter.class));
        when(((ExactStorageAmountProvider) inventory).exactStoredAmounts()).thenReturn(Map.of(key, wide));
        var cached = new KeyCounter();
        cached.set(key, Long.MAX_VALUE);
        var stock = new VmBigIntegerAccounting().exactStock(grid(inventory, cached));
        assertEquals(wide, stock.amount(key));
    }

    private static IGrid grid(MEStorage inventory, KeyCounter cached) {
        var grid = mock(IGrid.class);
        var storage = mock(IStorageService.class);
        when(grid.getStorageService()).thenReturn(storage);
        when(storage.getInventory()).thenReturn(inventory);
        when(storage.getCachedInventory()).thenReturn(cached);
        return grid;
    }
}
