package com.ae2vm.addon.nativeengine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** #218: explore recipes only when the ordered calculation needs their output. */
class NativeVmDemandGraphTest {
    @BeforeAll static void bootstrap() throws Exception { NativeVmCaptureTest.bootstrap(); }

    @Test void stockedIntermediateDoesNotInspectItsManufacturingRecipe() {
        var raw = AEItemKey.of(Items.IRON_INGOT);
        var middle = AEItemKey.of(Items.GOLD_INGOT);
        var root = AEItemKey.of(Items.DIAMOND);
        var upstream = pattern(middle, raw, 16);
        var finish = pattern(root, middle, 16);
        var capture = capture(Map.of(root, List.of(finish), middle, List.of(upstream)),
                Map.of(middle, BigInteger.valueOf(16)));
        clearInvocations(upstream);
        var result = capture.plan(root, BigInteger.ONE, false);
        capture.validate();
        assertEquals(Map.of(middle, BigInteger.valueOf(16)), result.used());
        assertTrue(result.missing().isEmpty());
        assertEquals(Map.of(finish, BigInteger.ONE), patternCounts(capture, result));
        verifyNoInteractions(upstream);
    }

    @Test void unusedAlternativeDoesNotInspectItsDescendants() {
        var raw = AEItemKey.of(Items.IRON_INGOT);
        var middle = AEItemKey.of(Items.GOLD_INGOT);
        var root = AEItemKey.of(Items.DIAMOND);
        var unused = pattern(middle, AEItemKey.of(Items.EMERALD), 16);
        var first = pattern(root, raw, 1);
        var second = pattern(root, middle, 1);
        var capture = capture(Map.of(root, List.of(first, second), middle, List.of(unused)),
                Map.of(raw, BigInteger.TWO));
        clearInvocations(unused);
        var result = capture.plan(root, BigInteger.TWO, false);
        capture.validate();
        assertEquals(Map.of(first, BigInteger.TWO), patternCounts(capture, result));
        assertEquals(Map.of(raw, BigInteger.TWO), result.used());
        assertTrue(result.missing().isEmpty());
        verifyNoInteractions(unused);
    }

    @Test void aMalformedUnusedDescendantCannotAbortAStockedOrder() {
        var middle = AEItemKey.of(Items.GOLD_INGOT);
        var root = AEItemKey.of(Items.DIAMOND);
        var broken = mock(IPatternDetails.class);
        when(broken.getInputs()).thenThrow(new IllegalStateException("unused broken pattern"));
        var capture = capture(Map.of(root, List.of(pattern(root, middle, 1)), middle, List.of(broken)),
                Map.of(middle, BigInteger.ONE));
        var result = assertDoesNotThrow(() -> capture.plan(root, BigInteger.ONE, false));
        assertDoesNotThrow(capture::validate);
        assertTrue(result.missing().isEmpty());
    }

    @Test void demandCaptureKeepsExactPartialAndMissingCountsAtEveryOrderSize() {
        var raw = AEItemKey.of(Items.IRON_INGOT);
        var middle = AEItemKey.of(Items.GOLD_INGOT);
        var root = AEItemKey.of(Items.DIAMOND);
        var upstream = pattern(middle, raw, 16);
        var finish = pattern(root, middle, 16);
        for (var order : List.of(BigInteger.ONE, BigInteger.TWO, BigInteger.valueOf(Long.MAX_VALUE),
                BigInteger.ONE.shiftLeft(63), BigInteger.TEN.pow(64))) {
            var total = order.multiply(BigInteger.valueOf(16));
            for (var available : List.of(BigInteger.ZERO, total.divide(BigInteger.TWO), total)) {
                var capture = capture(Map.of(root, List.of(finish), middle, List.of(upstream)),
                        Map.of(middle, available));
                var result = capture.plan(root, order, false);
                capture.validate();
                var remainder = total.subtract(available);
                assertEquals(available, result.used().getOrDefault(middle, BigInteger.ZERO));
                assertEquals(remainder.multiply(BigInteger.valueOf(16)),
                        result.missing().getOrDefault(raw, BigInteger.ZERO));
                var expected = new LinkedHashMap<IPatternDetails, BigInteger>();
                expected.put(finish, order);
                if (remainder.signum() > 0) expected.put(upstream, remainder);
                assertEquals(expected, patternCounts(capture, result));
            }
        }
    }

