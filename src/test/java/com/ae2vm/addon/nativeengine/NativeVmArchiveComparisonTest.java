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
import com.ae2vm.addon.vm.ArchivedVmFixture;
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

class NativeVmArchiveComparisonTest {
    @BeforeAll static void bootstrap() throws Exception { NativeVmCaptureTest.bootstrap(); }

    @Test @Timeout(90) void bothCalculatorsUseIdenticalFrozenNativePatternsAndExactStock() throws Exception {
        int depth = 24;
        var keys = new ArrayList<AEKey>();
        var patterns = new ArrayList<IPatternDetails>();
        for (int i = 0; i <= depth; i++) {
            var stack = new ItemStack(Items.PAPER);
            com.ae2vm.addon.PortStacks.putInt(stack, "comparison_stage", i);
            keys.add(AEItemKey.of(stack));
        }
        for (int i = 0; i < depth; i++) {
            var definition = new ItemStack(Items.PAPER);
            var inputs = new ListTag();
            inputs.add(GenericStack.writeTag(com.ae2vm.addon.PortStacks.REGISTRIES, new GenericStack(keys.get(i + 1), 1)));
            if (i + 2 <= depth) inputs.add(GenericStack.writeTag(com.ae2vm.addon.PortStacks.REGISTRIES, new GenericStack(keys.get(i + 2), 1)));
            var outputs = new ListTag();
            outputs.add(GenericStack.writeTag(com.ae2vm.addon.PortStacks.REGISTRIES, new GenericStack(keys.get(i), 1)));
            com.ae2vm.addon.PortStacks.put(definition, "in", inputs);
            com.ae2vm.addon.PortStacks.put(definition, "out", outputs);
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
            for (var order : List.of(BigInteger.ONE, BigInteger.valueOf(50), BigInteger.valueOf(100),
                    BigInteger.ONE.shiftLeft(63), BigInteger.TEN.pow(1024).subtract(BigInteger.ONE))) {
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
                        var stock = Map.of(keys.get(depth), available);
                        var context = new NativeVmPlanningContext(grid, level, requester,
                                new VmAccounting.Stock(stock, true, Set.of()), access, new NativeVmTiming());
                        long start = System.nanoTime();
                        context.prepare(keys.get(0));
                        long capture = System.nanoTime() - start;
                        assertFalse(context.requiresLiveAccess());
                        var field = NativeVmPlanningContext.class.getDeclaredField("programsByKey");
                        field.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        var snapshot = Map.copyOf((Map<AEKey, List<IPatternDetails>>) field.get(context));
                        access.finish();
                        ArchivedVmFixture.Result previous = null;
                        for (boolean historical : repeat % 2 == 0 ? new boolean[]{true, false} : new boolean[]{false, true}) {
                            var result = worker.submit(() -> ArchivedVmFixture.run(historical, keys.get(0), order, stock, snapshot))
                                    .get(10, TimeUnit.SECONDS);
                            var plan = result.plan();
                            assertEquals(order, plan.finalOutput().amount());
                            assertEquals(available, plan.usedItems().get(keys.get(depth)));
                            assertEquals(needed.subtract(available), plan.missingItems().get(keys.get(depth)));
                            assertTrue(plan.emittedItems().quantities().isEmpty());
                            assertEquals(depth, plan.patternTimes().size());
                            for (int i = 0; i < depth; i++)
                                assertEquals(demands.get(i), plan.patternTimes().get(snapshot.get(keys.get(i)).get(0)));
                            if (previous != null) {
                                assertEquals(previous.plan().bytes(), plan.bytes());
                                assertEquals(previous.plan().patternTimes(), plan.patternTimes());
                            }
                            measurements.add(Map.of("version", historical ? "archived-arithmetic" : "candidate",
                                    "quantity", order.toString(), "stock", mode, "repeat", repeat,
                                    "captureNs", capture, "planNs", result.elapsedNs(), "work", result.work(),
                                    "worldReadsDuringPlan", access.metrics().reads(), "quantitiesVerified", true));
                            assertEquals(0, access.metrics().reads());
                            previous = result;
                        }
                    }
                }
            }
        } finally { worker.shutdownNow(); }
        var report = Path.of("build/reports/issue218-vm-reuse/archive-snapshot-comparison.json");
        Files.createDirectories(report.getParent());
        try (var writer = Files.newBufferedWriter(report)) {
            new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                    "scope", "synthetic 24-stage shared DAG; same native-pattern snapshot; not live pack",
                    "baseline", "archived dev.19 source arithmetic; current PatternCompiler and snapshot adapters",
                    "measurements", measurements), writer);
        }
        System.out.println("Archived VM plus snapshot comparison: " + report);
    }
}
