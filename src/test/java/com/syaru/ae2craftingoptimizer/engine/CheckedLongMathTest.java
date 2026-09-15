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
    void indexedOperationsKeepExactArithmeticAndLazyFailureContext() {
        assertEquals(
                Long.MAX_VALUE,
                CheckedLongMath.addIndexed(
                        Long.MAX_VALUE - 1L,
                        1L,
                        "test/add",
                        7));
        assertEquals(
                Long.MAX_VALUE,
                CheckedLongMath.multiplyIndexed(
                        Long.MAX_VALUE,
                        1L,
                        "test/multiply",
                        8));
        assertEquals(
                (Long.MAX_VALUE / 2L) + 1L,
                CheckedLongMath.ceilDivIndexed(
                        Long.MAX_VALUE,
                        2L,
                        "test/ceil",
                        9));

        CountOverflowException overflow = assertThrows(
                CountOverflowException.class,
                () -> CheckedLongMath.multiplyIndexed(
                        Long.MAX_VALUE,
                        2L,
                        "test/multiply",
                        8));
        assertTrue(overflow.getMessage().contains("test/multiply/8"));
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
