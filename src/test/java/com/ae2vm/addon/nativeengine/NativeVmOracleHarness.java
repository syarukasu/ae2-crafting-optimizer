package com.ae2vm.addon.nativeengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
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
        compare(service, output, requested, stock, strategy, expected, 0);
    }

    public static void compare(ICraftingService service, AEKey output, long requested,
            Map<AEKey, Long> stock, CalculationStrategy strategy, ICraftingPlan expected, long additionalReturnBytes) {
        compare(service, output, requested, stock, strategy, expected, additionalReturnBytes, false);
    }

    public static void compareFundedMissing(ICraftingService service, AEKey output, long requested,
            Map<AEKey, Long> stock, CalculationStrategy strategy, ICraftingPlan expected) {
        compare(service, output, requested, stock, strategy, expected, 0, true);
    }

    private static void compare(ICraftingService service, AEKey output, long requested,
            Map<AEKey, Long> stock, CalculationStrategy strategy, ICraftingPlan expected,
            long additionalReturnBytes, boolean fundedMissingRoutes) {
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
        var requester = mock(appeng.api.networking.crafting.ICraftingSimulationRequester.class);
        var node = mock(appeng.api.networking.IGridNode.class);
        when(requester.getGridNode()).thenReturn(node);
        when(node.getGrid()).thenReturn(grid);
        var capture = new NativeVmPlanningContext(grid, level, requester, accounting.exactStock(grid),
                new NativeVmAccess(), new NativeVmTiming());
        capture.prepare(output);
        var result = capture.plan(output, BigInteger.valueOf(requested), strategy == CalculationStrategy.CRAFT_LESS,
                ignored -> { });
        Map<IPatternDetails, BigInteger> times = new LinkedHashMap<>(), expectedTimes = new LinkedHashMap<>();
        result.crafts().forEach((id, count) -> times.merge(capture.bindings().get(id), count, BigInteger::add));
        expected.patternTimes().forEach((pattern, count) -> expectedTimes.put(pattern, BigInteger.valueOf(count)));
        assertEquals(BigInteger.valueOf(expected.finalOutput().amount()), result.requested(), "native VM request");
        if (fundedMissingRoutes && expected.simulation()) {
            // #218: AE2's first-route missing plan is not the oracle for selecting a funded alternative.
            assertFixedRecipeBalance(service, stock, result, times);
            return;
        }
        assertEquals(expectedTimes, times, "native VM executions");
        assertEquals(counts(expected.usedItems()), result.used(), "native VM used items");
        assertEquals(counts(expected.emittedItems()), result.emitted(), "native VM emitted items");
        assertEquals(counts(expected.missingItems()), result.missing(), "native VM missing items");
        assertEquals(BigInteger.valueOf(expected.bytes()).add(BigInteger.valueOf(additionalReturnBytes)),
                result.integerCharges(), "AE2 charge plus explicitly accounted alternative-only returns");
    }

    static void assertFixedRecipeBalance(ICraftingService service, Map<AEKey, Long> stock,
            VmQuantities result, Map<IPatternDetails, BigInteger> times) {
        var balance = new LinkedHashMap<AEKey, BigInteger>();
        for (var entry : result.used().entrySet()) {
            assertTrue(entry.getValue().signum() > 0);
            assertTrue(entry.getValue().compareTo(BigInteger.valueOf(stock.getOrDefault(entry.getKey(), 0L))) <= 0,
                    "Missing-pass trial overspent actual stock");
        }
        for (var key : result.emitted().keySet()) assertTrue(service.canEmitFor(key), "Invented emitter");
        for (var supply : java.util.List.of(result.used(), result.missing(), result.emitted()))
            supply.forEach((key, amount) -> {
                assertTrue(amount.signum() > 0);
                balance.merge(key, amount, BigInteger::add);
            });
        var minimumBytes = result.requested().multiply(BigInteger.valueOf(8))
                .divide(BigInteger.valueOf(result.root().getType().getAmountPerByte()));
        for (var entry : times.entrySet()) {
            var pattern = entry.getKey();
            var count = entry.getValue();
            assertTrue(count.signum() > 0);
            minimumBytes = minimumBytes.add(count);
            for (var output : pattern.getOutputs()) {
                assertTrue(service.getCraftingFor(output.what()).contains(pattern), "Unregistered producer");
                balance.merge(output.what(), BigInteger.valueOf(output.amount()).multiply(count), BigInteger::add);
            }
            for (var input : pattern.getInputs()) {
                assertEquals(1, input.getPossibleInputs().length, "This oracle covers fixed-input networks only");
                var template = input.getPossibleInputs()[0];
                assertEquals(null, input.getRemainingKey(template.what()), "Returns need their separate semantic oracle");
                var amount = BigInteger.valueOf(template.amount()).multiply(BigInteger.valueOf(input.getMultiplier())).multiply(count);
                balance.merge(template.what(), amount.negate(), BigInteger::add);
                minimumBytes = minimumBytes.add(amount.multiply(BigInteger.valueOf(8))
                        .divide(BigInteger.valueOf(template.what().getType().getAmountPerByte())));
            }
        }
        balance.merge(result.root(), result.requested().negate(), BigInteger::add);
        balance.forEach((key, amount) -> assertTrue(amount.signum() >= 0, "Unfunded recipe input: " + key + "=" + amount));
        assertTrue(result.integerCharges().compareTo(minimumBytes) >= 0, "Omitted stack/pattern capacity");
    }

    private static Map<AEKey, BigInteger> counts(KeyCounter counter) {
        Map<AEKey, BigInteger> result = new LinkedHashMap<>();
        for (var entry : counter) if (entry.getLongValue() > 0)
            result.put(entry.getKey(), BigInteger.valueOf(entry.getLongValue()));
        return result;
    }
}
