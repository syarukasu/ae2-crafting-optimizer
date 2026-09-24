package com.ae2vm.addon.nativeengine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.ae2vm.addon.compiler.PatternCompiler;
import com.ae2vm.addon.vm.CraftingBytecode;
import com.ae2vm.addon.vm.Opcode;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class UpstreamCompilerExactQuantitiesTest {
    @BeforeAll static void bootstrap() throws Exception { NativeVmCaptureTest.bootstrap(); }
    @AfterEach void clearCompiler() { PatternCompiler.clearCache(); PatternCompiler.clearFuzzyGroups(); }

    @Test void originalCompilerKeepsHugeOrdersAndExactCeiling() {
        var pattern = pattern(1, 1, 7);
        for (var request : List.of(BigInteger.ONE, BigInteger.TWO, BigInteger.valueOf(Long.MAX_VALUE),
                BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE), BigInteger.TEN.pow(1024))) {
            var code = PatternCompiler.compileRequest(pattern, request);
            assertEquals(request, code.getExactOutputAmountPerCraft());
            assertSame(pattern, code.getPatternPool()[0]);
            assertEquals(List.of(request.add(BigInteger.valueOf(6)).divide(BigInteger.valueOf(7))), literals(code));
            if (request.bitLength() > 63) {
                assertThrows(ArithmeticException.class, code::getOutputAmountPerCraft);
                assertThrows(ArithmeticException.class, code::getOutputStack);
            }
        }
    }

    @Test void originalCompilerDoesNotOverflowPerCraftFluidOrItemCoefficients() {
        for (var amount : List.of(1L, 1_000L, Long.MAX_VALUE)) {
            var pattern = pattern(Long.MAX_VALUE, amount, 1);
            PatternCompiler.compileIfAbsent(pattern);
            var code = PatternCompiler.getCompiled(pattern);
            var expected = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(amount));
            assertTrue(literals(code).contains(expected));
            assertSame(pattern, code.getPatternPool()[0]);
        }
    }

    @Test void quantityPoolIdentityIncludesValuesNotJustIdenticalInstructionIndices() {
        var a = literal(BigInteger.ONE.shiftLeft(80));
        var b = literal(BigInteger.ONE.shiftLeft(81));
        assertArrayEquals(a.getCode(), b.getCode());
        assertNotEquals(a, b);
        assertEquals(a, literal(BigInteger.ONE.shiftLeft(80)));
        var copy = a.getQuantityPool();
        copy[0] = BigInteger.ZERO;
        assertEquals(BigInteger.ONE.shiftLeft(80), a.getQuantity(0));
    }

    @Test void metadataIsPartOfBytecodeIdentity() {
        var key = AEItemKey.of(Items.DIAMOND);
        var a = new CraftingBytecode(new AEItemKey[]{key}, new IPatternDetails[0], new byte[]{(byte)255}, 0, 1);
        var b = new CraftingBytecode(new AEItemKey[]{key}, new IPatternDetails[0], new byte[]{(byte)255}, 0, 2);
        assertNotEquals(a, b);
    }

    @Test void longEntryPointRemainsExactAndInvalidInputsAreRejected() {
        var pattern = pattern(1, 1, 2);
        var request = PatternCompiler.compileRequest(pattern, Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, request.getOutputAmountPerCraft());
        assertEquals(List.of(BigInteger.ONE.shiftLeft(62)), literals(request));
        assertThrows(IllegalArgumentException.class, () -> PatternCompiler.compileRequest(pattern, BigInteger.ZERO));
        assertThrows(IllegalArgumentException.class, () -> PatternCompiler.compileIfAbsent(pattern(0, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> PatternCompiler.compileIfAbsent(pattern(1, 0, 1)));
    }

    @Test void encodedIndicesCannotWrapAroundToAnotherQuantity() {
        var builder = new CraftingBytecode.Builder();
        assertThrows(IllegalArgumentException.class, () -> builder.emitShort(-1));
        assertThrows(IllegalArgumentException.class, () -> builder.emitShort(65_536));
    }

    private static IPatternDetails pattern(long multiplier, long amount, long output) {
        var input = mock(IPatternDetails.IInput.class);
        when(input.getMultiplier()).thenReturn(multiplier);
        when(input.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(AEItemKey.of(Items.IRON_INGOT), amount)});
        var pattern = mock(IPatternDetails.class);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[]{input});
        var product = new GenericStack(AEItemKey.of(Items.DIAMOND), output);
        when(pattern.getOutputs()).thenReturn(new GenericStack[]{product});
        when(pattern.getPrimaryOutput()).thenReturn(product);
        return pattern;
    }

    private static CraftingBytecode literal(BigInteger quantity) {
        var builder = new CraftingBytecode.Builder();
        builder.setOutput(builder.addConstant(AEItemKey.of(Items.DIAMOND)), BigInteger.ONE);
        builder.emitPushAmount(quantity);
        return builder.build();
    }

    private static List<BigInteger> literals(CraftingBytecode code) {
        List<BigInteger> values = new ArrayList<>();
        var buffer = ByteBuffer.wrap(code.getCode());
        while (buffer.hasRemaining()) {
            switch (Opcode.fromCode(buffer.get())) {
                case PUSH_LONG -> values.add(BigInteger.valueOf(buffer.getLong()));
                case PUSH_BIG_INTEGER -> values.add(code.getQuantity(Short.toUnsignedInt(buffer.getShort())));
                case PUSH_ITEM -> { buffer.getShort(); values.add(BigInteger.valueOf(buffer.getLong())); }
                case EXTRACT_INGREDIENT, RECORD_OUTPUT, RECORD_INGREDIENT, RECORD_MISSING, RECORD_PATTERN,
                        CALL, CALL_BY_KEY, INSERT_OUTPUT, CATALYST_SEED, DURABILITY_TOOL, REQUEST_INPUT -> buffer.getShort();
                default -> { }
            }
        }
        return values;
    }
}
