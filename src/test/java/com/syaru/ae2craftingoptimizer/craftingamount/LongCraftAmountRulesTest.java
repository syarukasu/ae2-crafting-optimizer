package com.syaru.ae2craftingoptimizer.craftingamount;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class LongCraftAmountRulesTest {
    /** AE2のFluid/Chemical GUIで1 Bを内部1000単位へ変換する倍率。 */
    private static final int FLUID_AMOUNT_PER_BUCKET = 1000;

    @Test
    void preservesAe2PathThroughIntegerMaximum() {
        assertFalse(LongCraftAmountRules.isValidExtendedRequest(Long.MIN_VALUE));
        assertFalse(LongCraftAmountRules.isValidExtendedRequest(0L));
        assertFalse(LongCraftAmountRules.usesExtendedPath(1L));
        assertFalse(LongCraftAmountRules.usesExtendedPath(Integer.MAX_VALUE));
        assertTrue(LongCraftAmountRules.usesExtendedPath((long) Integer.MAX_VALUE + 1L));
        assertTrue(LongCraftAmountRules.usesExtendedPath(Long.MAX_VALUE));
    }

    @Test
    void acceptsLongMaximumWithoutNarrowing() {
        var exact = LongCraftAmountRules.toExactExternalAmount(
                new BigDecimal("9223372036854775807"),
                1);

        assertTrue(exact.isPresent());
        assertEquals(Long.MAX_VALUE, exact.getAsLong());
    }

    @Test
    void acceptsLongMaximumThroughFluidUnitConversion() {
        var exact = LongCraftAmountRules.toExactExternalAmount(
                new BigDecimal("9223372036854775.807"),
                FLUID_AMOUNT_PER_BUCKET);

        assertTrue(exact.isPresent());
        assertEquals(Long.MAX_VALUE, exact.getAsLong());
    }

    @Test
    void rejectsValuesAboveLongInsteadOfWrapping() {
        assertTrue(LongCraftAmountRules.toExactExternalAmount(
                new BigDecimal("9223372036854775808"),
                1).isEmpty());
        assertTrue(LongCraftAmountRules.toExactExternalAmount(
                new BigDecimal("18446744073709551617"),
                1).isEmpty());
    }

    @Test
    void subtractsStoredAmountWithoutOverflow() {
        assertEquals(
                Long.MAX_VALUE,
                LongCraftAmountRules.subtractAvailable(Long.MAX_VALUE, 0L));
        assertEquals(
                Long.MAX_VALUE - 1L,
                LongCraftAmountRules.subtractAvailable(Long.MAX_VALUE, 1L));
        assertEquals(
                0L,
                LongCraftAmountRules.subtractAvailable(Long.MAX_VALUE, Long.MAX_VALUE));
        assertEquals(
                Long.MAX_VALUE,
                LongCraftAmountRules.subtractAvailable(Long.MAX_VALUE, -1L));
    }
}
