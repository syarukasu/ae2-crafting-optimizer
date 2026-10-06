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
import appeng.crafting.pattern.AEProcessingPattern;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Acquisition + arithmetic on the production entry; not a live pack/network acceptance test. */
class NativeVmFrozenOrderBenchmarkTest {
    @BeforeAll static void bootstrap() throws Exception { NativeVmCaptureTest.bootstrap(); }

    @Test @Timeout(60) void shared48StageOrderMatrixHasExactQuantitiesAndNoArithmeticWorldReads() throws Exception {
        int depth = 48;
        var keys = new ArrayList<AEKey>();
        var patterns = new ArrayList<IPatternDetails>();
        for (int i = 0; i <= depth; i++) {
            var stack = new ItemStack(Items.PAPER);
            stack.getOrCreateTag().putInt("frozen_stage", i);
            keys.add(AEItemKey.of(stack));
        }
        for (int i = 0; i < depth; i++) {
            var definition = new ItemStack(Items.PAPER);
            var inputs = new ListTag();
            inputs.add(GenericStack.writeTag(new GenericStack(keys.get(i + 1), 1)));
            if (i + 2 <= depth) inputs.add(GenericStack.writeTag(new GenericStack(keys.get(i + 2), 1)));
            var outputs = new ListTag();
            outputs.add(GenericStack.writeTag(new GenericStack(keys.get(i), 1)));
            definition.getOrCreateTag().put("in", inputs);
            definition.getOrCreateTag().put("out", outputs);
            patterns.add(new AEProcessingPattern(AEItemKey.of(definition)));
        }
        var grid = mock(IGrid.class);
        var service = mock(ICraftingService.class);
        var requester = mock(ICraftingSimulationRequester.class);
        var node = mock(IGridNode.class);
        when(requester.getGridNode()).thenReturn(node);
        when(node.getGrid()).thenReturn(grid);
        when(grid.getCraftingService()).thenReturn(service);
        for (int i = 0; i < depth; i++) when(service.getCraftingFor(keys.get(i))).thenReturn(List.of(patterns.get(i)));
        var level = mock(Level.class);
        var measurements = new ArrayList<Map<String, Object>>();
        var worker = Executors.newSingleThreadExecutor();
        try {
            for (var order : List.of(BigInteger.ONE, BigInteger.TWO, new BigInteger("9220000000000000000"),
                    BigInteger.valueOf(Long.MAX_VALUE), BigInteger.ONE.shiftLeft(63),
                    BigInteger.TEN.pow(1024).subtract(BigInteger.ONE))) {
                var demands = new ArrayList<BigInteger>(Collections.nCopies(depth + 1, BigInteger.ZERO));
                demands.set(0, order);
                for (int i = 0; i < depth; i++) {
                    demands.set(i + 1, demands.get(i + 1).add(demands.get(i)));
                    if (i + 2 <= depth) demands.set(i + 2, demands.get(i + 2).add(demands.get(i)));
                }
                for (var mode : List.of("empty", "partial", "funded")) {
                    var needed = demands.get(depth);
                    var available = mode.equals("empty") ? BigInteger.ZERO
                            : mode.equals("partial") ? needed.divide(BigInteger.TWO) : needed;
                    for (int repeat = 0; repeat < 4; repeat++) {
                        var access = new NativeVmAccess();
                        var timing = new NativeVmTiming();
                        long start = System.nanoTime();
                        var context = new NativeVmPlanningContext(grid, level, requester,
                                new VmAccounting.Stock(Map.of(keys.get(depth), available), true, Set.of()), access, timing);
                        context.prepare(keys.get(0));
                        assertFalse(context.requiresLiveAccess());
                        long prepared = System.nanoTime();
                        access.finish();
                        var result = worker.submit(() -> context.plan(keys.get(0), order, false, ignored -> { }))
                                .get(5, TimeUnit.SECONDS);
                        long finished = System.nanoTime();
                        assertEquals(order, result.requested());
                        assertEquals(available, result.used().getOrDefault(keys.get(depth), BigInteger.ZERO));
                        assertEquals(needed.subtract(available), result.missing().getOrDefault(keys.get(depth), BigInteger.ZERO));
                        assertTrue(result.emitted().isEmpty());
                        var bindings = context.bindings();
                        for (var entry : result.crafts().entrySet()) {
                            int index = patterns.indexOf(bindings.get(entry.getKey()));
                            assertTrue(index >= 0);
                            assertEquals(demands.get(index), entry.getValue());
                        }
                        assertEquals(depth, result.crafts().size());
                        assertEquals(1, result.work(), "Shared dependency paths must use one aggregate demand program");
                        assertEquals(0, access.metrics().reads());
                        var row = new LinkedHashMap<String, Object>();
                        row.put("quantity", order.toString()); row.put("stock", mode); row.put("repeat", repeat);
                        row.put("captureNs", prepared - start); row.put("planNs", finished - prepared);
                        row.put("totalNs", finished - start); row.put("worldReadsDuringPlan", access.metrics().reads());
                        row.put("work", result.work()); row.put("quantitiesVerified", true);
                        measurements.add(row);
                    }
                }
            }
        } finally { worker.shutdownNow(); }
        var report = Path.of("build/reports/issue218-order-snapshot/measurements.json");
        Files.createDirectories(report.getParent());
        try (var writer = Files.newBufferedWriter(report)) {
            new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                    "scope", "synthetic 48-stage shared DAG; native AE2 processing patterns; capture+VM; not live pack",
                    "java", System.getProperty("java.version"), "measurements", measurements), writer);
        }
        long worst = measurements.stream().mapToLong(row -> (long) row.get("totalNs")).max().orElseThrow();
        System.out.println("Order snapshot quantity matrix: worst total ms=" + worst / 1_000_000.0 + " report=" + report);
    }
}
