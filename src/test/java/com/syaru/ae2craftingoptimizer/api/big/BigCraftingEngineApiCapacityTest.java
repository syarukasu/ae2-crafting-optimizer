package com.syaru.ae2craftingoptimizer.api.big;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.engine.BigCountMath;
import java.lang.reflect.Method;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class BigCraftingEngineApiCapacityTest {
    @Test
    void returnsExactConfiguredBinaryLimit() {
        assertEquals(
                BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE),
                BigCraftingEngineApi.maximumSupportedAmount(64));
    }

    @Test
    void clampsMaximumBitsToDecimalImplementationLimit() {
        assertEquals(
                BigCountMath.hardMaximumValue(),
                BigCraftingEngineApi.maximumSupportedAmount(
                        BigCountMath.HARD_MAXIMUM_BITS));
    }

    @Test
    void rejectsBitsOutsideAcoBoundary() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BigCraftingEngineApi.maximumSupportedAmount(0));
        assertThrows(
                IllegalArgumentException.class,
                () -> BigCraftingEngineApi.maximumSupportedAmount(
                        BigCountMath.HARD_MAXIMUM_BITS + 1));
    }

    @Test
    void exposesAeKeyLedgerFactoryWithoutInternalCodecParameter() throws Exception {
        Method factory = BigCraftingEngineApi.class.getMethod("createAeKeyAmountLedger");

        assertEquals(BigIntegerAmountLedger.class, factory.getReturnType());
        assertEquals(0, factory.getParameterCount());
        assertTrue(BigCraftingEngineApi.AMOUNT_LEDGER_API_VERSION >= 2);
        // ジェネリック戻り値にもAEKeyが残り、アドオン側の型契約を文書化できる。
        assertTrue(factory.getGenericReturnType().getTypeName().contains(AEKey.class.getName()));
    }
}
