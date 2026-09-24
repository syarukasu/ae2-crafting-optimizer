package com.ae2vm.addon.nativeengine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.ae2vm.addon.compiler.PatternCompiler;
import com.ae2vm.addon.vm.CraftingVM;
import com.ae2vm.addon.vm.VmSimulationState;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class UpstreamVmAccountingTest {
    @BeforeAll static void bootstrap() throws Exception { NativeVmCaptureTest.bootstrap(); }
    @AfterEach void clearCompiler() { PatternCompiler.clearCache(); PatternCompiler.clearFuzzyGroups(); }

    @Test void upstreamExecutesOnePatternAgainstEmptyPartialAndCompleteStock() {
        var raw = AEItemKey.of(Items.IRON_INGOT);
        var product = AEItemKey.of(Items.DIAMOND);
        var recipe = pattern(product, 1, Map.of(raw, 16L));
        var vm = new CraftingVM(new Object(), key -> key.equals(product) ? recipe : null);
        for (long order : new long[]{1, 2, 1000}) {
            long demand = order * 16;
            for (long available : new long[]{0, 1, demand / 2, demand, demand + 1}) {
                var plan = vm.execute(PatternCompiler.compileRequest(recipe, order), snapshot(Map.of(raw, available)));
                assertEquals(BigInteger.valueOf(order), plan.finalOutput().amount());
                assertEquals(BigInteger.valueOf(order), plan.patternTimes().get(recipe));
                assertEquals(BigInteger.valueOf(Math.min(available, demand)), plan.usedItems().get(raw), "used: order=" + order + " stock=" + available);
                assertEquals(BigInteger.valueOf(Math.max(0, demand - available)), plan.missingItems().get(raw), "missing: order=" + order + " stock=" + available);
                assertEquals(available < demand, plan.simulation());
                assertTrue(plan.emittedItems().isEmpty(), "crafted output must not be counted twice");
            }
        }
    }

    @Test void upstreamAggregatesSharedDependencyBeforeRoundingItsBatchCount() {
        var raw = AEItemKey.of(Items.IRON_INGOT);
        var shared = AEItemKey.of(Items.GOLD_INGOT);
        var a = AEItemKey.of(Items.EMERALD);
        var b = AEItemKey.of(Items.LAPIS_LAZULI);
        var product = AEItemKey.of(Items.DIAMOND);
        var sharedRecipe = pattern(shared, 7, Map.of(raw, 3L));
        var aRecipe = pattern(a, 1, Map.of(shared, 2L));
        var bRecipe = pattern(b, 1, Map.of(shared, 3L));
        var root = pattern(product, 1, Map.of(a, 16L, b, 16L));
        Map<AEKey, IPatternDetails> recipes = Map.of(shared, sharedRecipe, a, aRecipe, b, bRecipe, product, root);
        var vm = new CraftingVM(new Object(), recipes::get);
        for (long order : new long[]{1, 2, 1000}) {
            long sharedCrafts = (80 * order + 6) / 7;
            var plan = vm.execute(PatternCompiler.compileRequest(root, order), snapshot(Map.of()));
            assertEquals(BigInteger.valueOf(order), plan.patternTimes().get(root));
            assertEquals(BigInteger.valueOf(16 * order), plan.patternTimes().get(aRecipe));
            assertEquals(BigInteger.valueOf(16 * order), plan.patternTimes().get(bRecipe));
            assertEquals(BigInteger.valueOf(sharedCrafts), plan.patternTimes().get(sharedRecipe));
            assertEquals(BigInteger.valueOf(sharedCrafts * 3), plan.missingItems().get(raw));
            assertEquals(1, plan.missingItems().size());
        }
    }

    @Test void upstreamUsesTheSuppliedSnapshotWithoutAccessingTheLiveGrid() {
        var raw = AEItemKey.of(Items.IRON_INGOT);
        var intermediate = AEItemKey.of(Items.GOLD_INGOT);
        var product = AEItemKey.of(Items.DIAMOND);
        var root = pattern(product, 1, Map.of(intermediate, 16L));
        var child = pattern(intermediate, 1, Map.of(raw, 2L));
        Map<AEKey, IPatternDetails> recipes = Map.of(product, root, intermediate, child);
        var grid = mock(appeng.api.networking.IGrid.class);
        var vm = new CraftingVM(grid, recipes::get);
        var plan = vm.execute(PatternCompiler.compileRequest(root, 2), snapshot(Map.of(intermediate, 20L)));
        assertEquals(BigInteger.valueOf(20), plan.usedItems().get(intermediate));
        assertEquals(BigInteger.valueOf(12), plan.patternTimes().get(child));
        assertEquals(BigInteger.valueOf(24), plan.missingItems().get(raw));
        verifyNoInteractions(grid);
    }

    @Test void upstreamRechecksIntermediateStockBeforeReusingACompletedPlan() {
        var raw = AEItemKey.of(Items.IRON_INGOT);
        var intermediate = AEItemKey.of(Items.GOLD_INGOT);
        var product = AEItemKey.of(Items.DIAMOND);
        var root = pattern(product, 1, Map.of(intermediate, 16L));
        var child = pattern(intermediate, 1, Map.of(raw, 2L));
        Map<AEKey, IPatternDetails> recipes = Map.of(product, root, intermediate, child);
        var vm = new CraftingVM(new Object(), recipes::get);
        for (long stock : new long[]{0, 1, 8, 16, 32, 8, 0}) {
            var plan = vm.execute(PatternCompiler.compileRequest(root, 1), snapshot(Map.of(raw, 1000L, intermediate, stock)));
            long used = Math.min(stock, 16);
            assertEquals(BigInteger.valueOf(used), plan.usedItems().get(intermediate), "stock=" + stock);
            assertEquals(BigInteger.valueOf((16 - used) * 2), plan.usedItems().get(raw), "stock=" + stock);
            assertEquals(BigInteger.valueOf(16 - used), plan.patternTimes().getOrDefault(child, BigInteger.ZERO), "stock=" + stock);
            assertFalse(plan.simulation());
        }
    }

    @Test void upstreamExecutesWideRequestsStockAndCoefficientsWithoutNarrowing() {
        var raw = AEItemKey.of(Items.IRON_INGOT);
        var product = AEItemKey.of(Items.DIAMOND);
        var recipe = pattern(product, 7, Map.of(raw, Long.MAX_VALUE));
        var vm = new CraftingVM(new Object(), key -> key.equals(product) ? recipe : null);
        for (var order : List.of(BigInteger.ONE, BigInteger.TWO, BigInteger.valueOf(Long.MAX_VALUE),
                BigInteger.ONE.shiftLeft(63), BigInteger.TEN.pow(1024))) {
            var crafts = order.add(BigInteger.valueOf(6)).divide(BigInteger.valueOf(7));
            var demand = crafts.multiply(BigInteger.valueOf(Long.MAX_VALUE));
            for (var stock : List.of(BigInteger.ZERO, demand.divide(BigInteger.TWO), demand, demand.add(BigInteger.ONE))) {
                var plan = vm.execute(PatternCompiler.compileRequest(recipe, order), new VmSimulationState(Map.of(raw, stock)));
                assertEquals(order, plan.finalOutput().amount());
                assertEquals(crafts, plan.patternTimes().get(recipe));
                assertEquals(demand.min(stock), plan.usedItems().get(raw));
                assertEquals(demand.subtract(stock).max(BigInteger.ZERO), plan.missingItems().get(raw));
                assertTrue(plan.bytes().signum() > 0);
            }
        }
    }

    private static VmSimulationState snapshot(Map<AEKey, Long> stock) {
        Map<AEKey, BigInteger> frozen = new LinkedHashMap<>();
        stock.forEach((key, value) -> frozen.put(key, BigInteger.valueOf(value)));
        return new VmSimulationState(frozen);
    }

    @Test void wideAlternativeTrialsReuseByproductsAndDoNotIteratePerItem() {
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(10), () -> {
            var rawA = AEItemKey.of(Items.IRON_INGOT);
            var rawB = AEItemKey.of(Items.COPPER_INGOT);
            var intermediate = AEItemKey.of(Items.GOLD_INGOT);
            var byproduct = AEItemKey.of(Items.EMERALD);
            var product = AEItemKey.of(Items.DIAMOND);
            var first = pattern(intermediate, 1, Map.of(rawA, 1L));
            var second = pattern(intermediate, 1, Map.of(rawB, 1L));
            for (var branch : List.of(first, second)) when(branch.getOutputs()).thenReturn(new GenericStack[]{
                    new GenericStack(intermediate, 1), new GenericStack(byproduct, 1)});
            var ordered = new LinkedHashMap<AEKey, Long>();
            ordered.put(intermediate, 1L); ordered.put(byproduct, 1L);
            var root = pattern(product, 1, ordered);
            Map<AEKey, List<IPatternDetails>> recipes = Map.of(product, List.of(root), intermediate, List.of(first, second));
            var vm = new CraftingVM(new Object(), key -> recipes.getOrDefault(key, List.of()).stream().findFirst().orElse(null));
            vm.setAllPatternsResolver(key -> recipes.getOrDefault(key, List.of()));
            for (var order : List.of(BigInteger.ONE, BigInteger.TWO, BigInteger.valueOf(Long.MAX_VALUE),
                    BigInteger.ONE.shiftLeft(63), BigInteger.TEN.pow(64))) {
                var a = order.divide(BigInteger.TWO);
                var plan = vm.execute(PatternCompiler.compileRequest(root, order),
                        new VmSimulationState(Map.of(rawA, a, rawB, order.subtract(a))));
                assertFalse(plan.simulation());
                assertEquals(order, plan.patternTimes().get(root));
                assertEquals(a, plan.patternTimes().getOrDefault(first, BigInteger.ZERO));
                assertEquals(order.subtract(a), plan.patternTimes().getOrDefault(second, BigInteger.ZERO));
                assertEquals(a, plan.usedItems().get(rawA));
                assertEquals(order.subtract(a), plan.usedItems().get(rawB));
                assertEquals(BigInteger.ZERO, plan.usedItems().get(byproduct));
            }
        });
    }

    @Test void wideReturnedInputsKeepTheirPeakReservationInsteadOfMultiplyingSeeds() {
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(10), () -> {
            var seed = AEItemKey.of(Items.BUCKET);
            var raw = AEItemKey.of(Items.IRON_INGOT);
            var output = AEItemKey.of(Items.DIAMOND);
            var recipe = pattern(output, 1, Map.of(seed, 2L, raw, 1L));
            for (var input : recipe.getInputs()) if (input.getPossibleInputs()[0].what().equals(seed))
                when(input.getRemainingKey(seed)).thenReturn(seed);
            var vm = new CraftingVM(new Object(), key -> key.equals(output) ? recipe : null);
            var order = BigInteger.TEN.pow(64);
            for (long seeds : new long[]{0, 1, 2}) {
                var plan = vm.execute(PatternCompiler.compileRequest(recipe, order),
                        new VmSimulationState(Map.of(seed, BigInteger.valueOf(seeds), raw, order)));
                assertEquals(order, plan.patternTimes().get(recipe));
                assertEquals(BigInteger.valueOf(seeds), plan.usedItems().get(seed));
                assertEquals(BigInteger.valueOf(2 - seeds), plan.missingItems().get(seed));
                assertEquals(order, plan.usedItems().get(raw));
            }
        });
    }

    @Test void wideDamagingToolsReplayACompleteDamagePeriodWithoutFabricatingStock() {
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(10), () -> {
            var tools = new java.util.ArrayList<AEItemKey>();
            for (int damage = 0; damage < 3; damage++) {
                var stack = new net.minecraft.world.item.ItemStack(Items.IRON_PICKAXE);
                stack.setDamageValue(damage);
                tools.add(AEItemKey.of(stack));
            }
            var product = AEItemKey.of(Items.DIAMOND);
            var recipe = pattern(product, 1, Map.of(tools.get(0), 1L));
            var input = recipe.getInputs()[0];
            when(input.isValid(any(), any())).thenAnswer(call -> tools.contains(call.getArgument(0)));
            when(input.getRemainingKey(tools.get(0))).thenReturn(tools.get(1));
            when(input.getRemainingKey(tools.get(1))).thenReturn(tools.get(2));
            var vm = new CraftingVM(new Object(), key -> key.equals(product) ? recipe : null);
            var order = BigInteger.TEN.pow(64);
            var plan = vm.execute(PatternCompiler.compileRequest(recipe, order),
                    new VmSimulationState(Map.of(tools.get(0), BigInteger.ONE)));
            assertEquals(order, plan.patternTimes().get(recipe));
            assertEquals(BigInteger.ONE, plan.usedItems().get(tools.get(0)));
            assertEquals(order.add(BigInteger.TWO).divide(BigInteger.valueOf(3)).subtract(BigInteger.ONE),
                    plan.missingItems().get(tools.get(0)));
            assertEquals(1, plan.missingItems().size());
        });
    }

    private static IPatternDetails pattern(AEKey key, long amount, Map<AEKey, Long> ingredients) {
        var inputs = new IPatternDetails.IInput[ingredients.size()];
        int index = 0;
        for (var entry : ingredients.entrySet()) {
            var input = mock(IPatternDetails.IInput.class);
            when(input.getMultiplier()).thenReturn(entry.getValue());
            when(input.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(entry.getKey(), 1)});
            when(input.isValid(any(), any())).thenAnswer(call -> entry.getKey().equals(call.getArgument(0)));
            inputs[index++] = input;
        }
        var pattern = mock(IPatternDetails.class);
        var output = new GenericStack(key, amount);
        when(pattern.getInputs()).thenReturn(inputs);
        when(pattern.getOutputs()).thenReturn(new GenericStack[]{output});
        when(pattern.getPrimaryOutput()).thenReturn(output);
        return pattern;
    }
}
