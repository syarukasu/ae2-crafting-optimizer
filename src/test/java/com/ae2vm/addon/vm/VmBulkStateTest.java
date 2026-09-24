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
            assertTrue(execution.stateCopies() > 0, "Branch transactions/replay must remain");
            assertTrue(execution.stateCopies() < 100, "Do not iterate once per requested item");
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
            assertTrue(execution.stateCopies() > 0);
            assertTrue(execution.skippedIterations().signum() > 0);
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
