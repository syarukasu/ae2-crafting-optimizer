package com.ae2vm.addon.vm;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.ae2vm.addon.compiler.PatternCompiler;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class VmDemandAggregationTest {
    private static final BigInteger ZERO = BigInteger.ZERO, ONE = BigInteger.ONE;
    @BeforeAll static void bootstrap() throws Exception { VmBulkStateTest.bootstrap(); }

    @Test @Timeout(30) void requesterSharedDemandDoesNotRepeatQuantityBitSearch() {
        var recipes = dag(6);
        var order = BigInteger.valueOf(Long.MAX_VALUE);
        var available = requirements(6, order.divide(BigInteger.valueOf(3))).get(6);
        var stock = Map.of(key(6), available);
        VmCraftingPlan expected;
        long before;
        try (var scope = PatternCompiler.openScope()) {
            var vm = executor(recipes, order, stock, false);
            expected = vm.execute(order, true);
            before = vm.work();
        }
        try (var scope = PatternCompiler.openScope()) {
            var vm = executor(recipes, order, stock, true);
            var result = vm.execute(order, true);
            assertPlanEquals(expected, result);
            assertDag(result, recipes, order.divide(BigInteger.valueOf(3)), available);
            System.out.printf("Requester shared demand: orderedWork=%d optimizedWork=%d%n", before, vm.work());
            assertTrue(vm.work() < 10, "Fixed demand must not execute one trial per quantity bit");
        }
    }

    @Test @Timeout(30) void requesterDirectQuantitiesStayExactThrough3402Bits() {
        var recipes = dag(6);
        for (var order : List.of(ONE, BigInteger.TWO, BigInteger.valueOf(1000),
                BigInteger.valueOf(Long.MAX_VALUE), ONE.shiftLeft(63), BigInteger.TEN.pow(1024).subtract(ONE))) {
            for (int supply = 0; supply <= 2; supply++) {
                var demand = requirements(6, order).get(6);
                var available = demand.multiply(BigInteger.valueOf(supply)).divide(BigInteger.TWO);
                var unit = requirements(6, ONE).get(6);
                var funded = order.min(available.divide(unit));
                try (var scope = PatternCompiler.openScope()) {
                    var vm = executor(recipes, order, Map.of(key(6), available), true);
                    var result = vm.execute(order, true);
                    assertDag(result, recipes, funded.signum() == 0 ? order : funded, available);
                    assertTrue(vm.work() < 10);
                }
            }
        }
    }

    @Test @Timeout(60) void requesterRoundingStockAndAlternativeMatrixMatchesOrdered() {
        var random = new Random(0x218C1E55);
        for (int sample = 0; sample < 500; sample++) {
            var recipes = dag(2 + random.nextInt(5));
            for (var entry : recipes.entrySet()) {
                var p = entry.getValue().get(0);
                var output = new GenericStack(entry.getKey(), 1 + random.nextInt(7));
                when(p.getPrimaryOutput()).thenReturn(output);
                when(p.getOutputs()).thenReturn(new GenericStack[]{output});
            }
            if (sample % 4 == 0) {
                var first = recipes.get(key(0)).get(0);
                recipes.put(key(0), List.of(first, pattern(key(0), 3, input(key(99), 2))));
            }
            var stock = new LinkedHashMap<AEKey, BigInteger>();
            stock.put(key(recipes.size()), BigInteger.valueOf(random.nextInt(80)));
            stock.put(key(99), BigInteger.valueOf(random.nextInt(30)));
            if (sample % 3 == 0) stock.put(key(1), BigInteger.valueOf(random.nextInt(30)));
            var order = BigInteger.valueOf(1 + random.nextInt(150));
            VmCraftingPlan expected;
            try (var scope = PatternCompiler.openScope()) {
                expected = executor(recipes, order, stock, false).execute(order, true);
            }
            try (var scope = PatternCompiler.openScope()) {
                var actual = executor(recipes, order, stock, true).execute(order, true);
                if (!expected.bytes().equals(actual.bytes())) {
                    System.out.printf("Requester capacity sample=%d depth=%d order=%s stock=%s expectedOutput=%s actualOutput=%s%n",
                            sample, recipes.size(), order, stock, expected.finalOutput(), actual.finalOutput());
                    recipes.forEach((k, ps) -> ps.forEach(p -> System.out.printf("%s output=%s%n", k, p.getPrimaryOutput())));
                }
                assertPlanEquals(expected, actual);
            }
        }
    }

    @Test @Timeout(30) void requesterFractionalBatchesOnlySearchTheRoundingInterval() {
        var root = pattern(key(0), 3, input(key(1), 2));
        var child = pattern(key(1), 7, input(key(2), 5));
        var recipes = Map.<AEKey, List<IPatternDetails>>of(key(0), List.of(root), key(1), List.of(child));
        var order = BigInteger.TEN.pow(1024).subtract(ONE);
        var raw = order.divide(BigInteger.valueOf(11));
        var times = raw.divide(BigInteger.valueOf(5)).multiply(BigInteger.valueOf(7)).divide(BigInteger.TWO);
        var output = times.multiply(BigInteger.valueOf(3));
        var childTimes = PatternCompiler.ceilDiv(times.multiply(BigInteger.TWO), BigInteger.valueOf(7));
        try (var scope = PatternCompiler.openScope()) {
            var vm = executor(recipes, order, Map.of(key(2), raw), true);
            var result = vm.execute(order, true);
            assertEquals(output, result.finalOutput().amount());
            assertEquals(Map.of(key(2), childTimes.multiply(BigInteger.valueOf(5))), result.usedItems().quantities());
            assertEquals(Map.of(root, times, child, childTimes), result.patternTimes());
            assertFalse(result.simulation());
            assertTrue(result.missingItems().isEmpty());
            assertTrue(vm.work() < 100, "Search width must depend on batch rounding, not 3402 quantity bits");
        }
    }

    private static void assertPlanEquals(VmCraftingPlan expected, VmCraftingPlan actual) {
        assertEquals(expected.finalOutput(), actual.finalOutput());
        assertEquals(expected.simulation(), actual.simulation());
        assertEquals(expected.multiplePaths(), actual.multiplePaths());
        assertEquals(expected.patternTimes(), actual.patternTimes());
        assertEquals(expected.usedItems().quantities(), actual.usedItems().quantities());
        assertEquals(expected.missingItems().quantities(), actual.missingItems().quantities());
        assertEquals(expected.emittedItems().quantities(), actual.emittedItems().quantities());
        assertEquals(expected.bytes(), actual.bytes());
    }

    @Test @Timeout(60) void requesterArithmeticBenchmarkAgainstOrderedReference() {
        var recipes = dag(6);
        var order = BigInteger.valueOf(Long.MAX_VALUE);
        var stock = Map.of(key(6), requirements(6, order.divide(BigInteger.valueOf(3))).get(6));
        var orderedNanos = new ArrayList<Long>();
        var optimizedNanos = new ArrayList<Long>();
        for (int round = 0; round < 9; round++) {
            VmCraftingPlan expected;
            try (var scope = PatternCompiler.openScope()) {
                var vm = executor(recipes, order, stock, false);
                long start = System.nanoTime();
                expected = vm.execute(order, true);
                if (round >= 2) orderedNanos.add(System.nanoTime() - start);
            }
            try (var scope = PatternCompiler.openScope()) {
                var vm = executor(recipes, order, stock, true);
                long start = System.nanoTime();
                var actual = vm.execute(order, true);
                if (round >= 2) optimizedNanos.add(System.nanoTime() - start);
                assertPlanEquals(expected, actual);
            }
        }
        Collections.sort(orderedNanos);
        Collections.sort(optimizedNanos);
        System.out.printf("Synthetic requester arithmetic median: orderedMs=%.3f optimizedMs=%.3f samples=7%n",
                orderedNanos.get(3) / 1e6, optimizedNanos.get(3) / 1e6);
    }

    @Test void requesterStockBoundaryCannotForgetPreviouslyVisitedCapacityNodes() {
        var recipes = dag(6);
        int[] batches = {3, 4, 2, 4, 2, 3};
        for (int i = 0; i < batches.length; i++) {
            var p = recipes.get(key(i)).get(0);
            var output = new GenericStack(key(i), batches[i]);
            when(p.getOutputs()).thenReturn(new GenericStack[]{output});
            when(p.getPrimaryOutput()).thenReturn(output);
        }
        var stock = Map.of(key(6), BigInteger.valueOf(12), key(1), BigInteger.valueOf(6));
        var order = BigInteger.valueOf(145);
        VmCraftingPlan expected;
        try (var scope = PatternCompiler.openScope()) {
            expected = executor(recipes, order, stock, false).execute(order, true);
        }
        try (var scope = PatternCompiler.openScope()) {
            var actual = executor(recipes, order, stock, true).execute(order, true);
            assertPlanEquals(expected, actual);
            assertEquals(BigInteger.valueOf(60), actual.finalOutput().amount());
            assertEquals(BigInteger.valueOf(468), actual.bytes());
        }
    }

    @Test @Timeout(30) void requesterReturnedToolKeepsExactAccountingWhenSupplyIsPartial() {
        var tool = input(key(2), 1);
        when(tool.exactKey()).thenReturn(null);
        when(tool.mayReturnItems()).thenReturn(true);
        when(tool.getRemainingKey(key(2))).thenReturn(key(2));
        var p = pattern(key(0), 1, input(key(1), 2), tool);
        var recipes = Map.<AEKey, List<IPatternDetails>>of(key(0), List.of(p));
        var order = BigInteger.valueOf(1000);
        for (var stock : List.of(Map.<AEKey, BigInteger>of(),
                Map.of(key(1), BigInteger.valueOf(75), key(2), ONE),
                Map.of(key(1), BigInteger.valueOf(2000), key(2), ONE))) {
            VmCraftingPlan expected;
            try (var scope = PatternCompiler.openScope()) {
                expected = executor(recipes, order, stock, false).execute(order, true);
            }
            try (var scope = PatternCompiler.openScope()) {
                assertPlanEquals(expected, executor(recipes, order, stock, true).execute(order, true));
            }
        }
    }

    @Test @Timeout(30) void missingPassDoesNotInventCircuitShortageWhenGoldProducerIsFunded() {
        for (boolean cyclicFirst : List.of(false, true)) {
            for (boolean optimized : List.of(false, true)) {
                var circuit = key(1);
                var gold = key(2);
                var absent = key(3);
                var root = pattern(key(0), 1, input(absent, 1), input(circuit, 12480));
                var rejected = pattern(circuit, 1, input(cyclicFirst ? circuit : key(4), 1));
                var funded = pattern(circuit, 1, input(gold, 1));
                var recipes = Map.<AEKey, List<IPatternDetails>>of(key(0), List.of(root),
                        circuit, List.of(rejected, funded));
                var stock = Map.of(circuit, BigInteger.valueOf(12000), gold, BigInteger.valueOf(480));
                try (var scope = PatternCompiler.openScope()) {
                    var plan = executor(recipes, ONE, stock, optimized).execute(ONE, false);
                    assertEquals(Map.of(absent, ONE), plan.missingItems().quantities(),
                            "A different missing ingredient must not suppress the funded circuit producer; cyclic="
                                    + cyclicFirst + " optimized=" + optimized);
                    assertEquals(stock, plan.usedItems().quantities());
                    assertEquals(Map.of(root, ONE, funded, BigInteger.valueOf(480)), plan.patternTimes());
                    assertTrue(plan.simulation());
                }
            }
        }
    }

    @Test @Timeout(30) void missingPassUsesPartialFundedRoutesBeforeSimulatingOnlyTheRemainder() {
        var circuit = key(1);
        var root = pattern(key(0), 1, input(key(4), 1), input(circuit, 10));
        var goldRoute = pattern(circuit, 1, input(key(2), 1));
        var ironRoute = pattern(circuit, 1, input(key(3), 1));
        var recipes = Map.<AEKey, List<IPatternDetails>>of(key(0), List.of(root),
                circuit, List.of(goldRoute, ironRoute));
        var stock = Map.of(circuit, BigInteger.TWO, key(2), BigInteger.valueOf(3), key(3), BigInteger.valueOf(4));
        for (boolean optimized : List.of(false, true)) {
            try (var scope = PatternCompiler.openScope()) {
                var plan = executor(recipes, ONE, stock, optimized).execute(ONE, false);
                assertEquals(Map.of(key(4), ONE, key(2), ONE), plan.missingItems().quantities());
                assertEquals(stock, plan.usedItems().quantities());
                assertEquals(Map.of(root, ONE, goldRoute, BigInteger.valueOf(4),
                        ironRoute, BigInteger.valueOf(4)), plan.patternTimes());
            }
        }
    }

    @Test @Timeout(60) void missingPassKeepsExactFundedAlternativeThrough3402Bits() {
        var circuit = key(1);
        var root = pattern(key(0), 1, input(key(4), 1), input(circuit, 1));
        var unavailable = pattern(circuit, 1, input(key(2), 1));
        var goldRoute = pattern(circuit, 1, input(key(3), 1));
        var recipes = Map.<AEKey, List<IPatternDetails>>of(key(0), List.of(root),
                circuit, List.of(unavailable, goldRoute));
        for (var order : List.of(ONE, BigInteger.TWO, BigInteger.valueOf(480), BigInteger.valueOf(Long.MAX_VALUE),
                ONE.shiftLeft(63), BigInteger.TEN.pow(1024).subtract(ONE))) {
            for (int supplied = 0; supplied <= 2; supplied++) {
                var existing = order.divide(BigInteger.valueOf(3));
                var remaining = order.subtract(existing);
                var gold = remaining.multiply(BigInteger.valueOf(supplied)).divide(BigInteger.TWO);
                var stock = new HashMap<AEKey, BigInteger>();
                if (existing.signum() > 0) stock.put(circuit, existing);
                if (gold.signum() > 0) stock.put(key(3), gold);
                var expectedMissing = new HashMap<AEKey, BigInteger>();
                expectedMissing.put(key(4), order);
                var shortage = remaining.subtract(gold);
                if (shortage.signum() > 0) expectedMissing.put(key(2), shortage);
                var expectedPatterns = new HashMap<IPatternDetails, BigInteger>();
                expectedPatterns.put(root, order);
                if (gold.signum() > 0) expectedPatterns.put(goldRoute, gold);
                if (shortage.signum() > 0) expectedPatterns.put(unavailable, shortage);
                for (boolean optimized : List.of(false, true)) {
                    try (var scope = PatternCompiler.openScope()) {
                        var plan = executor(recipes, order, stock, optimized).execute(order, false);
                        assertEquals(expectedMissing, plan.missingItems().quantities());
                        assertEquals(stock, plan.usedItems().quantities());
                        assertEquals(expectedPatterns, plan.patternTimes());
                        assertEquals(order, plan.finalOutput().amount());
                    }
                }
            }
        }
    }

    @Test @Timeout(30) void failedMissingPassRouteCannotSpendStockOrLoseFundedByproducts() {
        var circuit = key(1);
        var gold = key(2);
        var byproduct = key(3);
        var root = pattern(key(0), 1, input(key(4), 1), input(circuit, 8), input(byproduct, 12));
        var failed = pattern(circuit, 1, input(gold, 1), input(key(5), 1));
        var funded = pattern(circuit, 1, input(gold, 1));
        when(funded.getOutputs()).thenReturn(new GenericStack[]{new GenericStack(circuit, 1), new GenericStack(byproduct, 2)});
        var recipes = Map.<AEKey, List<IPatternDetails>>of(key(0), List.of(root),
                circuit, List.of(failed, funded), byproduct, List.of(funded));
        var stock = Map.of(circuit, BigInteger.TWO, gold, BigInteger.valueOf(6));
        for (boolean optimized : List.of(false, true)) {
            try (var scope = PatternCompiler.openScope()) {
                var plan = executor(recipes, ONE, stock, optimized).execute(ONE, false);
                assertEquals(Map.of(key(4), ONE), plan.missingItems().quantities());
                assertEquals(stock, plan.usedItems().quantities());
                assertEquals(Map.of(root, ONE, funded, BigInteger.valueOf(6)), plan.patternTimes());
                assertTrue(plan.emittedItems().isEmpty());
            }
        }
    }

    @Test @Timeout(30) void stockedBatchBoundariesDoNotRetryEachCraft() {
        var root = pattern(key(0), 1, input(key(1), 1), input(key(2), 1), input(key(3), 1));
        var recipes = new LinkedHashMap<AEKey, List<IPatternDetails>>();
        recipes.put(key(0), List.of(root, pattern(key(0), 1, input(key(99), 1))));
        int[] batches = {31, 37, 43};
        for (int i = 1; i <= 3; i++) recipes.put(key(i), List.of(pattern(key(i), batches[i - 1], input(key(10 + i), 1))));
        var amount = BigInteger.valueOf(4000);
        var stock = Map.<AEKey, BigInteger>of(key(1), BigInteger.valueOf(23), key(2), BigInteger.valueOf(17),
                key(3), BigInteger.valueOf(11), key(11), amount, key(12), amount, key(13), amount);
        VmCraftingPlan expected;
        long baseline;
        try (var scope = PatternCompiler.openScope()) {
            var vm = new VmInstructionExecution(PatternCompiler.compileRequest(key(0), amount), new VmSimulationState(stock),
                    key -> recipes.getOrDefault(key, List.of()), key -> false, ignored -> { }, true, false);
            expected = vm.execute(amount, false);
            baseline = vm.branchTrials();
        }
        try (var scope = PatternCompiler.openScope()) {
            var vm = new VmInstructionExecution(PatternCompiler.compileRequest(key(0), amount), new VmSimulationState(stock),
                    key -> recipes.getOrDefault(key, List.of()), key -> false, ignored -> { });
            var actual = vm.execute(amount, false);
            assertEquals(expected.patternTimes(), actual.patternTimes());
            assertEquals(expected.usedItems().quantities(), actual.usedItems().quantities());
            assertEquals(expected.missingItems().quantities(), actual.missingItems().quantities());
            assertEquals(expected.bytes(), actual.bytes());
            System.out.printf("Stocked batch boundaries: orderedTrials=%d optimizedTrials=%d work=%d%n", baseline, vm.branchTrials(), vm.work());
            assertTrue(vm.branchTrials() < 1000, "Stocked intervals must be consumed in bulk");
        }
    }

    @Test @Timeout(60) void seededStockedAlternativesPreserveAllLedgers() {
        var random = new Random(0x218570C);
        for (int sample = 0; sample < 400; sample++) {
            var recipes = dag(3 + random.nextInt(5));
            var root = recipes.get(key(0)).get(0);
            recipes.put(key(0), List.of(root, pattern(key(0), 1 + random.nextInt(4), input(key(99), 2))));
            var stock = new LinkedHashMap<AEKey, BigInteger>();
            for (int i = 1; i < recipes.size(); i++) {
                var pattern = recipes.get(key(i)).get(0);
                var output = new GenericStack(key(i), 1 + random.nextInt(7));
                when(pattern.getPrimaryOutput()).thenReturn(output);
                when(pattern.getOutputs()).thenReturn(random.nextBoolean() ? new GenericStack[]{output}
                        : new GenericStack[]{output, new GenericStack(key(1000 + i), 1 + random.nextInt(3))});
                if (random.nextBoolean()) stock.put(key(i), BigInteger.valueOf(random.nextInt(30)));
            }
            stock.put(key(recipes.size()), BigInteger.valueOf(random.nextInt(70)));
            stock.put(key(99), BigInteger.valueOf(random.nextInt(40)));
            sameAsOrdered(recipes, BigInteger.valueOf(1 + random.nextInt(80)), stock);
        }
    }

    @Test @Timeout(60) void stockedAlternativeQuantityAndCapacityMatrixThrough3402Bits() {
        for (var order : List.of(ONE, BigInteger.TWO, BigInteger.valueOf(4000), BigInteger.valueOf(Long.MAX_VALUE),
                ONE.shiftLeft(63), BigInteger.TEN.pow(1024).subtract(ONE))) {
            for (int supply = 0; supply <= 2; supply++) {
                var recipes = new LinkedHashMap<AEKey, List<IPatternDetails>>();
                var root = pattern(key(0), 1, input(key(1), 1), input(key(2), 1), input(key(3), 1));
                recipes.put(key(0), List.of(root, pattern(key(0), 1, input(key(99), 1))));
                var stock = new LinkedHashMap<AEKey, BigInteger>();
                var expectedUsed = new LinkedHashMap<AEKey, BigInteger>();
                var expectedMissing = new LinkedHashMap<AEKey, BigInteger>();
                var crafts = new LinkedHashMap<IPatternDetails, BigInteger>();
                crafts.put(root, order);
                var childCrafts = ZERO;
                int[] sizes = {2, 3, 5};
                for (int i = 1; i <= 3; i++) {
                    var process = pattern(key(i), sizes[i - 1], input(key(i + 10), 1));
                    recipes.put(key(i), List.of(process));
                    var intermediate = order.divide(BigInteger.valueOf(i + 2));
                    var needed = PatternCompiler.ceilDiv(order.subtract(intermediate), BigInteger.valueOf(sizes[i - 1]));
                    var raw = supply == 0 ? ZERO : supply == 1 ? needed.divide(BigInteger.TWO) : needed;
                    stock.put(key(i), intermediate); stock.put(key(i + 10), raw);
                    if (intermediate.signum() > 0) expectedUsed.put(key(i), intermediate);
                    if (raw.signum() > 0) expectedUsed.put(key(i + 10), raw);
                    if (needed.compareTo(raw) > 0) expectedMissing.put(key(i + 10), needed.subtract(raw));
                    crafts.put(process, needed); childCrafts = childCrafts.add(needed);
                }
                var bytes = new VmByteCost(order.multiply(BigInteger.valueOf(4)).add(childCrafts).multiply(BigInteger.valueOf(8)),
                        BigInteger.valueOf(key(0).getType().getAmountPerByte()))
                        .add(new VmByteCost(order.add(childCrafts).add(BigInteger.valueOf(64)), ONE)).ceiling();
                try (var scope = PatternCompiler.openScope()) {
                    var vm = executor(recipes, order, stock, true);
                    var result = vm.execute(order, false);
                    assertEquals(expectedUsed, result.usedItems().quantities());
                    assertEquals(expectedMissing, result.missingItems().quantities());
                    assertEquals(crafts, result.patternTimes());
                    assertEquals(!expectedMissing.isEmpty(), result.simulation());
                    assertEquals(bytes, result.bytes());
                }
            }
        }
    }

    @Test @Timeout(60) void stockedAlternativeBenchmarkAgainstOrderedReference() throws Exception {
        var root = pattern(key(0), 1, input(key(1), 1), input(key(2), 1), input(key(3), 1));
        var recipes = new LinkedHashMap<AEKey, List<IPatternDetails>>();
        recipes.put(key(0), List.of(root, pattern(key(0), 1, input(key(99), 1))));
        int[] batches = {31, 37, 43};
        for (int i = 1; i <= 3; i++) recipes.put(key(i), List.of(pattern(key(i), batches[i - 1], input(key(10 + i), 1))));
        var amount = BigInteger.valueOf(4000);
        var stock = Map.<AEKey, BigInteger>of(key(1), BigInteger.valueOf(23), key(2), BigInteger.valueOf(17),
                key(3), BigInteger.valueOf(11), key(11), amount, key(12), amount, key(13), amount);
        var rows = new ArrayList<Map<String, Object>>();
        VmCraftingPlan expected = null;
        var cpu = java.lang.management.ManagementFactory.getThreadMXBean();
        for (int repeat = 0; repeat < 8; repeat++) for (boolean optimized : repeat % 2 == 0
                ? List.of(false, true) : List.of(true, false)) {
            try (var scope = PatternCompiler.openScope()) {
                var vm = new VmInstructionExecution(PatternCompiler.compileRequest(key(0), amount), new VmSimulationState(stock),
                        key -> recipes.getOrDefault(key, List.of()), key -> false, ignored -> { }, true, optimized);
                long cpuStart = cpu.getCurrentThreadCpuTime(), start = System.nanoTime();
                var result = vm.execute(amount, false);
                long elapsed = System.nanoTime() - start, cpuNs = cpu.getCurrentThreadCpuTime() - cpuStart;
                if (expected == null) expected = result;
                assertEquals(expected.patternTimes(), result.patternTimes());
                assertEquals(expected.usedItems().quantities(), result.usedItems().quantities());
                assertEquals(expected.missingItems().quantities(), result.missingItems().quantities());
                assertEquals(expected.bytes(), result.bytes());
                rows.add(Map.of("repeat", repeat, "optimized", optimized, "elapsedNs", elapsed, "cpuNs", cpuNs,
                        "branchTrials", vm.branchTrials(), "work", vm.work(), "quantitiesVerified", true));
            }
        }
        var report = java.nio.file.Path.of("build/reports/issue218-input-stock/benchmark.json");
        java.nio.file.Files.createDirectories(report.getParent());
        try (var writer = java.nio.file.Files.newBufferedWriter(report)) {
            new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                    "scope", "Synthetic stocked multi-route x4000; ordered VM reference, not production timing",
                    "rows", rows), writer);
        }
    }

    @Test @Timeout(30) void sharedDagDoesNotExpandOncePerPath() {
        var recipes = dag(24);
        try (var scope = PatternCompiler.openScope()) {
            var vm = executor(recipes, ONE, Map.of(), true);
            var plan = vm.execute(ONE, false);
            assertDag(plan, recipes, ONE, ZERO);
            assertTrue(vm.work() < 1024, "Shared recipes must be aggregated, not re-expanded per path");
            assertEquals(0, vm.stateCopies());
            assertTrue(vm.aggregatedBlocks() > 0);
            System.out.println("Issue218 shared DAG: depth=24 work=" + vm.work() + " copies=" + vm.stateCopies());
        }
    }

    @Test @Timeout(30) void deterministicShortageSkipsTheFailedPlanningPass() {
        var recipes = dag(24);
        try (var scope = PatternCompiler.openScope()) {
            var vm = executor(recipes, ONE, Map.of(), true);
            var result = vm.execute(ONE, false);
            assertDag(result, recipes, ONE, ZERO);
            assertEquals(1L, vm.work(), "The proved graph needs one aggregate request, not a failed replay");
            assertEquals(0L, vm.branchTrials());
        }
        sameAsOrdered(dag(16), ONE, Map.of());
    }

    @Test @Timeout(30) void stockedLeafUsesOneDirectPlanWithoutResolvingUnusedProducer() {
        var recipes = dag(24);
        var order = ONE.shiftLeft(70);
        var stock = Map.<AEKey, BigInteger>of(key(24), requirements(24, order).get(24));
        var leafLookups = new AtomicInteger();
        try (var scope = PatternCompiler.openScope()) {
            var vm = new VmInstructionExecution(PatternCompiler.compileRequest(key(0), order),
                    new VmSimulationState(stock), key -> {
                        if (key.equals(key(24))) leafLookups.incrementAndGet();
                        return recipes.getOrDefault(key, List.of());
                    }, key -> false, ignored -> { });
            var plan = vm.execute(order, false);
            assertDag(plan, recipes, order, stock.get(key(24)));
            assertEquals(1, vm.work());
            assertEquals(0, vm.branchTrials());
            assertEquals(0, leafLookups.get());
        }
        var small = dag(5);
        sameAsOrdered(small, BigInteger.valueOf(3),
                Map.of(key(5), requirements(5, BigInteger.valueOf(3)).get(5)));
    }

    @Test @Timeout(30) void partialStockOfUncraftableLeafDirectlyReportsExactShortage() {
        var recipes = dag(16);
        var order = BigInteger.valueOf(100);
        var available = requirements(16, order).get(16).divide(BigInteger.TWO);
        var stock = Map.<AEKey, BigInteger>of(key(16), available);
        var leafLookups = new AtomicInteger();
        try (var scope = PatternCompiler.openScope()) {
            var vm = new VmInstructionExecution(PatternCompiler.compileRequest(key(0), order),
                    new VmSimulationState(stock), key -> {
                        if (key.equals(key(16))) leafLookups.incrementAndGet();
                        return recipes.getOrDefault(key, List.of());
                    }, key -> false, ignored -> { });
            var plan = vm.execute(order, false);
            assertDag(plan, recipes, order, available);
            assertEquals(1, vm.work());
            assertEquals(1, leafLookups.get());
        }
        sameAsOrdered(recipes, order, stock);
    }

    @Test @Timeout(30) void sharedDagQuantitiesAndBytesStayExactThrough3402Bits() {
        var recipes = dag(48);
        for (var order : List.of(ONE, BigInteger.TWO, BigInteger.valueOf(Long.MAX_VALUE),
                ONE.shiftLeft(63), BigInteger.TEN.pow(1024).subtract(ONE))) {
            var demand = requirements(recipes.size(), order).get(recipes.size());
            for (var available : List.of(ZERO, demand.divide(BigInteger.TWO), demand)) {
                try (var scope = PatternCompiler.openScope()) {
                    var vm = executor(recipes, order, Map.of(key(recipes.size()), available), true);
                    var result = vm.execute(order, false);
                    assertDag(result, recipes, order, available);
                    assertTrue(vm.work() < 2048);
                    assertEquals(0, vm.stateCopies());
                    assertTrue(vm.aggregatedBlocks() > 0);
                }
            }
        }
    }

    @Test @Timeout(30) void seededStockBatchAndByproductCasesMatchOrderedVm() {
        var random = new Random(0x218DA6L);
        for (int iteration = 0; iteration < 150; iteration++) {
            int depth = 3 + random.nextInt(5);
            var recipes = dag(depth);
            for (int i = 0; i < depth; i++) {
                var pattern = recipes.get(key(i)).get(0);
                long count = 1 + random.nextInt(3);
                var output = new GenericStack(key(i), count);
                var extra = new GenericStack(key(random.nextBoolean() ? 1000 + i : depth), 1);
                when(pattern.getPrimaryOutput()).thenReturn(output);
                when(pattern.getOutputs()).thenReturn(random.nextInt(3) == 0 ? new GenericStack[]{output, extra} : new GenericStack[]{output});
            }
            var stock = new LinkedHashMap<AEKey, BigInteger>();
            for (int i = 1; i <= depth; i++) if (random.nextBoolean()) stock.put(key(i), BigInteger.valueOf(random.nextInt(31)));
            sameAsOrdered(recipes, BigInteger.valueOf(1 + random.nextInt(12)), stock);
        }
    }

    @Test @Timeout(30) void returnedToolsAndAlternativeParentsKeepExactTrialAccounting() {
        var recipes = dag(6);
        var first = recipes.get(key(0)).get(0);
        var second = pattern(key(0), 1, input(key(99), 1));
        recipes.put(key(0), List.of(first, second));
        for (var order : List.of(ONE, BigInteger.TWO, BigInteger.valueOf(100), ONE.shiftLeft(63))) {
            sameAsOrdered(recipes, order, Map.of(key(6), BigInteger.valueOf(13), key(99), order));
        }
        var returning = input(key(99), 1);
        when(returning.exactKey()).thenReturn(null);
        when(returning.getRemainingKey(key(99))).thenReturn(key(99));
        recipes.put(key(0), List.of(pattern(key(0), 1, input(key(1), 1), returning)));
        for (var order : List.of(ONE, BigInteger.valueOf(100), ONE.shiftLeft(63)))
            sameAsOrdered(recipes, order, Map.of(key(6), order.multiply(BigInteger.valueOf(100)), key(99), ONE));
    }

    @Test @Timeout(30) void stockOnlyBoundaryMayExpandAfterEarlierAggregatedIterations() {
        var recipes = dag(5);
        var first = recipes.get(key(0)).get(0);
        recipes.put(key(0), List.of(first, pattern(key(0), 1, input(key(98), 1))));
        for (var order : List.of(BigInteger.TWO, BigInteger.valueOf(7), BigInteger.valueOf(20)))
            for (long mid = 0; mid < 5; mid++) sameAsOrdered(recipes, order,
                    Map.of(key(1), BigInteger.valueOf(mid), key(2), BigInteger.valueOf(7 - mid), key(5), BigInteger.valueOf(1000)));
    }

    @Test @Timeout(30) void repeatedPatternVisitsInspectImmutableRemainderMetadataOnce() {
        var recipes = dag(6);
        try (var scope = PatternCompiler.openScope()) {
            var vm = executor(recipes, ONE, Map.of(), false);
            var plan = vm.execute(ONE, false);
            assertDag(plan, recipes, ONE, ZERO);
            for (var patterns : recipes.values()) {
                var pattern = patterns.get(0);
                for (var input : pattern.getInputs()) {
                    var key = input.getPossibleInputs()[0].what();
                    verify(input, times(1)).getRemainingKey(key);
                }
            }
        }
    }

    @Test @Timeout(30) void multipleIndustrialRoutesWithMissingStockStayBounded() {
        var recipes = dag(8);
        for (int i = 0; i < 8; i++) {
            var key = key(i);
            recipes.put(key, List.of(recipes.get(key).get(0),
                    pattern(key, 4, input(key(1000 + i), 1))));
        }
        var amount = BigInteger.valueOf(50);
        VmCraftingPlan expected;
        try (var scope = PatternCompiler.openScope()) {
            expected = executor(recipes, amount, Map.of(), false).execute(amount, false);
        }
        try (var scope = PatternCompiler.openScope()) {
            var vm = executor(recipes, amount, Map.of(), true);
            var actual = vm.execute(amount, false);
            assertEquals(expected.patternTimes(), actual.patternTimes());
            assertEquals(expected.missingItems().quantities(), actual.missingItems().quantities());
            assertEquals(expected.bytes(), actual.bytes());
            assertTrue(vm.work() < 8192, "Shortage with alternative routes must not expand per output unit");
        }
    }

    @Test @Timeout(30) void bulkAlternativeKeepsOrderedStockAndByproductAccounting() {
        var recipes = dag(4);
        var first = recipes.get(key(0)).get(0);
        var primary = first.getPrimaryOutput();
        when(first.getOutputs()).thenReturn(new GenericStack[]{primary, new GenericStack(key(80), 2)});
        recipes.put(key(0), List.of(first, pattern(key(0), 1, input(key(99), 1))));
        for (var order : List.of(BigInteger.valueOf(2), BigInteger.valueOf(100))) {
            for (var stock : List.of(
                    Map.<AEKey, BigInteger>of(key(4), order.multiply(BigInteger.TEN)),
                    Map.<AEKey, BigInteger>of(key(4), order, key(99), order),
                    Map.<AEKey, BigInteger>of())) {
                sameAsOrdered(recipes, order, stock);
            }
        }
        try (var scope = PatternCompiler.openScope()) {
            var order = BigInteger.valueOf(100);
            var vm = executor(recipes, order, Map.of(key(4), order.multiply(BigInteger.TEN)), true);
            var plan = vm.execute(order, false);
            assertFalse(plan.simulation());
            assertEquals(order, plan.patternTimes().get(first));
            assertTrue(vm.aggregatedBlocks() > 0);
            assertTrue(vm.branchTrials() < 20, "A feasible alternative must not be tried once per item");
            assertTrue(vm.work() < 12, "A feasible shared graph must not be revisited for every branch");
        }
    }

    @Test @Timeout(30) void oneItemAlternativeUsesExactDemandInsteadOfTrialReplay() {
        var raw = key(99);
        var first = pattern(key(0), 1, input(raw, 1));
        var second = pattern(key(0), 1, input(key(98), 1));
        var recipes = Map.<AEKey, List<IPatternDetails>>of(key(0), List.of(first, second));
        var stock = Map.<AEKey, BigInteger>of(raw, ONE);
        sameAsOrdered(recipes, ONE, stock);
        try (var scope = PatternCompiler.openScope()) {
            var vm = executor(recipes, ONE, stock, true);
            var result = vm.execute(ONE, false);
            assertFalse(result.simulation());
            assertEquals(ONE, result.patternTimes().get(first));
            assertEquals(ONE, result.usedItems().get(raw));
            assertEquals(1L, vm.aggregatedBlocks(), "A one-item order should use the exact program too");
            assertEquals(0L, vm.branchTrials(), "The first fully funded route needs no trial");
            assertNull(PatternCompiler.getCompiled(first), "Aggregated demand needs no VM bytecode");
        }
    }

    @Test @Timeout(30) void unfundedFirstRouteSelectsSecondExactRouteWithoutTrialReplay() {
        var first = pattern(key(0), 1, input(key(98), 1));
        var second = pattern(key(0), 1, input(key(99), 1));
        var recipes = Map.<AEKey, List<IPatternDetails>>of(key(0), List.of(first, second));
        var stock = Map.<AEKey, BigInteger>of(key(99), BigInteger.valueOf(7));
        sameAsOrdered(recipes, BigInteger.valueOf(7), stock);
        try (var scope = PatternCompiler.openScope()) {
            var vm = executor(recipes, BigInteger.valueOf(7), stock, true);
            var plan = vm.execute(BigInteger.valueOf(7), false);
            assertFalse(plan.simulation());
            assertEquals(Map.of(second, BigInteger.valueOf(7)), plan.patternTimes());
            assertEquals(1, vm.work());
            assertEquals(0, vm.branchTrials());
        }
    }

    @Test @Timeout(30) void allUnfundedExactRoutesReportFirstRoutesMissingInputs() {
        var first = pattern(key(0), 1, input(key(98), 1));
        var second = pattern(key(0), 1, input(key(99), 1));
        var recipes = Map.<AEKey, List<IPatternDetails>>of(key(0), List.of(first, second));
        sameAsOrdered(recipes, BigInteger.valueOf(7), Map.of());
        try (var scope = PatternCompiler.openScope()) {
            var vm = executor(recipes, BigInteger.valueOf(7), Map.of(), true);
            var plan = vm.execute(BigInteger.valueOf(7), false);
            assertTrue(plan.simulation());
            assertEquals(Map.of(key(98), BigInteger.valueOf(7)), plan.missingItems().quantities());
            assertEquals(Map.of(first, BigInteger.valueOf(7)), plan.patternTimes());
            assertEquals(1, vm.work());
            assertEquals(0, vm.branchTrials());
        }
    }

    @Test void unusedAlternativeDoesNotCompileItsReturnRules() {
        var raw = key(99);
        var unusedInput = input(key(98), 1);
        var first = pattern(key(0), 1, input(raw, 1));
        var second = pattern(key(0), 1, unusedInput);
        var recipes = Map.<AEKey, List<IPatternDetails>>of(key(0), List.of(first, second));
        try (var scope = PatternCompiler.openScope()) {
            var vm = executor(recipes, ONE, Map.of(raw, ONE), true);
            var result = vm.execute(ONE, false);
            assertEquals(ONE, result.patternTimes().get(first));
            assertFalse(result.patternTimes().containsKey(second));
            verify(unusedInput, never()).getRemainingKey(any());
        }
    }

    @Test @Timeout(30) void bulkAlternativeNeverNarrowsWideOrder() {
        var recipes = dag(4);
        var first = recipes.get(key(0)).get(0);
        recipes.put(key(0), List.of(first, pattern(key(0), 1, input(key(99), 1))));
        var order = BigInteger.ONE.shiftLeft(70);
        try (var scope = PatternCompiler.openScope()) {
            var vm = executor(recipes, order, Map.of(key(4), order.multiply(BigInteger.TEN)), true);
            var plan = vm.execute(order, false);
            assertFalse(plan.simulation());
            assertEquals(order, plan.patternTimes().get(first));
            assertEquals(BigInteger.ZERO, plan.missingItems().get(key(4)));
            assertTrue(vm.branchTrials() < 20);
        }
    }

    @Test @Timeout(30) void partialStockUsesFirstRouteOnceThenHandsRemainingDemandToNextRoute() {
        var recipes = dag(4);
        var first = recipes.get(key(0)).get(0);
        var second = pattern(key(0), 1, input(key(99), 1));
        recipes.put(key(0), List.of(first, second));
        for (var order : List.of(BigInteger.valueOf(100), BigInteger.ONE.shiftLeft(70))) {
            var stock = Map.<AEKey, BigInteger>of(key(4), order.multiply(BigInteger.TWO), key(99), order);
            if (order.bitLength() < 63) sameAsOrdered(recipes, order, stock);
            try (var scope = PatternCompiler.openScope()) {
                var vm = executor(recipes, order, stock, true);
                var plan = vm.execute(order, false);
                assertFalse(plan.simulation());
                var firstCount = order.multiply(BigInteger.TWO).divide(BigInteger.valueOf(5));
                assertEquals(firstCount, plan.patternTimes().get(first));
                assertEquals(order.subtract(firstCount), plan.patternTimes().get(second));
                assertEquals(firstCount.multiply(BigInteger.valueOf(5)), plan.usedItems().get(key(4)));
                assertEquals(order.subtract(firstCount), plan.usedItems().get(key(99)));
                assertTrue(vm.branchTrials() < 30, "Partial stock must be grouped, not retried for every item");
            }
        }
    }

    @Test @Timeout(30) void sharedByproductSuppliesBothInputsWithoutDoubleCrafting() {
        var raw = key(3);
        var root = pattern(key(0), 1, input(key(1), 1), input(key(2), 1));
        var paired = pattern(key(1), 1, input(raw, 1));
        when(paired.getOutputs()).thenReturn(new GenericStack[]{
                new GenericStack(key(1), 1), new GenericStack(key(2), 1)});
        var recipes = new LinkedHashMap<AEKey, List<IPatternDetails>>();
        recipes.put(key(0), List.of(root, pattern(key(0), 1, input(key(99), 1))));
        recipes.put(key(1), List.of(paired));
        var order = BigInteger.valueOf(100);
        var stock = Map.<AEKey, BigInteger>of(raw, order, key(99), order);
        sameAsOrdered(recipes, order, stock);
        try (var scope = PatternCompiler.openScope()) {
            var plan = executor(recipes, order, stock, true).execute(order, false);
            assertFalse(plan.simulation());
            assertEquals(order, plan.patternTimes().get(root));
            assertEquals(order, plan.patternTimes().get(paired));
            assertEquals(order, plan.usedItems().get(raw));
            assertEquals(ZERO, plan.missingItems().get(key(2)));
        }
    }

    @Test void unusedOrUnpreparedInputsDoNotCauseSpeculativeObservations() {
        var recipes = dag(1);
        var input = (PatternCompiler.DetachedInput) recipes.get(key(0)).get(0).getInputs()[0];
        when(input.isCandidatePrepared(any())).thenReturn(false);
        var known = new HashMap<AEKey, List<IPatternDetails>>();
        known.put(key(0), recipes.get(key(0)));
        var compiler = new VmDemandProgram.Compiler(known);
        clearInvocations(input);
        assertNull(compiler.get(recipes.get(key(0)).get(0)));
        verify(input, never()).exactKey();
        verify(input, never()).isValid(any(), any());
        verify(input, never()).getRemainingKey(any());
    }

    @Test void unrelatedDiscoveryRefreshesSharedDemandGenerationsWithoutRecompiling() {
        var root = pattern(key(0), 1, input(key(1), 1));
        var child = pattern(key(1), 1, input(key(2), 1));
        var known = new HashMap<AEKey, List<IPatternDetails>>();
        known.put(key(0), List.of(root));
        known.put(key(1), List.of(child));
        var compiler = new VmDemandProgram.Compiler(known);
        var first = compiler.get(root);
        var firstChild = first.children.get(0);
        assertTrue(first.leaves.get(key(2)).stockOnly());

        for (int i = 99; i < 199; i++) {
            known.put(key(i), List.of(pattern(key(i), 1)));
            assertSame(first, compiler.get(root));
        }
        assertEquals(known.size(), first.generation);
        assertSame(firstChild, first.children.get(0));
        assertEquals(known.size(), firstChild.generation);

        known.put(key(2), List.of());
        var resolved = compiler.get(root);
        assertNotSame(first, resolved, "Confirmed absence permits missing-demand aggregation");
        assertFalse(resolved.leaves.get(key(2)).stockOnly());

        var withProducer = new HashMap<AEKey, List<IPatternDetails>>();
        withProducer.put(key(0), List.of(root));
        withProducer.put(key(1), List.of(child));
        var otherCompiler = new VmDemandProgram.Compiler(withProducer);
        var beforeProducer = otherCompiler.get(root);
        withProducer.put(key(2), List.of(pattern(key(2), 1, input(key(3), 1))));
        var expanded = otherCompiler.get(root);
        assertNotSame(beforeProducer, expanded);
        assertTrue(expanded.leaves.containsKey(key(3)));
    }

    @Test void compiledCycleDoesNotBypassAncestorSelection() {
        var recipes = dag(4);
        recipes.put(key(4), List.of(pattern(key(4), 1, input(key(2), 1))));
        for (var stock : List.of(Map.<AEKey, BigInteger>of(), Map.of(key(2), BigInteger.TEN)))
            sameAsOrdered(recipes, BigInteger.valueOf(4), stock);
    }

    @Test void childByproductOfAncestorCannotBecomeAnAcyclicFastPlan() {
        var root = pattern(key(0), 1, input(key(1), 1));
        var child = pattern(key(1), 1, input(key(2), 1));
        when(child.getOutputs()).thenReturn(new GenericStack[]{
                new GenericStack(key(1), 1), new GenericStack(key(0), 1)});
        var recipes = Map.<AEKey, List<IPatternDetails>>of(key(0), List.of(root), key(1), List.of(child));
        sameAsOrdered(recipes, ONE, Map.of(key(2), ONE));
        try (var scope = PatternCompiler.openScope()) {
            var plan = executor(recipes, ONE, Map.of(key(2), ONE), true).execute(ONE, false);
            assertTrue(plan.simulation());
            assertEquals(ONE, plan.missingItems().get(key(1)));
            assertFalse(plan.patternTimes().containsKey(child));
        }
    }

    @Test void exactFluidUnitsAndFractionalBytesMatchOrderedVm() {
        var fluid = appeng.api.stacks.AEFluidKey.of(net.minecraft.world.level.material.Fluids.WATER);
        var recipes = dag(5);
        var input = input(fluid, 3);
        when(input.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(fluid, 81)});
        recipes.put(key(5), List.of(pattern(key(5), 1, input)));
        for (var order : List.of(ONE, BigInteger.TWO, ONE.shiftLeft(63)))
            for (var available : List.of(ZERO, BigInteger.valueOf(80), BigInteger.valueOf(82), order.multiply(BigInteger.valueOf(10000))))
                sameAsOrdered(recipes, order, Map.of(fluid, available));
    }

    @Test void aggregationCompilationHonorsCancellation() {
        var recipes = dag(1);
        try (var scope = PatternCompiler.openScope()) {
            var vm = executor(recipes, ONE, Map.of(), true);
            Thread.currentThread().interrupt();
            assertThrows(java.util.concurrent.CancellationException.class, () -> vm.execute(ONE, false));
        } finally { Thread.interrupted(); }
    }

    @Test @Timeout(30) void oneItemOrderCanHaveWideIntermediateAndNodeCosts() {
        var recipes = dag(96);
        try (var scope = PatternCompiler.openScope()) {
            var vm = executor(recipes, ONE, Map.of(), true);
            var result = vm.execute(ONE, false);
            assertDag(result, recipes, ONE, ZERO);
            assertTrue(result.missingItems().get(key(96)).bitLength() > 63);
            assertTrue(result.bytes().bitLength() > 63);
            assertTrue(vm.work() < 1024);
        }
    }

    @Test @Timeout(30) void identicalSharedDagCompletesWithLessWorkThanPerPathExecution() {
        var recipes = dag(16);
        VmCraftingPlan expected;
        long before;
        try (var scope = PatternCompiler.openScope()) {
            var old = executor(recipes, ONE, Map.of(), false);
            expected = old.execute(ONE, false); before = old.work();
        }
        try (var scope = PatternCompiler.openScope()) {
            var vm = executor(recipes, ONE, Map.of(), true);
            var actual = vm.execute(ONE, false);
            assertEquals(expected.patternTimes(), actual.patternTimes());
            assertEquals(expected.usedItems().quantities(), actual.usedItems().quantities());
            assertEquals(expected.missingItems().quantities(), actual.missingItems().quantities());
            assertEquals(expected.emittedItems().quantities(), actual.emittedItems().quantities());
            assertEquals(expected.bytes(), actual.bytes());
            assertTrue(vm.work() * 32 < before);
            System.out.println("Issue218 same graph: beforeWork=" + before + " afterWork=" + vm.work());
        }
    }

    @Test @Timeout(30) void aggregationMatchesOrderedAccountingWithIntermediateStockAndRounding() {
        var recipes = dag(7);
        var shared = recipes.get(key(5)).get(0);
        when(shared.getOutputs()).thenReturn(new GenericStack[]{new GenericStack(key(5), 3)});
        when(shared.getPrimaryOutput()).thenReturn(new GenericStack(key(5), 3));
        for (var order : List.of(ONE, BigInteger.TWO, BigInteger.valueOf(9), ONE.shiftLeft(63))) {
            for (var stock : List.of(Map.<AEKey, BigInteger>of(), Map.of(key(7), order.multiply(BigInteger.valueOf(10))),
                    Map.of(key(3), order.multiply(BigInteger.valueOf(3)), key(7), order.multiply(BigInteger.valueOf(20))))) {
                sameAsOrdered(recipes, order, stock);
            }
        }
    }

    private static void sameAsOrdered(Map<AEKey, List<IPatternDetails>> recipes, BigInteger order, Map<AEKey, BigInteger> stock) {
        VmCraftingPlan expected;
        try (var scope = PatternCompiler.openScope()) { expected = executor(recipes, order, stock, false).execute(order, false); }
        try (var scope = PatternCompiler.openScope()) {
            var actual = executor(recipes, order, stock, true).execute(order, false);
            assertEquals(expected.finalOutput(), actual.finalOutput());
            assertEquals(expected.simulation(), actual.simulation());
            assertEquals(expected.multiplePaths(), actual.multiplePaths());
            assertEquals(expected.patternTimes(), actual.patternTimes());
            assertEquals(expected.usedItems().quantities(), actual.usedItems().quantities());
            assertEquals(expected.missingItems().quantities(), actual.missingItems().quantities());
            assertEquals(expected.emittedItems().quantities(), actual.emittedItems().quantities());
            if (!expected.bytes().equals(actual.bytes())) {
                System.out.println("Capacity mismatch order=" + order);
                stock.forEach((k, v) -> System.out.println("stock " + ((AEItemKey) k).getTag() + "=" + v));
                recipes.forEach((k, list) -> {
                    System.out.println("recipes " + ((AEItemKey) k).getTag());
                    for (var p : list) {
                        for (var out : p.getOutputs()) System.out.println("out " + ((AEItemKey) out.what()).getTag() + "=" + out.amount());
                        for (var in : p.getInputs()) System.out.println("in " + ((AEItemKey) in.getPossibleInputs()[0].what()).getTag()
                                + "=" + in.getPossibleInputs()[0].amount() * in.getMultiplier());
                    }
                });
            }
            assertEquals(expected.bytes(), actual.bytes());
        }
    }

    private static VmInstructionExecution executor(Map<AEKey, List<IPatternDetails>> recipes, BigInteger amount,
            Map<AEKey, BigInteger> stock, boolean optimized) {
        return new VmInstructionExecution(PatternCompiler.compileRequest(key(0), amount), new VmSimulationState(stock),
                key -> recipes.getOrDefault(key, List.of()), key -> false,
                work -> assertTrue(work < 8192, "Per-path recipe expansion exceeded the test work budget"), true, optimized);
    }

    private static void assertDag(VmCraftingPlan plan, Map<AEKey, List<IPatternDetails>> recipes,
            BigInteger order, BigInteger available) {
        int depth = recipes.size();
        var demand = requirements(depth, order);
        var used = available.min(demand.get(depth));
        assertEquals(order, plan.finalOutput().amount());
        assertEquals(used, plan.usedItems().get(key(depth)));
        assertEquals(demand.get(depth).subtract(used), plan.missingItems().get(key(depth)));
        assertEquals(used.compareTo(demand.get(depth)) < 0, plan.simulation());
        assertTrue(plan.emittedItems().isEmpty());
        assertFalse(plan.multiplePaths());
        BigInteger calls = ZERO, crafts = ZERO;
        for (int i = 0; i <= depth; i++) {
            calls = calls.add(demand.get(i));
            if (i < depth) {
                crafts = crafts.add(demand.get(i));
                assertEquals(demand.get(i), plan.patternTimes().get(recipes.get(key(i)).get(0)));
            }
        }
        var treeNodes = new ArrayList<BigInteger>(Collections.nCopies(depth + 1, ONE));
        for (int i = depth - 1; i >= 0; i--) treeNodes.set(i, ONE.add(treeNodes.get(i + 1))
                .add(i + 2 <= depth ? treeNodes.get(i + 2) : ZERO));
        var stackBytes = new VmByteCost(calls.multiply(BigInteger.valueOf(8)), BigInteger.valueOf(key(0).getType().getAmountPerByte()));
        var bytes = stackBytes.add(new VmByteCost(crafts.add(treeNodes.get(0).multiply(BigInteger.valueOf(8))), ONE)).ceiling();
        assertEquals(bytes, plan.bytes(), "AE2 conceptual node/stack/pattern bytes must not be narrowed or omitted");
    }

    private static List<BigInteger> requirements(int depth, BigInteger order) {
        var result = new ArrayList<BigInteger>(Collections.nCopies(depth + 1, ZERO));
        result.set(0, order);
        for (int i = 0; i < depth; i++) {
            result.set(i + 1, result.get(i + 1).add(result.get(i)));
            if (i + 2 <= depth) result.set(i + 2, result.get(i + 2).add(result.get(i)));
        }
        return result;
    }

    private static Map<AEKey, List<IPatternDetails>> dag(int depth) {
        var recipes = new LinkedHashMap<AEKey, List<IPatternDetails>>();
        for (int i = 0; i < depth; i++) {
            var inputs = new ArrayList<IPatternDetails.IInput>();
            inputs.add(input(key(i + 1), 1));
            if (i + 2 <= depth) inputs.add(input(key(i + 2), 1));
            recipes.put(key(i), List.of(pattern(key(i), 1, inputs.toArray(IPatternDetails.IInput[]::new))));
        }
        return recipes;
    }

    private static IPatternDetails pattern(AEKey key, long count, IPatternDetails.IInput... inputs) {
        var pattern = mock(IPatternDetails.class);
        when(pattern.getInputs()).thenReturn(inputs);
        var output = new GenericStack(key, count);
        when(pattern.getOutputs()).thenReturn(new GenericStack[]{output});
        when(pattern.getPrimaryOutput()).thenReturn(output);
        return pattern;
    }
    private static PatternCompiler.DetachedInput input(AEKey key, long multiplier) {
        var input = mock(PatternCompiler.DetachedInput.class);
        when(input.craftedKey()).thenReturn(key);
        when(input.exactKey()).thenReturn(key);
        when(input.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(key, 1)});
        when(input.getMultiplier()).thenReturn(multiplier);
        when(input.exactMultiplier()).thenCallRealMethod();
        when(input.isCandidatePrepared(any())).thenReturn(true);
        when(input.isValid(any(), any())).thenAnswer(call -> key.equals(call.getArgument(0)));
        return input;
    }
    private static AEKey key(int i) {
        var stack = new ItemStack(Items.IRON_INGOT);
        stack.getOrCreateTag().putInt("demand_stage", i);
        return AEItemKey.of(stack);
    }
}
