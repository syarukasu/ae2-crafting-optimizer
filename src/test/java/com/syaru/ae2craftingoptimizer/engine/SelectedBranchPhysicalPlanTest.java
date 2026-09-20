package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.*;
import appeng.api.stacks.*;
import appeng.api.crafting.IPatternDetails;
import com.syaru.ae2craftingoptimizer.api.vector.*;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SelectedBranchPhysicalPlanTest {
    static AEKey RAW, OTHER, PART, OUT, EXTRA;
    @BeforeAll static void bootstrap() throws Exception {
        ReusableByproductAe2OracleTest.bootstrap();
        com.syaru.ae2craftingoptimizer.TestRegistries.initializeAe2KeyTypes();
        RAW = AEItemKey.of(Items.COBBLESTONE); OTHER = AEItemKey.of(Items.DIRT);
        PART = AEItemKey.of(Items.IRON_INGOT); OUT = AEItemKey.of(Items.DIAMOND);
        EXTRA = AEItemKey.of(Items.GOLD_INGOT);
    }

    @org.junit.jupiter.api.BeforeEach void installRegistries() {
        com.syaru.ae2craftingoptimizer.lifecycle.ACORegistryAccess.install(
                net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY));
    }
    @org.junit.jupiter.api.AfterEach void clearRegistries() {
        com.syaru.ae2craftingoptimizer.lifecycle.ACORegistryAccess.clear();
    }

    @Test void preservesBothChosenProducersAboveLongAndAllSurplus() {
        for (BigInteger n : List.of(BigInteger.ONE, BigInteger.valueOf(Long.MAX_VALUE),
                BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE), BigInteger.TEN.pow(64))) {
            var first = pattern("first", RAW, 1, Map.of(PART, 1L, EXTRA, 2L));
            var second = pattern("second", OTHER, 1, Map.of(PART, 1L));
            var root = pattern("root", PART, 1, Map.of(OUT, 1L));
            var graph = CompiledCraftingGraph.compile(1, List.of(first, second, root));
            Map<AEKey, BigInteger> inventory = Map.of(RAW, BigInteger.ONE, OTHER, n);
            var exact = new OrderedBranchingPlanner<>(OUT, graph::patternsFor, k -> false,
                    k -> inventory.getOrDefault(k, BigInteger.ZERO), k -> 1, PlanningGuard.none(), 4096)
                    .plan(n.add(BigInteger.ONE), false).plan();
            assertEquals(Map.of("first", BigInteger.ONE, "second", n, "root", n.add(BigInteger.ONE)),
                    exact.patternExecutions());
            assertEquals(inventory, exact.usedInventory());
            assertTrue(CompiledRootProgram.tryCompile(CompiledCraftingGraph.compile(1,
                    List.of(first, second, root)), OUT, k -> false).isEmpty());
            var plan = SelectedBranchPhysicalPlan.prepare(exact, List.of(root, second, first), 1, 1, 4096);
            assertEquals(exact.patternExecutions(), counts(plan));
            assertEquals(Map.of(OUT, n.add(BigInteger.ONE), EXTRA, BigInteger.TWO), replay(plan,
                    Map.of("first", first, "second", second, "root", root)));
            assertEquals(plan, PreparedVectorBatchCodec.decode(PreparedVectorBatchCodec.encode(plan)));
            UUID job = UUID.randomUUID();
            var rebound = SelectedBranchPhysicalPlan.forJob(plan, job);
            assertEquals(job, rebound.parentJobId());
            assertEquals(plan.craftingSteps(), rebound.craftingSteps());
            assertEquals(plan.programFingerprint(), rebound.programFingerprint());
        }
    }

    @Test void refusesShortageOrAnInterleavedCycleInsteadOfInventingStock() {
        var root = pattern("root", PART, 1, Map.of(OUT, 1L));
        var bad = new BigCraftingPlan<>(OUT, BigInteger.ONE, Map.of("root", BigInteger.ONE),
                Map.of(), Map.of(), Map.of(), 1);
        assertThrows(IllegalArgumentException.class,
                () -> SelectedBranchPhysicalPlan.prepare(bad, List.of(root), 1, 1, 4096));
        var loop = pattern("loop", OUT, 1, Map.of(PART, 1L));
        var cyclic = new BigCraftingPlan<>(OUT, BigInteger.ONE,
                Map.of("root", BigInteger.TWO, "loop", BigInteger.ONE),
                Map.of(PART, BigInteger.ONE), Map.of(), Map.of(), 2);
        assertThrows(IllegalArgumentException.class,
                () -> SelectedBranchPhysicalPlan.prepare(cyclic, List.of(root, loop), 1, 1, 4096));
    }

    @Test void rejectsAChangedSelectedCountAndDoesNotSelectUnusedCandidates() {
        var good = pattern("good", RAW, 1, Map.of(OUT, 1L));
        var unused = pattern("unused", OTHER, 1, Map.of(OUT, 99L));
        var exact = new BigCraftingPlan<>(OUT, BigInteger.ONE, Map.of("good", BigInteger.ONE),
                Map.of(RAW, BigInteger.ONE), Map.of(), Map.of(), 1);
        var selected = SelectedBranchPhysicalPlan.prepare(exact, List.of(unused, good), 1, 1, 4096);
        assertEquals(List.of("good"), selected.requiredPatternIds());
        var alteredTotal = new PreparedVectorBatch(selected.transactionId(), selected.parentJobId(), selected.resourceMode(),
                selected.requestedOutput(), selected.requestedAmount(), BigInteger.TWO, selected.logicalStageCount(),
                selected.totalInputs(), selected.finalOutputs(), selected.remainingOutputs(), selected.requiredPatternIds(),
                selected.craftingSteps(), selected.programFingerprint(), selected.patternGeneration(), selected.recipeGeneration());
        assertThrows(IllegalArgumentException.class, () -> SelectedBranchPhysicalPlan.forJob(alteredTotal, UUID.randomUUID()));
        var changed = new BigCraftingPlan<>(OUT, BigInteger.ONE, Map.of("good", BigInteger.TWO),
                exact.usedInventory(), Map.of(), Map.of(), 1);
        assertThrows(IllegalArgumentException.class,
                () -> SelectedBranchPhysicalPlan.validateAccounting(selected, changed));
    }

    @Test void storesSelectedBranchesInExactJobAndRejectsMissingOrCorruptMetadata() {
        BigInteger n = BigInteger.valueOf(Long.MAX_VALUE);
        var first = pattern("first", RAW, 1, Map.of(PART, 1L, EXTRA, 2L));
        var second = pattern("second", OTHER, 1, Map.of(PART, 1L));
        var root = new CompiledPattern<AEKey>("root", List.of(
                new CompiledPattern.InputSlot<>(List.of(new CompiledPattern.Stack<>(PART, Long.MAX_VALUE))),
                new CompiledPattern.InputSlot<>(List.of(new CompiledPattern.Stack<>(PART, Long.MAX_VALUE)))),
                Map.of(OUT, 1L), false);
        var exact = new BigCraftingPlan<>(OUT, BigInteger.ONE,
                Map.of("first", BigInteger.ONE, "second", n.multiply(BigInteger.TWO).subtract(BigInteger.ONE), "root", BigInteger.ONE),
                Map.of(RAW, BigInteger.ONE, OTHER, n.multiply(BigInteger.TWO).subtract(BigInteger.ONE)), Map.of(), Map.of(), 3);
        var selected = SelectedBranchPhysicalPlan.prepare(exact, List.of(first, second, root), 7, 11, 4096);
        var prepared = new Ae2BigCraftingPlanFactory.PreparedBigRootPlan(null, exact, n, 7, 11,
                Ae2BigCraftingPlanFactory.ExecutionMode.EXACT_PATTERN_EXECUTOR, 0, "test-epoch", selected.programFingerprint());
        Map<IPatternDetails, BigInteger> tasks = new LinkedHashMap<>();
        for (var p : List.of(first, second, root)) {
            AEItemKey definition = AEItemKey.of(p == first ? Items.STONE : p == second ? Items.DIRT : Items.PAPER);
            IPatternDetails details = (IPatternDetails) java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[] {IPatternDetails.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getDefinition" -> definition;
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        case "toString" -> p.id();
                        default -> throw new AssertionError(method);
                    });
            tasks.put(details, exact.patternExecutions().get(p.id()));
        }
        var facade = new BigIntegerCraftingPlan(new GenericStack(OUT, 1), exact, tasks, prepared, true, selected, true);
        assertFalse(facade.fitsStandardLongExecution());
        assertTrue(facade.multiplePaths());
        assertFalse(facade.simulation());
        var state = ExactCraftingJobState.fromPlan(facade);
        var saved = state.save(4096, com.syaru.ae2craftingoptimizer.lifecycle.ACORegistryAccess.require());
        var restored = ExactCraftingJobState.load(saved, 4096, com.syaru.ae2craftingoptimizer.lifecycle.ACORegistryAccess.require());
        assertEquals(selected, restored.selectedBranch().orElseThrow());
        assertEquals(state.taskTotals(), restored.taskTotals());
        assertEquals(saved, restored.save(4096, com.syaru.ae2craftingoptimizer.lifecycle.ACORegistryAccess.require()));
        assertFalse(restored.hasPhysicalExecution());
        var missing = saved.copy();
        missing.remove("selectedBranch");
        assertThrows(IllegalArgumentException.class, () -> ExactCraftingJobState.load(missing, 4096, com.syaru.ae2craftingoptimizer.lifecycle.ACORegistryAccess.require()));
        var corrupt = saved.copy();
        corrupt.getCompound("selectedBranch").putString("programFingerprint", "changed");
        assertThrows(IllegalArgumentException.class, () -> ExactCraftingJobState.load(corrupt, 4096, com.syaru.ae2craftingoptimizer.lifecycle.ACORegistryAccess.require()));
        assertThrows(IllegalArgumentException.class, () -> new BigIntegerCraftingPlan(
                new GenericStack(OUT, 1), exact, tasks, prepared, true));
    }

    @Test void rejectsArithmeticBoundsUnresolvedInputsAndProcessingMachines() {
        var one = pattern("one", RAW, 1, Map.of(OUT, 1L));
        var exact = new BigCraftingPlan<>(OUT, BigInteger.valueOf(256), Map.of("one", BigInteger.valueOf(256)),
                Map.of(RAW, BigInteger.valueOf(256)), Map.of(), Map.of(), 1);
        assertThrows(IllegalArgumentException.class, () -> SelectedBranchPhysicalPlan.prepare(exact, List.of(one), 1, 1, 8));
        assertThrows(IllegalArgumentException.class, () -> SelectedBranchPhysicalPlan.prepare(exact, List.of(), 1, 1, 4096));
        var processing = new CompiledPattern<>("one", one.inputs(), one.outputs(), true);
        assertThrows(IllegalArgumentException.class, () -> SelectedBranchPhysicalPlan.prepare(exact, List.of(processing), 1, 1, 4096));
        var alternative = new CompiledPattern<>("one", List.of(new CompiledPattern.InputSlot<>(List.of(
                new CompiledPattern.Stack<>(RAW, 1), new CompiledPattern.Stack<>(OTHER, 1)))), one.outputs(), false);
        assertThrows(IllegalArgumentException.class, () -> SelectedBranchPhysicalPlan.prepare(exact, List.of(alternative), 1, 1, 4096));
    }

    static CompiledPattern<AEKey> pattern(String id, AEKey input, long amount, Map<AEKey, Long> out) {
        return new CompiledPattern<>(id, List.of(new CompiledPattern.InputSlot<>(
                List.of(new CompiledPattern.Stack<>(input, amount)))), out, false);
    }
    static Map<String, BigInteger> counts(PreparedVectorBatch plan) {
        Map<String, BigInteger> values = new HashMap<>();
        plan.craftingSteps().forEach(s -> values.put(s.patternId(), s.executions()));
        return values;
    }
    static Map<AEKey, BigInteger> replay(PreparedVectorBatch plan, Map<String, CompiledPattern<AEKey>> patterns) {
        Map<AEKey, BigInteger> stock = new HashMap<>();
        plan.totalInputs().forEach(s -> stock.put(s.key(), s.amount()));
        for (var step : plan.craftingSteps()) {
            for (var input : step.selectedInputs()) {
                var amount = step.executions().multiply(BigInteger.valueOf(input.amountPerExecution()));
                assertTrue(stock.getOrDefault(input.key(), BigInteger.ZERO).compareTo(amount) >= 0);
                stock.merge(input.key(), amount.negate(), BigInteger::add);
            }
            patterns.get(step.patternId()).outputs().forEach((k, n) -> stock.merge(k,
                    BigInteger.valueOf(n).multiply(step.executions()), BigInteger::add));
        }
        stock.values().removeIf(n -> n.signum() == 0);
        return stock;
    }
}
