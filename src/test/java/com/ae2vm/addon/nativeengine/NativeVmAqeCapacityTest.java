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
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;
import com.syaru.ae2craftingoptimizer.api.big.AeKeyBigCraftingCodec;
import com.syaru.ae2craftingoptimizer.api.big.BigCraftingEngineApi;
import com.syaru.ae2craftingoptimizer.api.big.BigIntegerCraftingPlanView;
import com.syaru.ae2craftingoptimizer.engine.VmBigIntegerAccounting;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

/** #218: production VM computation and API bridge; mixed-in scheduling is covered by GameTests. */
class NativeVmAqeCapacityTest {
    private static final BigInteger CAPACITY = BigInteger.TEN.pow(1024).subtract(BigInteger.ONE);
    private static final BigInteger NINE = BigInteger.valueOf(9), OVERHEAD = BigInteger.valueOf(32);

    @BeforeAll static void bootstrap() throws Exception { NativeVmCaptureTest.bootstrap(); }

    @Test void advertisedVmLimitUsesTheSameExactAuthorityAsThePublicApi() {
        assertEquals(3402, CAPACITY.bitLength());
        var maximum = BigCraftingEngineApi.maximumSupportedAmount();
        assertTrue(maximum.compareTo(CAPACITY) > 0, "Do not shrink intermediate values or combined CPU capacity");
        assertTrue(maximum.equals(new VmBigIntegerAccounting().maximumCount()),
                "VM must not advertise binary values beyond the public API's decimal maximum");
    }

    @Test void vmAndApiPreserveCountsAndSharedByproductsNearQuantumCapacity() throws Exception {
        try (var fixture = new Fixture(true)) {
            var largestFit = CAPACITY.subtract(OVERHEAD).divide(NINE);
            for (var order : List.of(BigInteger.ONE, BigInteger.TWO, BigInteger.valueOf(Long.MAX_VALUE),
                    BigInteger.ONE.shiftLeft(63), largestFit, largestFit.add(BigInteger.ONE))) {
                var required = order.multiply(BigInteger.TWO);
                for (var supplied : List.of(BigInteger.ZERO, required.divide(BigInteger.TWO), required)) {
                    var plan = view(fixture.calculate(order, supplied));
                    assertEquals(order, plan.exactRequestedAmount());
                    // 4 item nodes: q + 3q + 2q + q; 2q pattern executions; 4 * 8 node bytes.
                    assertEquals(order.multiply(NINE).add(OVERHEAD), plan.exactBytes());
                    assertEquals(Map.of(fixture.root, order, fixture.child, order), plan.patternTimes());
                    assertEquals(count(fixture.raw, supplied), plan.usedItems());
                    assertEquals(count(fixture.raw, required.subtract(supplied)), plan.missingItems());
                    assertTrue(plan.emittedItems().isEmpty());
                    assertEquals(!supplied.equals(required), plan.simulation());
                    if (fixture.last != null) assertTrue(fixture.last.amounts().instructions() < 1000,
                            "Quantity must not become a per-item execution loop");
                }
            }
        }
    }

    @Test void calculatedCapacityCanBeFilledRestoredAndReleasedWithoutOverbooking() throws Exception {
        try (var fixture = new Fixture(true)) {
            var order = CAPACITY.subtract(OVERHEAD).divide(NINE);
            var plan = view(fixture.calculate(order, order.multiply(BigInteger.TWO)));
            var host = BigCraftingEngineApi.createHost(CAPACITY, AeKeyBigCraftingCodec.INSTANCE);
            var job = UUID.randomUUID();
            assertTrue(host.reserveExternal(job, plan.exactBytes()));
            assertEquals(BigInteger.valueOf(4), host.available());
            var remainderJob = UUID.randomUUID();
            assertTrue(host.reserveExternal(remainderJob, host.available()));
            assertEquals(CAPACITY, host.reserved());
            assertEquals(BigInteger.ZERO, host.available());
            var saved = host.save();
            assertFalse(host.reserveExternal(UUID.randomUUID(), BigInteger.ONE));
            assertEquals(saved, host.save());

            var restored = BigCraftingEngineApi.loadHost(saved, CAPACITY, AeKeyBigCraftingCodec.INSTANCE);
            restored.replaceExternalReservations(Map.of(job, BigInteger.valueOf(Long.MAX_VALUE),
                    remainderJob, BigInteger.valueOf(4)));
            assertEquals(CAPACITY, restored.reserved(), "Legacy CPU projections must not replace exact reservations");
            assertEquals(plan.exactBytes(), restored.releaseExternal(job));
            assertEquals(BigInteger.ZERO, restored.releaseExternal(job));
            assertEquals(BigInteger.valueOf(4), restored.releaseExternal(remainderJob));
            assertEquals(CAPACITY, restored.available());

            var above = view(fixture.calculate(order.add(BigInteger.ONE), order.add(BigInteger.ONE).multiply(BigInteger.TWO)));
            assertEquals(CAPACITY.add(BigInteger.valueOf(5)), above.exactBytes());
            assertFalse(above.simulation(), "Stock sufficiency and CPU admission are different decisions");
            var beforeDecline = restored.save();
            assertFalse(restored.reserveExternal(UUID.randomUUID(), above.exactBytes()));
            assertEquals(beforeDecline, restored.save());
        }
    }

