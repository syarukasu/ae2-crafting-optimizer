package com.syaru.ae2craftingoptimizer.optimization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class CraftingCalculationDiagnosticsTest {
    @Test
    void exactAmountFormattingIsBoundedByMagnitudeNotDecimalLength() {
        BigInteger huge = BigInteger.ONE.shiftLeft(54_425);

        String formatted = CraftingCalculationDiagnostics.formatExactAmount(huge);

        assertEquals("sign=1,bits=54426", formatted);
        assertFalse(formatted.contains(huge.toString()));
        assertEquals("42", CraftingCalculationDiagnostics.formatExactAmount(BigInteger.valueOf(42L)));
    }

    @Test
    void calculationIdsRemainPositiveAndMonotonic() {
        long first = CraftingCalculationDiagnostics.nextCalculationId();
        long second = CraftingCalculationDiagnostics.nextCalculationId();

        assertTrue(first > 0L);
        assertEquals(first + 1L, second);
    }
}
