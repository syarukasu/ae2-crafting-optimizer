package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.*;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.stacks.*;
import java.math.BigInteger;
import java.time.Duration;
import java.util.*;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OrderedBranchingPlannerTest {
    @BeforeAll
    static void bootstrap() throws Exception { ReusableByproductAe2OracleTest.bootstrap(); }

    @Test
    void preservesProducerPriorityPartialStockRollbackAndMissingAgainstActualAe2() throws Exception {
        AEKey out = AEItemKey.of(Items.DIAMOND), a = AEItemKey.of(Items.IRON_INGOT),
                b = AEItemKey.of(Items.GOLD_INGOT), raw = AEItemKey.of(Items.COBBLESTONE);
        Random random = new Random(19054);
        for (int i = 0; i < 300; i++) {
            var patterns = List.of(recipe("first", List.of(slot(a, 2), slot(b, 1)), Map.of(out, 2L)),
                    recipe("second", List.of(slot(raw, 3)), Map.of(out, 3L, b, 1L)),
                    recipe("a", List.of(slot(raw, 2)), Map.of(a, 3L)));
            var stock = Map.of(a, (long) random.nextInt(8), b, (long) random.nextInt(9),
                    raw, (long) random.nextInt(60));
            compare(patterns, out, 1 + random.nextInt(40), stock, Set.of(), i % 2 == 0);
        }
    }

    @Test
    void recursionUsesAncestorsAndEmittedLeavesNotGlobalCycles() throws Exception {
        AEKey out = AEItemKey.of(Items.DIAMOND), a = AEItemKey.of(Items.IRON_INGOT),
                raw = AEItemKey.of(Items.COBBLESTONE);
        var patterns = List.of(recipe("root", List.of(slot(a, 2)), Map.of(out, 1L)),
                recipe("recycle", List.of(slot(out, 1)), Map.of(a, 2L)),
                recipe("ore", List.of(slot(raw, 1)), Map.of(a, 3L)));
        compare(patterns, out, 15, Map.of(raw, 30L), Set.of(), false);
        compare(patterns, out, 15, Map.of(raw, 30L), Set.of(a), false);
    }

    @Test
    void variedBranchingNetworksMatchActualAe2() throws Exception {
        List<AEKey> keys = List.of(AEItemKey.of(Items.DIAMOND), AEItemKey.of(Items.IRON_INGOT),
                AEItemKey.of(Items.GOLD_INGOT), AEItemKey.of(Items.REDSTONE), AEItemKey.of(Items.COBBLESTONE),
                AEItemKey.of(Items.COAL), AEFluidKey.of(net.minecraft.world.level.material.Fluids.WATER));
        Random random = new Random(19020260917L);
        for (int sample = 0; sample < 400; sample++) {
            List<CompiledPattern<AEKey>> patterns = new ArrayList<>();
            for (int node = 0; node < 4; node++) {
                int producers = 1 + random.nextInt(3);
                for (int p = 0; p < producers; p++) {
                    List<CompiledPattern.InputSlot<AEKey>> inputs = new ArrayList<>();
                    for (int slot = 0, count = 1 + random.nextInt(3); slot < count; slot++) {
                        int key = node + 1 + random.nextInt(keys.size() - node - 1);
                        long quantum = key == 6 ? 1000 : 1;
                        inputs.add(new CompiledPattern.InputSlot<>(List.of(new CompiledPattern.Stack<>(
                                keys.get(key), quantum * (1 + random.nextInt(3)))), quantum));
                    }
                    Map<AEKey, Long> outputs = new LinkedHashMap<>();
                    outputs.put(keys.get(node), 1L + random.nextInt(4));
                    if (random.nextBoolean()) {
                        int coproduct = node + 1 + random.nextInt(5 - node);
                        outputs.put(keys.get(coproduct), 1L + random.nextInt(3));
                    }
                    patterns.add(recipe(node + "-" + p, inputs, outputs));
                }
            }
            Map<AEKey, Long> stock = new HashMap<>();
            keys.forEach(key -> stock.put(key, (long) random.nextInt(30) * (key instanceof AEFluidKey ? 250 : 1)));
            compare(patterns, keys.get(0), 1 + random.nextInt(18), stock,
                    sample % 7 == 0 ? Set.of(keys.get(5)) : Set.of(), sample % 2 == 0);
        }
    }

    @Test
    void preservesFluidTemplatesAndNbtIdentity() throws Exception {
        var first = new net.minecraft.world.item.ItemStack(Items.PAPER);
        var firstTag = new net.minecraft.nbt.CompoundTag();
        firstTag.putString("variant", "first");
        first.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(firstTag));
        var second = first.copy();
        var secondTag = new net.minecraft.nbt.CompoundTag();
        secondTag.putString("variant", "second");
        second.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(secondTag));
        AEKey a = AEItemKey.of(first), b = AEItemKey.of(second), out = AEItemKey.of(Items.DIAMOND),
                water = AEFluidKey.of(net.minecraft.world.level.material.Fluids.WATER);
        var patterns = List.of(recipe("first", List.of(slot(a, 2),
                        new CompiledPattern.InputSlot<>(List.of(new CompiledPattern.Stack<>(water, 2000)), 1000)),
                        Map.of(out, 2L)),
                recipe("second", List.of(slot(b, 1)), Map.of(out, 1L, water, 1500L)));
        for (long fluid : new long[] {0, 750, 2250, 9500}) {
            compare(patterns, out, 17, Map.of(a, 40L, b, 60L, water, fluid), Set.of(), false);
        }
    }

    @Test
    void acceleratesWideOrdersWithoutSelectingAnArbitraryProducer() {
        BigInteger count = BigInteger.TEN.pow(64);
        var patterns = List.of(recipe("first", List.of(slot("a", 2)), Map.of("out", 1L)),
                recipe("second", List.of(slot("b", 3)), Map.of("out", 1L)));
        var graph = CompiledCraftingGraph.compile(1, patterns);
        var stock = Map.of("a", count, "b", count.multiply(BigInteger.valueOf(3)));
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            var result = new OrderedBranchingPlanner<>("out", graph::patternsFor, k -> false,
                    k -> stock.getOrDefault(k, BigInteger.ZERO), k -> 1, PlanningGuard.none(), 4096)
                    .plan(count, false);
            assertTrue(result.plan().craftable());
            assertEquals(count.divide(BigInteger.TWO), result.plan().patternExecutions().get("first"));
            assertEquals(count.divide(BigInteger.TWO), result.plan().patternExecutions().get("second"));
            assertEquals(count, result.plan().usedInventory().get("a"));
            assertEquals(count.divide(BigInteger.TWO).multiply(BigInteger.valueOf(3)),
                    result.plan().usedInventory().get("b"));
            assertTrue(result.plan().expandedRequests() < 200);
            assertTrue(result.skippedIterations().compareTo(count.divide(BigInteger.TWO)) > 0);
        });
    }

    @Test
    void periodicSurplusHasExactWideAccounting() {
        BigInteger count = BigInteger.TEN.pow(40);
        var patterns = List.of(recipe("first", List.of(slot("a", 2)), Map.of("out", 1L)),
                recipe("second", List.of(slot("unavailable", 1)), Map.of("out", 1L)),
                recipe("a", List.of(slot("raw", 1)), Map.of("a", 3L)));
        var graph = CompiledCraftingGraph.compile(1, patterns);
        var result = new OrderedBranchingPlanner<>("out", graph::patternsFor, k -> false,
                k -> k.equals("raw") ? count : BigInteger.ZERO, k -> 1, PlanningGuard.none(), 4096)
                .plan(count, false);
        assertEquals(count, result.plan().patternExecutions().get("first"));
        assertEquals(count.multiply(BigInteger.TWO).add(BigInteger.TWO).divide(BigInteger.valueOf(3)),
                result.plan().usedInventory().get("raw"));
        assertTrue(result.plan().expandedRequests() < 1000);
    }

    @Test
    void reusableCatalystIsReservedOnce() {
        BigInteger count = BigInteger.TEN.pow(64);
        var graph = CompiledCraftingGraph.compile(1, List.of(recipe("reuse",
                List.of(slot("catalyst", 1), slot("raw", 2)), Map.of("out", 1L, "catalyst", 1L))));
        var stock = Map.of("catalyst", BigInteger.ONE, "raw", count.multiply(BigInteger.TWO));
        var result = new OrderedBranchingPlanner<>("out", graph::patternsFor, k -> false,
                k -> stock.getOrDefault(k, BigInteger.ZERO), k -> 1, PlanningGuard.none(), 4096).plan(count, false);
        assertEquals(stock, result.plan().usedInventory());
        assertEquals(Map.of("reuse", count), result.plan().patternExecutions());
        assertTrue(result.plan().expandedRequests() < 200);
    }

    @Test
    void acceleratedTrialsMatchActualAe2IncludingFailedAlternativeReads() throws Exception {
        AEKey out = AEItemKey.of(Items.DIAMOND), a = AEItemKey.of(Items.IRON_INGOT),
                b = AEItemKey.of(Items.GOLD_INGOT), raw = AEItemKey.of(Items.COBBLESTONE);
        var patterns = List.of(recipe("first", List.of(slot(a, 2), slot(b, 1)), Map.of(out, 1L)),
                recipe("second", List.of(slot(raw, 3)), Map.of(out, 2L, b, 1L)),
                recipe("aFirst", List.of(slot(b, 2)), Map.of(a, 1L)),
                recipe("aSecond", List.of(slot(raw, 1)), Map.of(a, 3L, b, 1L)));
        for (long stock : new long[] {0, 250, 14000}) {
            compare(patterns, out, 20000, Map.of(raw, 80000L, a, stock, b, 4L), Set.of(), false);
        }
    }

    @Test
    void cancellationIsNotConvertedToMissingOrAnotherProducer() {
        var graph = CompiledCraftingGraph.compile(1, List.of(
                recipe("a", List.of(slot("raw", 1)), Map.of("out", 1L)),
                recipe("b", List.of(slot("other", 1)), Map.of("out", 1L))));
        assertThrows(PlanningCancelledException.class, () -> new OrderedBranchingPlanner<>("out",
                graph::patternsFor, k -> false, k -> BigInteger.TEN.pow(64), k -> 1,
                n -> { if (n > 5) throw new PlanningCancelledException(n); }, 4096)
                .plan(BigInteger.TEN.pow(60), false));
    }

    @Test
    void repeatedDoubleByteAdditionsPreserveRoundingAndExponentTransitions() {
        Random random = new Random(190754);
        for (int sample = 0; sample < 200; sample++) {
            double start = Math.scalb(random.nextDouble(), random.nextInt(80) - 40);
            double[] sequence = {random.nextInt(20) / 125.0, random.nextInt(30) / 1000.0, 1};
            int count = 10_000 + random.nextInt(20_000);
            double expected = start;
            for (int i = 0; i < count; i++) for (double amount : sequence) expected += amount;
            assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(
                    OrderedBranchingPlanner.repeatedBytes(start, sequence, BigInteger.valueOf(count))));
        }
        assertEquals(0x1.0p53, OrderedBranchingPlanner.repeatedBytes(0, new double[] {1}, BigInteger.TEN.pow(64)));
        assertEquals(Double.longBitsToDouble(20000), OrderedBranchingPlanner.repeatedBytes(0,
                new double[] {Double.MIN_VALUE}, BigInteger.valueOf(20000)));
    }

    static <K> CompiledPattern.InputSlot<K> slot(K key, long amount) {
        return new CompiledPattern.InputSlot<>(List.of(new CompiledPattern.Stack<>(key, amount)), 1);
    }

    static <K> CompiledPattern<K> recipe(String id, List<CompiledPattern.InputSlot<K>> inputs,
            Map<K, Long> outputs) { return new CompiledPattern<>(id, inputs, outputs, true); }

    private static void compare(List<CompiledPattern<AEKey>> patterns, AEKey output, long amount,
            Map<AEKey, Long> stock, Set<AEKey> emitters, boolean craftLess) throws Exception {
        var expected = ReusableByproductAe2OracleTest.oracle(patterns, output, amount, stock, emitters,
                craftLess ? CalculationStrategy.CRAFT_LESS : CalculationStrategy.REPORT_MISSING_ITEMS);
        var graph = CompiledCraftingGraph.compile(1, patterns);
        var result = new OrderedBranchingPlanner<>(output, graph::patternsFor, emitters::contains,
                k -> BigInteger.valueOf(stock.getOrDefault(k, 0L)), k -> k.getType().getAmountPerByte(),
                PlanningGuard.none(), 4096).plan(BigInteger.valueOf(amount), craftLess);
        Map<String, BigInteger> crafts = new HashMap<>();
        expected.plan().patternTimes().forEach((p, n) -> crafts.put(expected.ids().get(p), BigInteger.valueOf(n)));
        assertEquals(crafts, result.plan().patternExecutions());
        assertEquals(counts(expected.plan().usedItems()), result.plan().usedInventory());
        assertEquals(counts(expected.plan().missingItems()), result.plan().missing());
        assertEquals(counts(expected.plan().emittedItems()), result.plan().emitted());
        assertEquals(expected.plan().finalOutput().amount(), result.plan().requestedAmount().longValueExact());
        assertEquals(expected.plan().bytes(), result.ae2Bytes());
        assertEquals(expected.plan().multiplePaths(), result.multiplePaths());
    }

    private static Map<AEKey, BigInteger> counts(KeyCounter counter) {
        Map<AEKey, BigInteger> result = new HashMap<>();
        for (var entry : counter) if (entry.getLongValue() > 0) {
            result.put(entry.getKey(), BigInteger.valueOf(entry.getLongValue()));
        }
        return result;
    }
}
