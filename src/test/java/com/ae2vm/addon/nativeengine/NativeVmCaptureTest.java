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
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NativeVmCaptureTest {
    @BeforeAll static void bootstrap() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        var bootstrapped = net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapped.setAccessible(true);
        bootstrapped.setBoolean(null, true);
        if (net.minecraft.core.registries.BuiltInRegistries.PAINTING_VARIANT.size() == 0)
            net.minecraft.core.registries.BuiltInRegistries.bootStrap();
        com.syaru.ae2craftingoptimizer.testsupport.TestKeyTypes.initialize();
        var field = com.syaru.ae2craftingoptimizer.config.ACOConfig.class.getDeclaredField("SPEC");
        field.setAccessible(true);
        var spec = (net.minecraftforge.common.ForgeConfigSpec) field.get(null);
        var defaults = com.electronwill.nightconfig.core.CommentedConfig.inMemory();
        spec.correct(defaults);
        spec.setConfig(defaults);
    }

    @Test void nativeAcquisitionMultipliesInputCoefficientsBeyondLongWithoutAnAcoGraph() {
        var raw = AEItemKey.of(Items.IRON_INGOT);
        var out = AEItemKey.of(Items.DIAMOND);
        var coefficient = BigInteger.valueOf(Long.MAX_VALUE).pow(2);
        for (var multiplier : List.of(BigInteger.ONE, BigInteger.TWO, BigInteger.valueOf(Long.MAX_VALUE),
                BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE), BigInteger.TEN.pow(64))) {
        var demand = coefficient.multiply(multiplier);
        for (var stock : List.of(BigInteger.ZERO, coefficient, demand)) {
            var grid = mock(IGrid.class);
            var service = mock(ICraftingService.class);
            when(grid.getCraftingService()).thenReturn(service);
            var pattern = mock(IPatternDetails.class);
            when(pattern.getDefinition()).thenReturn(AEItemKey.of(Items.PAPER));
            when(pattern.supportsPushInputsToExternalInventory()).thenReturn(true);
            var input = mock(IPatternDetails.IInput.class);
            when(input.getPossibleInputs()).thenReturn(new GenericStack[] {new GenericStack(raw, Long.MAX_VALUE)});
            when(input.getMultiplier()).thenReturn(Long.MAX_VALUE);
            when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[] {input});
            when(pattern.getOutputs()).thenReturn(new GenericStack[] {new GenericStack(out, 1)});
            when(service.getCraftingFor(out)).thenReturn(List.of(pattern));
            when(service.getCraftingFor(raw)).thenReturn(List.of());
            var level = mock(Level.class);
            var server = mock(MinecraftServer.class);
            when(level.getServer()).thenReturn(server);
            when(server.isSameThread()).thenReturn(true);
            when(input.isValid(raw, level)).thenReturn(true);
            var amounts = Map.<AEKey, BigInteger>of(raw, stock);
            VmAccounting accounting = new VmAccounting() {
                public BigInteger maximumCount() { return BigInteger.ONE.shiftLeft(4096); }
                public Stock exactStock(IGrid ignored) { return new Stock(amounts, true, amounts.keySet()); }
                public ICraftingPlan wideResult(IGrid ignored, Level world, NativeVmResult result) {
                    throw new AssertionError("capture never materializes or executes a result");
                }
            };
            var capture = new NativeVmCapture(grid, level, accounting);
            var result = capture.plan(out, multiplier, false);
            capture.validate();
            assertEquals(multiplier, result.requested());
            assertEquals(multiplier, result.crafts().get("vm-0"));
            assertEquals(stock, result.used().getOrDefault(raw, BigInteger.ZERO));
            assertEquals(demand.subtract(stock), result.missing().getOrDefault(raw, BigInteger.ZERO));
            // The calculation/API bridge must not inspect a processing pattern again
            // or require a crafting-table executor to materialize its exact result.
            clearInvocations(pattern);
            var bridged = assertDoesNotThrow(() -> new com.syaru.ae2craftingoptimizer.engine.VmBigIntegerAccounting()
                    .wideResult(grid, level, new NativeVmResult(result, capture.bindings(), BigInteger.ONE, true)));
            assertEquals(!stock.equals(demand), bridged.simulation());
            var api = com.syaru.ae2craftingoptimizer.api.big.BigCraftingEngineApi.inspectBigIntegerPlan(bridged)
                    .orElseThrow();
            assertEquals(result.requested(), api.exactRequestedAmount());
            assertEquals(result.used(), api.usedItems());
            assertEquals(result.missing(), api.missingItems());
            assertEquals(result.emitted(), api.emittedItems());
            assertEquals(Map.of(pattern, multiplier), api.patternTimes());
            verifyNoInteractions(pattern);
            verify(service, atLeastOnce()).getCraftingFor(out);
        }
        }
    }

    @Test void incompleteStockFailsOnlyWhenAnUnprovenKeyIsRead() {
        AEKey known = AEItemKey.of(Items.IRON_INGOT), unknown = AEItemKey.of(Items.GOLD_INGOT);
        var stock = new VmAccounting.Stock(Map.of(known, BigInteger.TEN), false, java.util.Set.of(known));
        assertEquals(BigInteger.TEN, stock.amount(known));
        assertThrows(IllegalStateException.class, () -> stock.amount(unknown));
    }
}
