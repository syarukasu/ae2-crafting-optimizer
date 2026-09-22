package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.crafting.inv.CraftingSimulationState;
import com.ae2vm.addon.vm.CraftingBytecode;
import com.ae2vm.addon.vm.CraftingVM;
import com.ae2vm.addon.vm.Opcode;
import java.math.BigInteger;
import java.util.List;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Executes the real upstream interpreter; no Minecraft server, world or mutable ME grid. */
class RealVmBoundaryProbeTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        ReusableByproductAe2OracleTest.bootstrap();
        com.syaru.ae2craftingoptimizer.testsupport.TestKeyTypes.initialize();
    }

    @Test
    void ordinaryMultiplicationUsesTheActualVm() throws Exception {
        assertEquals(BigInteger.valueOf(32), multiply(2, 16, false));
    }

    @Test
    void powerOfTwoOverflowIsAnExplicitIntegrationBlocker() throws Exception {
        BigInteger expected = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO);
        BigInteger actual = multiply(Long.MAX_VALUE, 2, false);
        checkBoundary("power_of_two_mul", expected, actual);
    }

    @Test
    void promotedOperandMustNotBeNarrowedByNextInstruction() throws Exception {
        BigInteger expected = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(3)).add(BigInteger.ONE);
        checkBoundary("promoted_mul_then_add", expected, multiply(Long.MAX_VALUE, 3, true));
    }

    @Test
    void exactOpcodeMatrixIncludesSmallMaxAndWideIntermediates() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(Boolean.getBoolean("aco.vm.requireExact"));
        for (long a : new long[] {0, 1, 2, Long.MAX_VALUE}) {
            for (long b : new long[] {0, 1, 2, 16, Long.MAX_VALUE}) {
                assertEquals(BigInteger.valueOf(a).multiply(BigInteger.valueOf(b)), multiply(a, b, false));
            }
        }
        var builder = builder();
        builder.emitPushLong(Long.MAX_VALUE);
        builder.emitPushItem(0, 16);
        builder.emitPushLong(3);
        builder.emit(Opcode.DIV_ROUNDUP);
        builder.emitPushLong(7);
        builder.emit(Opcode.SUB);
        BigInteger total = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(16));
        assertEquals(total.add(BigInteger.TWO).divide(BigInteger.valueOf(3)).subtract(BigInteger.valueOf(7)), evaluate(builder));
    }

    @Test
    void legacyCountConversionRejectsOverflowRatherThanClamping() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(Boolean.getBoolean("aco.vm.requireExact"));
        var method = CraftingVM.class.getDeclaredMethod("toLongSafe", BigInteger.class, String.class);
        method.setAccessible(true);
        for (long value : new long[] {0, 1, 2, Long.MAX_VALUE}) {
            assertEquals(value, method.invoke(null, BigInteger.valueOf(value), "probe"));
        }
        for (BigInteger value : List.of(BigInteger.valueOf(-1), BigInteger.ONE.shiftLeft(63), BigInteger.TEN.pow(64))) {
            var failure = assertThrows(java.lang.reflect.InvocationTargetException.class,
                    () -> method.invoke(null, value, "probe"));
            assertInstanceOf(ArithmeticException.class, failure.getCause());
        }
    }

    private static void checkBoundary(String boundary, BigInteger expected, BigInteger actual) {
        System.out.printf("VM_BOUNDARY %s expected=%s actual=%s exact=%s%n", boundary, expected, actual, expected.equals(actual));
        if (Boolean.getBoolean("aco.vm.requireExact")) {
            assertEquals(expected, actual, "Must pass before production exact adoption");
        } else {
            assertNotEquals(expected, actual, "Pinned upstream defect changed; review the integration gate");
        }
    }

    private static BigInteger multiply(long a, long b, boolean addOne) throws Exception {
        var builder = builder();
        builder.emitPushLong(a);
        builder.emitPushLong(b);
        builder.emit(Opcode.MUL);
        if (addOne) {
            builder.emitPushLong(1);
            builder.emit(Opcode.ADD);
        }
        return evaluate(builder);
    }

    private static CraftingBytecode.Builder builder() {
        var builder = new CraftingBytecode.Builder();
        builder.setOutput(builder.addConstant(AEItemKey.of(Items.IRON_INGOT)), 1);
        return builder;
    }

    private static BigInteger evaluate(CraftingBytecode.Builder builder) throws Exception {
        var vm = new CraftingVM(new Object(), key -> null);
        vm.execute(builder.build(), new CraftingSimulationState() {
            protected long simulateExtractParent(AEKey key, long amount) { return 0; }
            protected Iterable<AEKey> findFuzzyParent(AEKey input) { return List.of(); }
        });
        var stack = CraftingVM.class.getDeclaredField("stack");
        stack.setAccessible(true);
        var pointer = CraftingVM.class.getDeclaredField("sp");
        pointer.setAccessible(true);
        return ((BigInteger[]) stack.get(vm))[pointer.getInt(vm) - 1];
    }
}