    @Test void byteOnlyOverflowStillReachesTheExactCapacityBridge() throws Exception {
        try (var fixture = new Fixture(false)) {
            var order = BigInteger.valueOf(Long.MAX_VALUE).subtract(BigInteger.valueOf(16))
                    .divide(BigInteger.valueOf(3)).add(BigInteger.ONE);
            var plan = view(fixture.calculate(order, order));
            assertNotNull(fixture.last);
            assertFalse(fixture.last.wideQuantity());
            assertTrue(fixture.last.wide());
            assertEquals(order.multiply(BigInteger.valueOf(3)).add(BigInteger.valueOf(16)), plan.exactBytes());
            assertEquals(order, plan.exactRequestedAmount());
            assertEquals(Map.of(fixture.raw, order), plan.usedItems());
        }
    }

    private static Map<AEKey, BigInteger> count(AEKey key, BigInteger amount) {
        return amount.signum() == 0 ? Map.of() : Map.of(key, amount);
    }

    private static Map<AEKey, BigInteger> exact(KeyCounter counter) {
        Map<AEKey, BigInteger> values = new LinkedHashMap<>();
        for (var entry : counter) values.put(entry.getKey(), BigInteger.valueOf(entry.getLongValue()));
        return values;
    }

    private static BigIntegerCraftingPlanView view(ICraftingPlan plan) {
        return BigCraftingEngineApi.inspectBigIntegerPlan(plan).orElseGet(() -> {
            Map<IPatternDetails, BigInteger> patterns = new LinkedHashMap<>();
            plan.patternTimes().forEach((pattern, times) -> patterns.put(pattern, BigInteger.valueOf(times)));
            return new BigIntegerCraftingPlanView(plan.finalOutput(), BigInteger.valueOf(plan.bytes()), plan.simulation(),
                    patterns, exact(plan.usedItems()), exact(plan.emittedItems()), exact(plan.missingItems()));
        });
    }

    private static IPatternDetails pattern(List<GenericStack> inputs, List<GenericStack> outputs) {
        var definition = new ItemStack(Items.PAPER);
        var in = new ListTag(); var out = new ListTag();
        inputs.forEach(value -> in.add(GenericStack.writeTag(com.ae2vm.addon.PortStacks.REGISTRIES, value)));
        outputs.forEach(value -> out.add(GenericStack.writeTag(com.ae2vm.addon.PortStacks.REGISTRIES, value)));
        com.ae2vm.addon.PortStacks.put(definition, "in", in); com.ae2vm.addon.PortStacks.put(definition, "out", out);
        return new AEProcessingPattern(AEItemKey.of(definition));
    }

    private static final class Fixture implements AutoCloseable {
        final AEKey raw = AEItemKey.of(Items.IRON_INGOT), output = AEItemKey.of(Items.DIAMOND);
        final IPatternDetails root, child;
        final IGrid grid = mock(IGrid.class);
        final Level level = mock(Level.class);
        final MinecraftServer server = mock(MinecraftServer.class);
        final ICraftingSimulationRequester requester = mock(ICraftingSimulationRequester.class);
        final VmAccounting accounting;
        final Thread owner = Thread.currentThread();
        volatile Map<AEKey, BigInteger> stock = Map.of();
        volatile NativeVmResult last;

        @SuppressWarnings("unchecked")
        Fixture(boolean byproduct) throws Exception {
            var intermediate = AEItemKey.of(Items.GOLD_INGOT);
            var side = AEItemKey.of(Items.EMERALD);
            child = pattern(List.of(new GenericStack(raw, 2)),
                    List.of(new GenericStack(intermediate, 3), new GenericStack(side, 1)));
            root = pattern(byproduct ? List.of(new GenericStack(intermediate, 3), new GenericStack(side, 1))
                    : List.of(new GenericStack(raw, 1)), List.of(new GenericStack(output, 1)));
            var recipes = byproduct ? Map.of(output, List.of(root), intermediate, List.of(child)) : Map.of(output, List.of(root));
            var service = mock(ICraftingService.class);
            when(grid.getCraftingService()).thenAnswer(call -> { assertServer(); return service; });
            when(service.getCraftingFor(any())).thenAnswer(call -> { assertServer(); return recipes.getOrDefault(call.getArgument(0), List.of()); });
            when(service.canEmitFor(any())).thenAnswer(call -> { assertServer(); return false; });
            var node = mock(IGridNode.class);
            when(node.getGrid()).thenReturn(grid);
            when(requester.getGridNode()).thenReturn(node);
            when(requester.getActionSource()).thenReturn(mock(IActionSource.class));
            when(level.getServer()).thenReturn(server);
            when(server.isSameThread()).thenReturn(true);
            var adapter = new VmBigIntegerAccounting();
            accounting = new VmAccounting() {
                public BigInteger maximumCount() { return adapter.maximumCount(); }
                public Stock exactStock(IGrid ignored) {
                    assertServer(); return new Stock(stock, true, stock.keySet());
                }
                public ICraftingPlan wideResult(IGrid grid, Level world, NativeVmResult result) {
                    assertServer(); last = result; return adapter.wideResult(grid, world, result);
                }
            };
        }

        private void assertServer() { assertSame(owner, Thread.currentThread()); }

        ICraftingPlan calculate(BigInteger order, BigInteger supplied) throws Exception {
            stock = count(raw, supplied); last = null;
            var captured = NativeVm.requestStock(grid, requester, accounting);
            var timing = new NativeVmTiming();
            var access = new NativeVmAccess();
            var context = new NativeVmPlanningContext(grid, level, requester, captured, access, timing);
            context.prepare(output);
            return NativeVm.compute(0, System.nanoTime(), grid, level, output, order,
                    CalculationStrategy.REPORT_MISSING_ITEMS, accounting, context, accounting.orderContext(grid, level), timing, access);
        }

        public void close() { }
    }
}
