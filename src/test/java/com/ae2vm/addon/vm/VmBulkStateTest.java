package com.ae2vm.addon.vm;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.ae2vm.addon.compiler.PatternCompiler;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class VmBulkStateTest {
    @BeforeAll static void bootstrap() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        var field = net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
        field.setAccessible(true);
        field.setBoolean(null, true);
        if (net.minecraft.core.registries.BuiltInRegistries.PAINTING_VARIANT.size() == 0)
            net.minecraft.core.registries.BuiltInRegistries.bootStrap();
        com.syaru.ae2craftingoptimizer.testsupport.TestKeyTypes.initialize();
    }

    @Test void bulkChainUsesNoReplayCopiesAcrossWideOrdersAndStockLevels() {
        var recipes = new LinkedHashMap<AEKey, List<IPatternDetails>>();
        int depth = 64;
        for (int i = 0; i < depth; i++) recipes.put(key(i), List.of(pattern(key(i), Map.of(key(i + 1), 2L))));
        for (var order : List.of(BigInteger.ONE, BigInteger.TWO, BigInteger.valueOf(Long.MAX_VALUE),
                BigInteger.ONE.shiftLeft(63), BigInteger.TEN.pow(64))) {
            var demand = order.shiftLeft(depth);
            for (var available : List.of(BigInteger.ZERO, demand.divide(BigInteger.TWO), demand)) {
                try (var scope = PatternCompiler.openScope()) {
                    var execution = execution(key(0), order, recipes, Map.of(key(depth), available));
                    var result = execution.execute(order, false);
                    assertEquals(order, result.finalOutput().amount());
                    assertEquals(available, result.usedItems().get(key(depth)));
                    assertEquals(demand.subtract(available), result.missingItems().get(key(depth)));
                    assertEquals(available.compareTo(demand) < 0, result.simulation());
                    assertFalse(result.multiplePaths());
                    assertTrue(result.emittedItems().isEmpty());
                    for (int i = 0; i < depth; i++)
                        assertEquals(order.shiftLeft(i), result.patternTimes().get(recipes.get(key(i)).get(0)));
                    var expectedBytes = order.multiply(BigInteger.ONE.shiftLeft(depth + 1).subtract(BigInteger.ONE))
                            .multiply(BigInteger.valueOf(8))
                            .divide(BigInteger.valueOf(key(0).getType().getAmountPerByte()))
                            .add(order.multiply(BigInteger.ONE.shiftLeft(depth).subtract(BigInteger.ONE)))
                            .add(BigInteger.valueOf((depth + 1) * 8L));
                    assertEquals(expectedBytes, result.bytes());
                    System.out.println("Issue217 depth=" + depth + " order=" + order + " stock=" + available
                            + " stateCopies=" + execution.stateCopies());
                    assertEquals(0, execution.stateCopies(), "Unique bulk producers need no local replay window");
                }
            }
        }
    }

    @Test void bulkChildStillContributesToOuterBranchReplayGuardsAndByproducts() {
        var root = pattern(key(0), ordered(key(1), 1, key(4), 1));
        var first = pattern(key(1), Map.of(key(2), 1L));
        var second = pattern(key(1), Map.of(key(5), 1L));
        for (var recipe : List.of(first, second)) when(recipe.getOutputs()).thenReturn(new GenericStack[]{
                new GenericStack(key(1), 1), new GenericStack(key(4), 1)});
        var child = pattern(key(2), Map.of(key(3), 2L));
        Map<AEKey, List<IPatternDetails>> recipes = Map.of(key(0), List.of(root), key(1), List.of(first, second), key(2), List.of(child));
        var order = BigInteger.TEN.pow(64);
        var firstCount = order.divide(BigInteger.TWO);
        try (var scope = PatternCompiler.openScope()) {
            var execution = execution(key(0), order, recipes,
                    Map.of(key(3), firstCount.multiply(BigInteger.TWO), key(5), order.subtract(firstCount)));
            var result = execution.execute(order, false);
            assertFalse(result.simulation());
            assertEquals(order, result.patternTimes().get(root));
            assertEquals(firstCount, result.patternTimes().get(first));
            assertEquals(firstCount, result.patternTimes().get(child));
            assertEquals(order.subtract(firstCount), result.patternTimes().get(second));
            assertEquals(firstCount.multiply(BigInteger.TWO), result.usedItems().get(key(3)));
            assertEquals(order.subtract(firstCount), result.usedItems().get(key(5)));
            assertEquals(BigInteger.ZERO, result.usedItems().get(key(4)));
            assertTrue(result.missingItems().isEmpty());
            assertTrue(execution.branchTrials() > 0, "Branch transactions must remain");
            assertTrue(execution.replays() > 0, "Wide orders must still replay exact effects");
            assertEquals(0, execution.stateCopies(), "Replay records differences, not whole states");
            assertTrue(execution.skippedIterations().signum() > 0);
        }
    }

    @Test void directReturnsRetainPeriodicReplayAndExactSeedReservation() {
        var recipe = pattern(key(0), ordered(key(1), 1, key(2), 1));
        when(recipe.getInputs()[0].getRemainingKey(key(1))).thenReturn(key(1));
        var order = BigInteger.TEN.pow(64);
        try (var scope = PatternCompiler.openScope()) {
            var execution = execution(key(0), order, Map.of(key(0), List.of(recipe)),
                    Map.of(key(1), BigInteger.ONE, key(2), order));
            var result = execution.execute(order, false);
            assertFalse(result.simulation());
            assertEquals(BigInteger.ONE, result.usedItems().get(key(1)));
            assertEquals(order, result.usedItems().get(key(2)));
            assertEquals(order, result.patternTimes().get(recipe));
            assertTrue(execution.replays() > 0);
            assertEquals(0, execution.stateCopies());
            assertTrue(execution.skippedIterations().signum() > 0);
        }
    }

    @Test void replaySnapshotsDoNotCopyOtherTracesOrUnusedIndexes() throws Exception {
        var execution = execution(key(0), BigInteger.ONE, Map.of(), Map.of(key(1), BigInteger.TEN));
        var state = newState(execution);
        for (int i = 2; i < 258; i++) insert(state, key(i), BigInteger.ONE);
        call(state, "extract", new Class<?>[]{AEKey.class, BigInteger.class}, key(1), BigInteger.ONE);
        var traceType = Class.forName("com.ae2vm.addon.vm.VmInstructionExecution$Trace");
        var constructor = traceType.getDeclaredConstructor(VmInstructionExecution.class, state.getClass());
        constructor.setAccessible(true);
        constructor.newInstance(execution, state);
        var trace = constructor.newInstance(execution, state);
        var before = field(trace, "before");
        assertEquals(257, ((Map<?, ?>) field(before, "stock")).size());
        assertEquals(BigInteger.valueOf(9), call(before, "amount", new Class<?>[]{AEKey.class}, key(1)));
        assertTrue(((Map<?, ?>) field(before, "lows")).isEmpty(), "Replay never reads copied enclosing low-water maps");
        assertTrue(((Map<?, ?>) field(before, "keys")).isEmpty(), "Replay never searches the copied fuzzy index");
        assertTrue(((Map<?, ?>) field(before, "used")).isEmpty(), "Peak usage is reconstructed from minimum stock");
        assertEquals(2, ((Map<?, ?>) field(state, "lows")).size(), "Live rollback/minimum tracking must remain");
        insert(state, key(1), BigInteger.TEN.pow(1024));
        assertEquals(BigInteger.valueOf(9), call(before, "amount", new Class<?>[]{AEKey.class}, key(1)));
    }

    @Test void replayCheckpointsDoNotCopyPreviouslyVisitedStock() throws Exception {
        var execution = execution(key(0), BigInteger.ONE, Map.of(), Map.of());
        var state = newState(execution);
        for (int i = 1; i <= 2048; i++) insert(state, key(i), BigInteger.TEN);
        for (int i = 0; i < 64; i++) {
            var traceType = Class.forName("com.ae2vm.addon.vm.VmInstructionExecution$Trace");
            var constructor = traceType.getDeclaredConstructor(VmInstructionExecution.class, state.getClass());
            constructor.setAccessible(true);
            var trace = constructor.newInstance(execution, state);
            insert(state, key(1), BigInteger.ONE);
            assertEquals(BigInteger.TEN.add(BigInteger.valueOf(i)),
                    call(field(trace, "before"), "amount", new Class<?>[]{AEKey.class}, key(1)));
            call(trace, "finish", new Class<?>[]{state.getClass()}, state);
        }
        System.out.println("Issue218 replay checkpoints=64 visitedKeys=2048 fullCopies=" + execution.stateCopies());
        assertEquals(0, execution.stateCopies(), "Checkpoints must record changes, not copy the visited ledger");
    }

    @Test void finishedReplayDoesNotCopyTheAfterStateOrOpenAnUnusedWindow() {
        var recipe = pattern(key(0), Map.of(key(1), 1L));
        when(recipe.getInputs()[0].getRemainingKey(key(1))).thenReturn(key(1));
        var order = BigInteger.TEN.pow(1024).subtract(BigInteger.ONE);
        try (var scope = PatternCompiler.openScope()) {
            var execution = execution(key(0), order, Map.of(key(0), List.of(recipe)), Map.of(key(1), BigInteger.ONE));
            var result = execution.execute(order, false);
            assertFalse(result.simulation());
            assertEquals(Map.of(key(1), BigInteger.ONE), result.usedItems().quantities());
            assertEquals(Map.of(recipe, order), result.patternTimes());
            assertTrue(execution.skippedIterations().signum() > 0);
            System.out.println("Issue218 completed replay: copies=" + execution.stateCopies());
            assertTrue(execution.stateCopies() <= 2, "Only the initial and warmed before-state snapshots are needed");
        }
    }

    @Test @org.junit.jupiter.api.Timeout(30)
    void unusedSubUnitAlternativeMustNotPreventByproductReplay() {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        var recipe = pattern(key(0), Map.of(iron, 1L));
        var input = recipe.getInputs()[0];
        when(input.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(iron, 1), new GenericStack(gold, 8192)});
        when(input.isValid(any(), any())).thenAnswer(call -> iron.equals(call.getArgument(0)) || gold.equals(call.getArgument(0)));
        when(input.getRemainingKey(iron)).thenReturn(iron);
        when(recipe.getOutputs()).thenReturn(new GenericStack[]{new GenericStack(key(0), 1), new GenericStack(gold, 1)});
        var order = BigInteger.valueOf(2048);
        try (var scope = PatternCompiler.openScope()) {
            var execution = execution(key(0), order, Map.of(key(0), List.of(recipe)),
                    Map.of(iron, BigInteger.ONE, gold, BigInteger.ONE));
            var result = execution.execute(order, false);
            assertFalse(result.simulation());
            assertEquals(Map.of(iron, BigInteger.ONE), result.usedItems().quantities());
            assertEquals(Map.of(recipe, order), result.patternTimes());
            assertTrue(result.missingItems().isEmpty());
            System.out.println("Issue218 unused candidate: work=" + execution.work() + " copies=" + execution.stateCopies());
            assertTrue(execution.work() < 64, "A never-consulted later alternative must not constrain replay");
        }
    }

    @Test @org.junit.jupiter.api.Timeout(30)
    void growingSubUnitAlternativeDoesNotForceOneRequestPerCraft() {
        long unit = Long.MAX_VALUE;
        var recipe = subUnitRecipe(unit);
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        for (var order : List.of(BigInteger.valueOf(1_000_000_000_000L),
                BigInteger.valueOf(unit).subtract(BigInteger.ONE), BigInteger.valueOf(unit),
                BigInteger.valueOf(unit).add(BigInteger.ONE))) {
            try (var scope = PatternCompiler.openScope()) {
                var execution = new VmInstructionExecution(PatternCompiler.compileRequest(key(0), order),
                        new VmSimulationState(Map.of(iron, order)),
                        k -> k.equals(key(0)) ? List.of(recipe) : List.of(), k -> false,
                        work -> assertTrue(work < 8192, "Unusable alternatives must not force per-craft work"));
                var actual = execution.execute(order, false);
                assertFalse(actual.simulation());
                assertEquals(Map.of(iron, order.min(BigInteger.valueOf(unit))), actual.usedItems().quantities());
                assertTrue(actual.missingItems().isEmpty());
                assertEquals(Map.of(recipe, order), actual.patternTimes());
                assertEquals(BigInteger.ZERO, actual.usedItems().get(gold));
                System.out.println("Issue218 sub-unit order=" + order + " work=" + execution.work());
                assertTrue(execution.work() < 64, "Retain exact threshold changes without linear repetition");
            }
        }
    }

    @Test @org.junit.jupiter.api.Timeout(60)
    void subUnitThresholdMatchesOrderedExecutionWithMissingAndStockedInputs() {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        var recipe = subUnitRecipe(16);
        var recipes = Map.<AEKey, List<IPatternDetails>>of(key(0), List.of(recipe));
        for (int count : new int[]{1, 2, 15, 16, 17, 31, 32, 33, 100}) {
            var order = BigInteger.valueOf(count);
            for (int toolStock : new int[]{0, 1, 2}) {
                for (int byproductStock : new int[]{0, 1, 14, 15, 16, 17, 32}) {
                    var stock = Map.<AEKey, BigInteger>of(iron, BigInteger.valueOf(toolStock),
                            gold, BigInteger.valueOf(byproductStock));
                    VmCraftingPlan baseline;
                    try (var scope = PatternCompiler.openScope()) {
                        baseline = executionMode(order, recipes, stock, false, false).execute(order, false);
                    }
                    try (var scope = PatternCompiler.openScope()) {
                        samePlan(baseline, executionMode(order, recipes, stock, true, true).execute(order, false));
                    }
                }
            }
        }
    }

    private static IPatternDetails subUnitRecipe(long unit) {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        var recipe = pattern(key(0), Map.of(gold, unit));
        var input = recipe.getInputs()[0];
        when(input.getPossibleInputs()).thenReturn(new GenericStack[]{
                new GenericStack(gold, unit), new GenericStack(iron, 1)});
        when(input.isValid(any(), any())).thenAnswer(call -> gold.equals(call.getArgument(0)) || iron.equals(call.getArgument(0)));
        when(recipe.getOutputs()).thenReturn(new GenericStack[]{new GenericStack(key(0), 1), new GenericStack(gold, 1)});
        return recipe;
    }

    @Test @org.junit.jupiter.api.Timeout(30)
    void lookaheadReplayMatchesExactCatalystAccountingThrough3402Bits() {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        var recipe = pattern(key(0), Map.of(iron, 1L));
        var input = recipe.getInputs()[0];
        when(input.isValid(any(), any())).thenAnswer(call -> iron.equals(call.getArgument(0)) || gold.equals(call.getArgument(0)));
        when(input.getRemainingKey(iron)).thenReturn(iron);
        when(recipe.getOutputs()).thenReturn(new GenericStack[]{new GenericStack(key(0), 1), new GenericStack(gold, 1)});
        Map<AEKey, List<IPatternDetails>> recipes = Map.of(key(0), List.of(recipe));
        for (var order : List.of(BigInteger.ONE, BigInteger.TWO, BigInteger.valueOf(Long.MAX_VALUE),
                BigInteger.ONE.shiftLeft(63), BigInteger.TEN.pow(1024).subtract(BigInteger.ONE))) {
            for (var seeds : List.of(BigInteger.ZERO, BigInteger.ONE, order)) {
                var stock = Map.<AEKey, BigInteger>of(iron, seeds, gold, BigInteger.ONE);
                VmCraftingPlan expected;
                when(input.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(iron, 1)});
                try (var scope = PatternCompiler.openScope()) {
                    expected = executionMode(order, recipes, stock, false, false).execute(order, false);
                }
                when(input.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(iron, 1), new GenericStack(gold, 8192)});
                try (var scope = PatternCompiler.openScope()) {
                    var execution = new VmInstructionExecution(PatternCompiler.compileRequest(key(0), order),
                            new VmSimulationState(stock), key -> recipes.getOrDefault(key, List.of()), key -> false,
                            work -> assertTrue(work < 1024, "Replay must not expand an enormous order one craft at a time"));
                    var actual = execution.execute(order, false);
                    samePlan(expected, actual);
                    assertEquals(seeds.signum() > 0 ? BigInteger.ONE : BigInteger.ZERO, actual.usedItems().get(iron));
                    assertEquals(seeds.signum() == 0 ? BigInteger.ONE : BigInteger.ZERO, actual.missingItems().get(iron));
                    assertEquals(BigInteger.ZERO, actual.usedItems().get(gold));
                    assertEquals(Map.of(recipe, order), actual.patternTimes());
                    assertTrue(execution.work() < 64);
                }
            }
        }
    }

    @Test void bulkBytecodeStillHonorsCancellation() {
        var recipe = pattern(key(0), Map.of(key(1), 1L));
        when(recipe.getInputs()[0].isValid(any(), any())).thenAnswer(call -> {
            Thread.currentThread().interrupt();
            return true;
        });
        try (var scope = PatternCompiler.openScope()) {
            var execution = execution(key(0), BigInteger.ONE, Map.of(key(0), List.of(recipe)),
                    Map.of(key(1), BigInteger.ONE));
            assertThrows(java.util.concurrent.CancellationException.class, () -> execution.execute(BigInteger.ONE, false));
        } finally { Thread.interrupted(); }
    }

    @Test void singleExecutionAlternativesNeedRollbackButNoReplayCopies() {
        var first = pattern(key(0), ordered(key(1), 1, key(2), 1));
        var second = pattern(key(0), Map.of(key(3), 1L));
        Map<AEKey, List<IPatternDetails>> recipes = Map.of(key(0), List.of(first, second));
        for (boolean supplied : new boolean[]{false, true}) {
            Map<AEKey, BigInteger> stock = supplied ? Map.of(key(1), BigInteger.ONE, key(3), BigInteger.ONE)
                    : Map.of(key(1), BigInteger.ONE);
            try (var scope = PatternCompiler.openScope()) {
                var baseline = executionMode(BigInteger.ONE, recipes, stock, false, false).execute(BigInteger.ONE, false);
                var optimized = executionMode(BigInteger.ONE, recipes, stock, true, true);
                var plan = optimized.execute(BigInteger.ONE, false);
                samePlan(baseline, plan);
                if (supplied) {
                    assertEquals(Map.of(key(3), BigInteger.ONE), plan.usedItems().quantities());
                    assertEquals(Map.of(second, BigInteger.ONE), plan.patternTimes());
                }
                System.out.println("Issue218 single branch: copies=" + optimized.stateCopies()
                        + " trials=" + optimized.branchTrials() + " missing=" + plan.simulation());
                assertEquals(0, optimized.stateCopies(), "One execution cannot use a repeated-block snapshot");
                assertTrue(optimized.branchTrials() > 0, "Retain transactional trials and rollback");
            }
        }
    }

    @Test void oneShotChildStillGuardsWideParentReplayAndPrioritySwitch() {
        var root = pattern(key(0), Map.of(key(1), 1L));
        var rootAlternative = pattern(key(0), Map.of(key(8), 1L));
        var first = pattern(key(1), ordered(key(2), 1, key(3), 1));
        var second = pattern(key(1), Map.of(key(4), 1L));
        Map<AEKey, List<IPatternDetails>> recipes = Map.of(key(0), List.of(root, rootAlternative), key(1), List.of(first, second));
        for (var order : List.of(BigInteger.ONE, BigInteger.TWO, BigInteger.ONE.shiftLeft(63), BigInteger.TEN.pow(64))) {
            var partial = order.divide(BigInteger.TWO);
            Map<AEKey, BigInteger> stock = Map.of(key(2), order, key(3), partial, key(4), order.subtract(partial));
            try (var scope = PatternCompiler.openScope()) {
                var expected = executionMode(order, recipes, stock, false, false).execute(order, false);
                var optimized = executionMode(order, recipes, stock, true, true);
                var result = optimized.execute(order, false);
                samePlan(expected, result);
                assertFalse(result.simulation());
                assertEquals(partial, result.patternTimes().getOrDefault(first, BigInteger.ZERO));
                assertEquals(order.subtract(partial), result.patternTimes().getOrDefault(second, BigInteger.ZERO));
            }
        }
    }

    @Test void skippedSubUnitStockBecomesUsableAfterByproductRefill() {
        var root = pattern(key(0), Map.of(key(1), 2L));
        when(root.getOutputs()).thenReturn(new GenericStack[]{new GenericStack(key(0), 1), new GenericStack(key(1), 2)});
        var refill = pattern(key(1), Map.of(key(2), 1L));
        when(refill.getOutputs()).thenReturn(new GenericStack[]{new GenericStack(key(1), 2)});
        Map<AEKey, List<IPatternDetails>> recipes = Map.of(key(0), List.of(root), key(1), List.of(refill));
        for (var order : List.of(BigInteger.ONE, BigInteger.TWO, BigInteger.valueOf(Long.MAX_VALUE),
                BigInteger.ONE.shiftLeft(63), BigInteger.TEN.pow(64))) {
            Map<AEKey, BigInteger> stock = Map.of(key(1), BigInteger.ONE, key(2), BigInteger.ONE);
            try (var scope = PatternCompiler.openScope()) {
                var expected = executionMode(order, recipes, stock, false, false).execute(order, false);
                var optimized = executionMode(order, recipes, stock, true, true);
                var actual = optimized.execute(order, false);
                samePlan(expected, actual);
                assertFalse(actual.simulation());
                assertEquals(Map.of(key(2), BigInteger.ONE), actual.usedItems().quantities());
                assertEquals(Map.of(root, order, refill, BigInteger.ONE), actual.patternTimes());
            }
        }
    }

    @Test void unchangedKindsAvoidFullScansDespiteQuantityChanges() throws Exception {
        var execution = execution(key(0), BigInteger.ONE, Map.of(), Map.of());
        var before = newState(execution);
        for (int i = 0; i < 256; i++) insert(before, key(i), BigInteger.ONE);
        var after = call(before, "copy", new Class<?>[0]);
        for (int i = 0; i < 2000; i++) {
            insert(after, key(i % 256), BigInteger.TEN.pow(64));
            assertTrue(sameKinds(before, after));
        }
        System.out.println("Issue217 quantityOnlyComparisons=2000 fullDomainScans=" + execution.stockKeyScans());
        assertEquals(0, execution.stockKeyScans());
    }

    @Test void siblingKindsRollbackAndZeroQuantityReadsRemainExact() throws Exception {
        var execution = execution(key(0), BigInteger.ONE, Map.of(), Map.of());
        var base = newState(execution);
        insert(base, key(0), BigInteger.ONE);
        var left = call(base, "copy", new Class<?>[0]);
        var right = call(base, "copy", new Class<?>[0]);
        insert(left, key(1), BigInteger.ZERO);
        insert(right, key(2), BigInteger.ZERO);
        assertFalse(sameKinds(left, right), "Same-size sibling branches have different NBT domains");
        call(left, "adopt", new Class<?>[]{base.getClass()}, base);
        assertTrue(sameKinds(left, base), "Rollback restores the base domain");
        assertFalse(sameKinds(left, right));
        call(left, "read", new Class<?>[]{AEKey.class, BigInteger.class}, key(3), BigInteger.ONE);
        assertFalse(sameKinds(left, right), "Reading an absent key still changes the observed domain");
        call(left, "adopt", new Class<?>[]{base.getClass()}, base);
        call(left, "extract", new Class<?>[]{AEKey.class, BigInteger.class}, key(4), BigInteger.ONE);
        assertFalse(sameKinds(left, base), "An absent extraction must invalidate membership too");
        assertTrue(sameKinds(base, base));
    }

    @Test void independentlyBuiltEqualKindsAreCheckedOnceThenShared() throws Exception {
        var execution = execution(key(0), BigInteger.ONE, Map.of(), Map.of());
        var a = newState(execution);
        var b = newState(execution);
        insert(a, key(1), BigInteger.ONE); insert(a, key(2), BigInteger.TWO);
        insert(b, key(2), BigInteger.TEN); insert(b, key(1), BigInteger.ZERO);
        assertTrue(sameKinds(a, b));
        long checked = execution.stockKeyScans();
        assertEquals(1, checked, "Independent histories require an exact comparison");
        for (int i = 0; i < 100; i++) assertTrue(sameKinds(a, b));
        assertEquals(checked, execution.stockKeyScans());
        insert(b, key(3), BigInteger.ZERO);
        assertFalse(sameKinds(a, b), "A shared token must not survive a new key");
    }

    @Test @org.junit.jupiter.api.Timeout(30)
    void lowWaterSnapshotsAreOnlyCreatedForPositiveReplays() {
        int period = 16;
        var toolInput = mock(IPatternDetails.IInput.class);
        when(toolInput.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(tool(0), 1)});
        when(toolInput.getMultiplier()).thenReturn(1L);
        when(toolInput.isValid(any(), any())).thenAnswer(call -> call.getArgument(0) instanceof AEItemKey item
                && item.getItem() == Items.NETHERITE_PICKAXE);
        when(toolInput.getRemainingKey(any())).thenAnswer(call -> {
            int damage = ((AEItemKey) call.getArgument(0)).toStack().getDamageValue();
            return damage + 1 < period ? tool(damage + 1) : null;
        });
        var recipe = pattern(key(0), Map.of(key(1), 1L));
        var rawInput = recipe.getInputs()[0];
        when(recipe.getInputs()).thenReturn(new IPatternDetails.IInput[]{toolInput, rawInput});
        for (var order : List.of(BigInteger.ONE, BigInteger.TWO, BigInteger.valueOf(Long.MAX_VALUE),
                BigInteger.ONE.shiftLeft(63), BigInteger.TEN.pow(64))) {
            var requiredTools = order.add(BigInteger.valueOf(period - 1)).divide(BigInteger.valueOf(period));
            for (var available : List.of(BigInteger.ZERO, BigInteger.ONE, requiredTools)) {
                try (var scope = PatternCompiler.openScope()) {
                    var execution = execution(key(0), order, Map.of(key(0), List.of(recipe)),
                            Map.of(tool(0), available, key(1), order));
                    var result = execution.execute(order, false);
                    assertEquals(order, result.finalOutput().amount());
                    assertEquals(order, result.patternTimes().get(recipe));
                    assertEquals(available, result.usedItems().get(tool(0)));
                    assertEquals(requiredTools.subtract(available), result.missingItems().get(tool(0)));
                    assertEquals(order, result.usedItems().get(key(1)));
                    System.out.println("Issue217 period=" + period + " order=" + order + " seeds=" + available
                            + " minimumSnapshots=" + execution.minimumSnapshots() + " replays=" + execution.replays()
                            + " keyScans=" + execution.stockKeyScans());
                    assertEquals(execution.replays(), execution.minimumSnapshots(), "No copy on unsuccessful replay checks");
                    assertEquals(0, execution.stockKeyScans(), "Inherited domains or changed sizes need no full scans");
                }
            }
        }
    }

    @Test @org.junit.jupiter.api.Timeout(20)
    void alternativeOnlyCatalystIsReturnedAndReusedAtWideQuantities() {
        var recipe = pattern(key(0), ordered(key(1), 1, key(3), 1));
        var input = recipe.getInputs()[0];
        when(input.getPossibleInputs()).thenReturn(new GenericStack[]{
                new GenericStack(key(1), 1), new GenericStack(key(2), 1)});
        when(input.isValid(any(), any())).thenAnswer(call ->
                key(1).equals(call.getArgument(0)) || key(2).equals(call.getArgument(0)));
        when(input.getRemainingKey(key(2))).thenReturn(key(2));
        for (var order : List.of(BigInteger.TWO, BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE),
                BigInteger.TEN.pow(1024).subtract(BigInteger.ONE))) {
            for (var raw : List.of(BigInteger.ZERO, order.divide(BigInteger.TWO), order)) {
                try (var scope = PatternCompiler.openScope()) {
                    var result = execution(key(0), order, Map.of(key(0), List.of(recipe)),
                            Map.of(key(2), BigInteger.ONE, key(3), raw)).execute(order, false);
                    assertEquals(BigInteger.ONE, result.usedItems().get(key(2)));
                    assertEquals(raw, result.usedItems().get(key(3)));
                    assertEquals(order.subtract(raw), result.missingItems().get(key(3)));
                    assertEquals(BigInteger.ZERO, result.missingItems().get(key(1)),
                            "The alternate catalyst returns after each craft; no primary seed is needed");
                    assertEquals(order, result.patternTimes().get(recipe));
                }
            }
        }
    }

    @Test @org.junit.jupiter.api.Timeout(20)
    void alternativeOnlyContainersSupplyTheFollowingInputWithoutInventingStock() {
        var root = pattern(key(0), ordered(key(1), 1, key(4), 1));
        var child = pattern(key(1), Map.of(key(2), 1L));
        var slot = child.getInputs()[0];
        when(slot.getPossibleInputs()).thenReturn(new GenericStack[]{
                new GenericStack(key(2), 1), new GenericStack(key(3), 1)});
        when(slot.isValid(any(), any())).thenAnswer(call ->
                key(2).equals(call.getArgument(0)) || key(3).equals(call.getArgument(0)));
        when(slot.getRemainingKey(key(3))).thenReturn(key(4));
        for (var order : List.of(BigInteger.ONE, BigInteger.TWO, BigInteger.valueOf(Long.MAX_VALUE),
                BigInteger.ONE.shiftLeft(63), BigInteger.TEN.pow(1024).subtract(BigInteger.ONE))) {
            for (var available : List.of(BigInteger.ZERO, order.divide(BigInteger.TWO), order)) {
                try (var scope = PatternCompiler.openScope()) {
                    var result = execution(key(0), order, Map.of(key(0), List.of(root), key(1), List.of(child)),
                            Map.of(key(3), available)).execute(order, false);
                    assertEquals(available, result.usedItems().get(key(3)));
                    assertEquals(order.subtract(available), result.missingItems().get(key(2)));
                    assertEquals(order.subtract(available), result.missingItems().get(key(4)));
                    assertEquals(BigInteger.ZERO, result.usedItems().get(key(4)),
                            "Returned containers are generated by this plan, not taken from initial stock");
                    assertEquals(order, result.patternTimes().get(root));
                    assertEquals(order, result.patternTimes().get(child));
                }
            }
        }
    }

    private static AEItemKey tool(int damage) {
        var stack = new ItemStack(Items.NETHERITE_PICKAXE);
        stack.setDamageValue(damage);
        return AEItemKey.of(stack);
    }

    @Test @org.junit.jupiter.api.Timeout(60)
    void sparseBranchesMatchCopyingWithNestedFailureByproductsAndWideStock() {
        var root = pattern(key(0), ordered(key(1), 1, key(7), 1));
        var failing = pattern(key(1), ordered(key(2), 1, key(9), 1));
        var success = pattern(key(1), ordered(key(2), 1, key(3), 1));
        when(success.getOutputs()).thenReturn(new GenericStack[]{new GenericStack(key(1), 1), new GenericStack(key(7), 1)});
        var childA = pattern(key(2), Map.of(key(4), 1L));
        var childB = pattern(key(2), Map.of(key(5), 1L));
        Map<AEKey, List<IPatternDetails>> recipes = Map.of(key(0), List.of(root), key(1), List.of(failing, success), key(2), List.of(childA, childB));
        for (var order : List.of(BigInteger.ONE, BigInteger.TWO, BigInteger.valueOf(Long.MAX_VALUE),
                BigInteger.ONE.shiftLeft(63), BigInteger.TEN.pow(64))) {
            for (var available : List.of(BigInteger.ZERO, order.divide(BigInteger.TWO), order)) {
                Map<AEKey, BigInteger> stock = Map.of(key(4), available.divide(BigInteger.TWO), key(5), available.subtract(available.divide(BigInteger.TWO)),
                        key(3), available);
                VmCraftingPlan baseline;
                try (var scope = PatternCompiler.openScope()) {
                    baseline = executionMode(order, recipes, stock, false, false).execute(order, false);
                }
                for (boolean sparse : new boolean[]{false, true}) {
                    try (var scope = PatternCompiler.openScope()) {
                        var execution = executionMode(order, recipes, stock, sparse, true);
                        samePlan(baseline, execution.execute(order, false));
                        assertTrue(execution.branchTrials() > 0);
                    }
                }
            }
        }
    }

    @Test void sparseTrialsDoNotCopyUnrelatedPreviouslyVisitedKeys() {
        var raw = new LinkedHashMap<AEKey, Long>();
        var stock = new LinkedHashMap<AEKey, BigInteger>();
        for (int i = 10; i < 138; i++) { raw.put(key(i), 1L); stock.put(key(i), BigInteger.TWO); }
        raw.put(key(1), 1L);
        stock.put(key(2), BigInteger.ONE);
        Map<AEKey, List<IPatternDetails>> recipes = Map.of(key(0), List.of(pattern(key(0), raw)),
                key(1), List.of(pattern(key(1), Map.of(key(2), 1L)), pattern(key(1), Map.of(key(3), 1L))));
        try (var scope = PatternCompiler.openScope()) {
            var copy = executionMode(BigInteger.ONE, recipes, stock, false, true);
            var sparse = executionMode(BigInteger.ONE, recipes, stock, true, true);
            samePlan(copy.execute(BigInteger.ONE, false), sparse.execute(BigInteger.ONE, false));
            System.out.println("Issue217 branch entries: copying=" + copy.branchEntries() + " sparse=" + sparse.branchEntries());
            assertTrue(sparse.branchEntries() * 4 < copy.branchEntries(), "Branch writes must not scale with untouched stock");
        }
    }

    @Test @org.junit.jupiter.api.Timeout(60)
    void branchModesAgreeForReturnedToolsFluidsAndCancellation() {
        var fluid = appeng.api.stacks.AEFluidKey.of(net.minecraft.world.level.material.Fluids.WATER);
        var toolInput = mock(IPatternDetails.IInput.class);
        when(toolInput.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(tool(0), 1)});
        when(toolInput.getMultiplier()).thenReturn(1L);
        when(toolInput.isValid(any(), any())).thenAnswer(call -> call.getArgument(0) instanceof AEItemKey key
                && key.getItem() == Items.NETHERITE_PICKAXE);
        when(toolInput.getRemainingKey(any())).thenAnswer(call -> {
            int damage = ((AEItemKey) call.getArgument(0)).toStack().getDamageValue();
            return damage < 2 ? tool(damage + 1) : null;
        });
        var success = pattern(key(0), Map.of(fluid, 1000L));
        var waterInput = success.getInputs()[0];
        when(success.getInputs()).thenReturn(new IPatternDetails.IInput[]{toolInput, waterInput});
        var failing = pattern(key(0), ordered(key(2), 1, key(3), 1));
        Map<AEKey, List<IPatternDetails>> recipes = Map.of(key(0), List.of(failing, success));
        for (var order : List.of(BigInteger.ONE, BigInteger.TWO, BigInteger.valueOf(Long.MAX_VALUE),
                BigInteger.ONE.shiftLeft(63), BigInteger.TEN.pow(64))) {
            for (var amount : List.of(BigInteger.ZERO, order.divide(BigInteger.TWO), order)) {
                Map<AEKey, BigInteger> stock = Map.of(tool(0), order.add(BigInteger.TWO).divide(BigInteger.valueOf(3)),
                        fluid, amount.multiply(BigInteger.valueOf(1000)), key(2), order);
                try (var scope = PatternCompiler.openScope()) {
                    var baseline = executionMode(order, recipes, stock, false, false).execute(order, false);
                    samePlan(baseline, executionMode(order, recipes, stock, false, true).execute(order, false));
                    samePlan(baseline, executionMode(order, recipes, stock, true, true).execute(order, false));
                }
            }
        }
        when(failing.getInputs()[0].isValid(any(), any())).thenAnswer(call -> {
            Thread.currentThread().interrupt(); return true;
        });
        for (boolean sparse : new boolean[]{false, true}) {
            try (var scope = PatternCompiler.openScope()) {
                var execution = executionMode(BigInteger.ONE, recipes, Map.of(key(2), BigInteger.ONE), sparse, true);
                assertThrows(java.util.concurrent.CancellationException.class, () -> execution.execute(BigInteger.ONE, false));
            } finally { Thread.interrupted(); }
        }
    }

    private static VmInstructionExecution executionMode(BigInteger order, Map<AEKey, List<IPatternDetails>> recipes,
            Map<AEKey, BigInteger> stock, boolean sparse, boolean guards) {
        return new VmInstructionExecution(PatternCompiler.compileRequest(key(0), order), new VmSimulationState(stock),
                key -> recipes.getOrDefault(key, List.of()), key -> false, ignored -> { }, sparse, guards);
    }
    private static void samePlan(VmCraftingPlan a, VmCraftingPlan b) {
        assertEquals(a.finalOutput(), b.finalOutput()); assertEquals(a.bytes(), b.bytes());
        assertEquals(a.simulation(), b.simulation()); assertEquals(a.multiplePaths(), b.multiplePaths());
        assertEquals(a.usedItems().quantities(), b.usedItems().quantities());
        assertEquals(a.missingItems().quantities(), b.missingItems().quantities());
        assertEquals(a.emittedItems().quantities(), b.emittedItems().quantities());
        assertEquals(a.patternTimes(), b.patternTimes());
    }
    private static Object newState(VmInstructionExecution execution) throws Exception {
        var type = Class.forName("com.ae2vm.addon.vm.VmInstructionExecution$State");
        var constructor = type.getDeclaredConstructor(VmInstructionExecution.class);
        constructor.setAccessible(true);
        return constructor.newInstance(execution);
    }
    private static Object call(Object receiver, String name, Class<?>[] types, Object... args) throws Exception {
        var method = receiver.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(receiver, args);
    }
    private static Object field(Object receiver, String name) throws Exception {
        var field = receiver.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(receiver);
    }
    private static boolean sameKinds(Object a, Object b) throws Exception {
        return (boolean) call(a, "sameStockKeys", new Class<?>[]{a.getClass()}, b);
    }
    private static void insert(Object state, AEKey key, BigInteger count) throws Exception {
        call(state, "insert", new Class<?>[]{AEKey.class, BigInteger.class}, key, count);
    }

    private static VmInstructionExecution execution(AEKey root, BigInteger order,
            Map<AEKey, List<IPatternDetails>> recipes, Map<AEKey, BigInteger> stock) {
        return new VmInstructionExecution(PatternCompiler.compileRequest(root, order), new VmSimulationState(stock),
                key -> recipes.getOrDefault(key, List.of()), key -> false, ignored -> { });
    }
    private static AEItemKey key(int index) {
        var stack = new ItemStack(Items.PAPER);
        stack.getOrCreateTag().putInt("stage", index);
        return AEItemKey.of(stack);
    }
    private static Map<AEKey, Long> ordered(AEKey first, long a, AEKey second, long b) {
        var inputs = new LinkedHashMap<AEKey, Long>();
        inputs.put(first, a); inputs.put(second, b);
        return inputs;
    }
    private static IPatternDetails pattern(AEKey output, Map<AEKey, Long> inputs) {
        var slots = inputs.entrySet().stream().map(entry -> {
            var slot = mock(IPatternDetails.IInput.class);
            when(slot.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(entry.getKey(), entry.getValue())});
            when(slot.getMultiplier()).thenReturn(1L);
            when(slot.isValid(any(), any())).thenAnswer(call -> entry.getKey().equals(call.getArgument(0)));
            return slot;
        }).toArray(IPatternDetails.IInput[]::new);
        var pattern = mock(IPatternDetails.class);
        when(pattern.getDefinition()).thenReturn(AEItemKey.of(Items.PAPER));
        when(pattern.getInputs()).thenReturn(slots);
        when(pattern.getOutputs()).thenReturn(new GenericStack[]{new GenericStack(output, 1)});
        when(pattern.getPrimaryOutput()).thenReturn(new GenericStack(output, 1));
        return pattern;
    }
}
