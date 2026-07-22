package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CheckedLongMathTest {
    @Test
    void handlesMaximumLongWithoutOverflow() {
        assertEquals(Long.MAX_VALUE, CheckedLongMath.add(Long.MAX_VALUE - 1L, 1L, "test"));
        assertEquals(Long.MAX_VALUE, CheckedLongMath.multiply(Long.MAX_VALUE, 1L, "test"));
    }

    @Test
    void rejectsOverflowInsteadOfWrapping() {
        assertThrows(CountOverflowException.class, () -> CheckedLongMath.add(Long.MAX_VALUE, 1L, "test"));
        assertThrows(CountOverflowException.class, () -> CheckedLongMath.multiply(Long.MAX_VALUE, 2L, "test"));
    }

    @Test
    void ceilDivisionDoesNotUseOverflowingAddition() {
        assertEquals(4L, CheckedLongMath.ceilDiv(10L, 3L, "test"));
        assertEquals((Long.MAX_VALUE / 2L) + 1L, CheckedLongMath.ceilDiv(Long.MAX_VALUE, 2L, "test"));
    }

    @Test
    void detectsDistinctLongMaximumInputsWithoutAddingThem() {
        assertTrue(CheckedLongMath.sumExceedsLong(
                Map.of("gas_a", Long.MAX_VALUE, "gas_b", Long.MAX_VALUE),
                "test"));
        assertFalse(CheckedLongMath.sumExceedsLong(
                Map.of("gas_a", Long.MAX_VALUE - 1L, "gas_b", 1L),
                "test"));
    }

    @Test
    void detectsBoundaryAcrossInventoryAndMissingCounters() {
        assertTrue(CheckedLongMath.sumExceedsLong(
                List.of(
                        Map.of("gas_a", Long.MAX_VALUE),
                        Map.of(),
                        Map.of("gas_b", Long.MAX_VALUE)),
                "test"));
    }
}
