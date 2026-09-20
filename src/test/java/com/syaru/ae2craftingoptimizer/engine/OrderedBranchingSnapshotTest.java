package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.*;
import static com.syaru.ae2craftingoptimizer.engine.OrderedBranchingPlannerTest.*;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OrderedBranchingSnapshotTest {
    @BeforeAll
    static void bootstrap() throws Exception { ReusableByproductAe2OracleTest.bootstrap(); }

    @Test
    void productionSnapshotUsesOutputSpecificPriorityNotGlobalDiscoveryOrder() throws Exception {
        AEKey out = AEItemKey.of(Items.DIAMOND), byproduct = AEItemKey.of(Items.GOLD_INGOT),
                raw = AEItemKey.of(Items.COBBLESTONE), other = AEItemKey.of(Items.IRON_INGOT);
        var first = recipe("first", List.of(slot(raw, 2)), Map.of(out, 1L, byproduct, 1L));
        var second = recipe("second", List.of(slot(other, 3)), Map.of(out, 1L, byproduct, 2L));
        var order = new LinkedHashMap<AEKey, List<CompiledPattern<AEKey>>>();
        var priority = new ArrayList<>(List.of(second, first));
        order.put(out, priority);
        order.put(byproduct, List.of(first, second));
        var snapshot = snapshot(List.of(first, second), order, Set.of(), Map.of());
        priority.clear();
        order.clear();
        assertEquals(List.of(second, first), snapshot.orderedPatternsFor(out));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.orderedPatternsFor(out).clear());
        assertEquals(RootProgramFailure.MULTIPLE_PRODUCERS, snapshot.rootProgramOutcome(out).failure());
        var result = evaluate(snapshot, out, Map.of(raw, 20L, other, 9L));
        assertEquals(Map.of("second", BigInteger.valueOf(3), "first", BigInteger.valueOf(2)),
                result.plan().patternExecutions());
        assertEquals(Map.of(other, BigInteger.valueOf(9), raw, BigInteger.valueOf(4)),
                result.plan().usedInventory());
        assertEquals(Map.of("first", BigInteger.valueOf(5)),
                evaluate(snapshot, byproduct, Map.of(raw, 20L, other, 9L)).plan().patternExecutions());
    }

    @Test
    void fullyStockedInputDoesNotNeedItsUnsupportedProducerButMissingInputDoes() throws Exception {
        AEKey out = AEItemKey.of(Items.DIAMOND), raw = AEItemKey.of(Items.COBBLESTONE);
        var root = recipe("root", List.of(slot(raw, 1)), Map.of(out, 1L));
        var snapshot = snapshot(List.of(root), Map.of(), Set.of(raw), Map.of(raw, 1));
        assertTrue(evaluate(snapshot, out, Map.of(raw, 5L)).plan().craftable());
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> evaluate(snapshot, out, Map.of(raw, 4L)));
        assertTrue(error.getMessage().contains("minecraft:cobblestone"));
        assertTrue(error.getMessage().contains("registered=1, captured=0"));
    }

    @Test
    void staticInheritedProcessingContractIsAcceptedButOverrideIsNot() {
        assertTrue(Ae2CompiledPatternFactory.inheritsExactProcessingInputs(AEProcessingPattern.class));
        assertTrue(Ae2CompiledPatternFactory.inheritsExactProcessingInputs(InheritedPattern.class));
        assertFalse(Ae2CompiledPatternFactory.inheritsExactProcessingInputs(ChangedOutputs.class));
        assertFalse(Ae2CompiledPatternFactory.inheritsExactProcessingInputs(ChangedInputs.class));
        assertFalse(Ae2CompiledPatternFactory.inheritsExactProcessingInputs(String.class));
    }

    @Test
    void missingBranchingSimulationRetainsExactCountsAndPathFlag() {
        AEKey output = AEItemKey.of(Items.DIAMOND), raw = AEItemKey.of(Items.COBBLESTONE);
        BigInteger wide = BigInteger.TEN.pow(64);
        var exact = new BigCraftingPlan<>(output, BigInteger.TEN, Map.of("root", wide),
                Map.of(), Map.of(), Map.of(raw, wide), 1);
        var simulation = new BigIntegerSimulationPlan(new GenericStack(output, 10), exact,
                Map.of(), wide.multiply(BigInteger.TEN), 4096, true);
        assertTrue(simulation.simulation());
        assertTrue(simulation.multiplePaths());
        assertEquals(wide, simulation.exactPlan().missing().get(raw));
        assertEquals(wide.multiply(BigInteger.TEN), simulation.exactBytes());
        assertFalse(simulation.exactPlan().craftable());
    }

    private static OrderedBranchingPlanner.Result<AEKey> evaluate(Ae2PlanningGraphSnapshot snapshot,
            AEKey output, Map<AEKey, Long> stock) {
        return Ae2AuthoritativeCraftingPlanner.evaluateBranching(snapshot, output, 5,
                CalculationStrategy.REPORT_MISSING_ITEMS,
                key -> BigInteger.valueOf(stock.getOrDefault(key, 0L)), PlanningGuard.none(), 4096);
    }

    private static Ae2PlanningGraphSnapshot snapshot(List<CompiledPattern<AEKey>> patterns,
            Map<AEKey, List<CompiledPattern<AEKey>>> order, Set<AEKey> incomplete,
            Map<AEKey, Integer> additionalCounts) throws Exception {
        var graph = CompiledCraftingGraph.compile(1, patterns);
        Map<AEKey, Integer> counts = new HashMap<>(additionalCounts);
        patterns.forEach(p -> p.outputs().keySet().forEach(k -> counts.merge(k, 1, Integer::sum)));
        var type = Class.forName(Ae2ImmutablePlanningGraphCache.class.getName() + "$Snapshot");
        var constructor = type.getDeclaredConstructor(CompiledCraftingGraph.class, IdentityHashMap.class,
                Map.class, Map.class, Set.class, Set.class, Set.class, long.class, Map.class);
        constructor.setAccessible(true);
        return (Ae2PlanningGraphSnapshot) constructor.newInstance(graph, new IdentityHashMap<>(), Map.of(),
                counts, incomplete, Set.of(), patterns.stream().map(CompiledPattern::id)
                        .collect(java.util.stream.Collectors.toSet()), 1L, order);
    }

    private static class InheritedPattern extends AEProcessingPattern {
        InheritedPattern(AEItemKey key) { super(key); }
    }

    private static class ChangedOutputs extends InheritedPattern {
        ChangedOutputs(AEItemKey key) { super(key); }
        @Override public GenericStack[] getOutputs() { throw new AssertionError("must not be called"); }
    }

    private static class ChangedInputs extends InheritedPattern {
        ChangedInputs(AEItemKey key) { super(key); }
        @Override public IInput[] getInputs() { throw new AssertionError("must not be called"); }
    }
}
