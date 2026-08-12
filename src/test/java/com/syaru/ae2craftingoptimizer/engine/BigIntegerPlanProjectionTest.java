package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BigIntegerPlanProjectionTest {
    @Test
    void longBoundaryIsExactAndOnlyOverflowIsSaturated() {
        BigInteger maximum = BigInteger.valueOf(Long.MAX_VALUE);

        assertEquals(Long.MAX_VALUE, BigIntegerPlanProjection.saturatedLong(maximum));
        assertEquals(
                Long.MAX_VALUE,
                BigIntegerPlanProjection.saturatedLong(maximum.add(BigInteger.ONE)));
        assertEquals(42L, BigIntegerPlanProjection.saturatedLong(BigInteger.valueOf(42L)));
    }

    @Test
    void positiveCountCopyRejectsZeroAndNegativeValues() {
        assertEquals(
                Map.of("pattern", BigInteger.ONE),
                BigIntegerPlanProjection.immutablePositiveCounts(
                        Map.of("pattern", BigInteger.ONE),
                        "patterns"));
        assertThrows(
                IllegalArgumentException.class,
                () -> BigIntegerPlanProjection.immutablePositiveCounts(
                        Map.of("pattern", BigInteger.ZERO),
                        "patterns"));
        assertThrows(
                IllegalArgumentException.class,
                () -> BigIntegerPlanProjection.immutablePositiveCounts(
                        Map.of("pattern", BigInteger.valueOf(-1L)),
                        "patterns"));
    }
}
