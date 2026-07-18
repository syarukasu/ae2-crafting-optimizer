package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