    @Test void producerReplacementIsStillRejectedBeforeAdoption() {
        var root = AEItemKey.of(Items.DIAMOND);
        var raw = AEItemKey.of(Items.IRON_INGOT);
        var service = mock(ICraftingService.class);
        var original = pattern(root, raw, 1);
        var replacement = pattern(root, raw, 2);
        when(service.getCraftingFor(root)).thenReturn(List.of(original));
        var capture = capture(service, Map.of(raw, BigInteger.ONE));
        capture.plan(root, BigInteger.ONE, false);
        when(service.getCraftingFor(root)).thenReturn(List.of(replacement));
        assertThrows(LegacyVmState.Changed.class, capture::validate);
    }

    @Test void nativeProcessingInputDoesNotProbeThousandsOfWrongNbtVariants() throws Exception {
        var output = AEItemKey.of(Items.DIAMOND);
        var primary = AEItemKey.of(Items.IRON_INGOT);
        var stock = new LinkedHashMap<AEKey, BigInteger>();
        for (int i = 0; i < 2048; i++) {
            var stack = new net.minecraft.world.item.ItemStack(Items.IRON_INGOT);
            stack.getOrCreateTag().putInt("variant", i);
            stock.put(AEItemKey.of(stack), BigInteger.ONE);
        }
        var encoded = new net.minecraft.world.item.ItemStack(Items.PAPER);
        var in = new net.minecraft.nbt.ListTag();
        in.add(GenericStack.writeTag(new GenericStack(primary, 1)));
        var out = new net.minecraft.nbt.ListTag();
        out.add(GenericStack.writeTag(new GenericStack(output, 1)));
        encoded.getOrCreateTag().put("in", in);
        encoded.getOrCreateTag().put("out", out);
        var nativePattern = new appeng.crafting.pattern.AEProcessingPattern(AEItemKey.of(encoded));
        var capture = capture(Map.of(output, List.of(nativePattern)), stock);
        var result = capture.plan(output, BigInteger.ONE, false);
        capture.validate();
        assertEquals(Map.of(primary, BigInteger.ONE), result.missing());
        assertTrue(result.used().isEmpty(), "Wrong NBT must never satisfy a native processing input");
        var field = NativeVmCapture.class.getDeclaredField("observations");
        field.setAccessible(true);
        int observations = ((Map<?, ?>) field.get(capture)).size();
        assertEquals(1, observations, "Only the primary semantic check should cross the live boundary");
    }

