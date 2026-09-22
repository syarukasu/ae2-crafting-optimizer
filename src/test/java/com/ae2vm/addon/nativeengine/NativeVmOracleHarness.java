package com.ae2vm.addon.nativeengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import com.syaru.ae2vm.exact.ExactBranchVM;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

/** Runs production VM acquisition against the same service as AE2's real reference calculation. */
public final class NativeVmOracleHarness {
    private NativeVmOracleHarness() { }

    public static void compare(ICraftingService service, AEKey output, long requested,
            Map<AEKey, Long> stock, CalculationStrategy strategy, ICraftingPlan expected) {
        var grid = mock(IGrid.class);
        when(grid.getCraftingService()).thenReturn(service);
        var server = mock(MinecraftServer.class);
        when(server.isSameThread()).thenReturn(true);
        var level = mock(Level.class);
        when(level.getServer()).thenReturn(server);
        Map<AEKey, BigInteger> amounts = new LinkedHashMap<>();
        stock.forEach((key, value) -> amounts.put(key, BigInteger.valueOf(value)));
        VmAccounting accounting = new VmAccounting() {
            public BigInteger maximumCount() { return BigInteger.ONE.shiftLeft(4096); }
            public Stock exactStock(IGrid ignored) { return new Stock(amounts, true, amounts.keySet()); }
            public ICraftingPlan wideResult(IGrid ignored, Level world, NativeVmResult result) {
                throw new AssertionError("oracle capture does not submit jobs");
            }
        };
        var capture = new NativeVmCapture(grid, level, accounting);
        var result = new ExactBranchVM<>(output, capture::candidates, capture::emittable, capture::amount,
                k -> k.getType().getAmountPerByte(), ignored -> {}, accounting.maximumCount(), capture)
                .plan(BigInteger.valueOf(requested), strategy == CalculationStrategy.CRAFT_LESS);
        capture.validate();
        Map<IPatternDetails, BigInteger> times = new LinkedHashMap<>(), expectedTimes = new LinkedHashMap<>();
        result.crafts().forEach((id, count) -> times.merge(capture.bindings().get(id), count, BigInteger::add));
        expected.patternTimes().forEach((pattern, count) -> expectedTimes.put(pattern, BigInteger.valueOf(count)));
        assertEquals(BigInteger.valueOf(expected.finalOutput().amount()), result.requested(), "native VM request");
        assertEquals(expectedTimes, times, "native VM executions");
        assertEquals(counts(expected.usedItems()), result.used(), "native VM used items");
        assertEquals(counts(expected.emittedItems()), result.emitted(), "native VM emitted items");
        assertEquals(counts(expected.missingItems()), result.missing(), "native VM missing items");
        assertEquals(expected.bytes(), (long) Math.ceil(result.legacyBytes()), "native VM normal byte semantics");
    }

    private static Map<AEKey, BigInteger> counts(KeyCounter counter) {
        Map<AEKey, BigInteger> result = new LinkedHashMap<>();
        for (var entry : counter) if (entry.getLongValue() > 0)
            result.put(entry.getKey(), BigInteger.valueOf(entry.getLongValue()));
        return result;
    }
}