    @Test void exactProcessingLookupMatchesGenericInputsForFluidsNbtAndWideAmounts() {
        var requiredStack = new net.minecraft.world.item.ItemStack(Items.IRON_INGOT);
        requiredStack.getOrCreateTag().putInt("variant", 1);
        AEKey item = AEItemKey.of(requiredStack);
        requiredStack.getOrCreateTag().putInt("variant", 2);
        AEKey wrong = AEItemKey.of(requiredStack);
        AEKey fluid = appeng.api.stacks.AEFluidKey.of(net.minecraft.world.level.material.Fluids.WATER);
        AEKey output = AEItemKey.of(Items.DIAMOND);
        var encoded = new net.minecraft.world.item.ItemStack(Items.PAPER);
        var in = new net.minecraft.nbt.ListTag();
        in.add(GenericStack.writeTag(new GenericStack(item, 3)));
        in.add(GenericStack.writeTag(new GenericStack(fluid, 1000)));
        var out = new net.minecraft.nbt.ListTag();
        out.add(GenericStack.writeTag(new GenericStack(output, 1)));
        out.add(GenericStack.writeTag(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 2)));
        encoded.getOrCreateTag().put("in", in);
        encoded.getOrCreateTag().put("out", out);
        var nativePattern = new appeng.crafting.pattern.AEProcessingPattern(AEItemKey.of(encoded));
        var generic = mock(IPatternDetails.class);
        when(generic.getDefinition()).thenReturn(nativePattern.getDefinition());
        when(generic.getInputs()).thenReturn(nativePattern.getInputs());
        when(generic.getOutputs()).thenReturn(nativePattern.getOutputs());
        for (var order : List.of(BigInteger.ONE, BigInteger.TWO, BigInteger.valueOf(Long.MAX_VALUE),
                BigInteger.ONE.shiftLeft(63), BigInteger.TEN.pow(64))) {
            for (int filled : new int[]{0, 1, 2}) {
                var items = order.multiply(BigInteger.valueOf(3L * filled)).divide(BigInteger.TWO);
                var fluids = order.multiply(BigInteger.valueOf(1000L * filled)).divide(BigInteger.TWO);
                var stock = Map.of(item, items, fluid, fluids, wrong, BigInteger.TEN.pow(65));
                var optimized = capture(Map.of(output, List.of(nativePattern)), stock);
                var reference = capture(Map.of(output, List.of(generic)), stock);
                var actual = optimized.plan(output, order, false);
                var expected = reference.plan(output, order, false);
                optimized.validate();
                reference.validate();
                assertEquals(expected.requested(), actual.requested());
                assertEquals(expected.crafts(), actual.crafts());
                assertEquals(expected.used(), actual.used());
                assertEquals(expected.missing(), actual.missing());
                assertEquals(expected.emitted(), actual.emitted());
                assertEquals(expected.integerCharges(), actual.integerCharges());
                assertEquals(expected.stackCharges(), actual.stackCharges());
                assertEquals(items, actual.used().getOrDefault(item, BigInteger.ZERO));
                assertEquals(fluids, actual.used().getOrDefault(fluid, BigInteger.ZERO));
                assertEquals(order.multiply(BigInteger.valueOf(3)).subtract(items),
                        actual.missing().getOrDefault(item, BigInteger.ZERO));
                assertEquals(order.multiply(BigInteger.valueOf(1000)).subtract(fluids),
                        actual.missing().getOrDefault(fluid, BigInteger.ZERO));
                assertFalse(actual.used().containsKey(wrong));
            }
        }
    }

    private static Map<IPatternDetails, BigInteger> patternCounts(NativeVmCapture capture, VmQuantities result) {
        var counts = new LinkedHashMap<IPatternDetails, BigInteger>();
        result.crafts().forEach((id, amount) -> counts.put(capture.bindings().get(id), amount));
        return counts;
    }

    private static IPatternDetails pattern(AEKey output, AEKey key, long count) {
        var input = mock(IPatternDetails.IInput.class);
        when(input.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(key, 1)});
        when(input.getMultiplier()).thenReturn(count);
        when(input.isValid(any(), any())).thenAnswer(call -> key.equals(call.getArgument(0)));
        var pattern = mock(IPatternDetails.class);
        when(pattern.getDefinition()).thenReturn(AEItemKey.of(Items.PAPER));
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[]{input});
        when(pattern.getOutputs()).thenReturn(new GenericStack[]{new GenericStack(output, 1)});
        return pattern;
    }

    private static NativeVmCapture capture(Map<AEKey, List<IPatternDetails>> recipes, Map<AEKey, BigInteger> stock) {
        var service = mock(ICraftingService.class);
        recipes.forEach((key, patterns) -> when(service.getCraftingFor(key)).thenReturn(patterns));
        return capture(service, stock);
    }

    private static NativeVmCapture capture(ICraftingService service, Map<AEKey, BigInteger> stock) {
        var grid = mock(IGrid.class);
        when(grid.getCraftingService()).thenReturn(service);
        var level = mock(Level.class);
        var server = mock(MinecraftServer.class);
        when(level.getServer()).thenReturn(server);
        when(server.isSameThread()).thenReturn(true);
        return new NativeVmCapture(grid, level, new VmAccounting() {
            public BigInteger maximumCount() { return BigInteger.ONE.shiftLeft(4096); }
            public Stock exactStock(IGrid ignored) { return new Stock(stock, true, stock.keySet()); }
            public ICraftingPlan wideResult(IGrid ignored, Level world, NativeVmResult result) { throw new AssertionError(); }
        });
    }
}
