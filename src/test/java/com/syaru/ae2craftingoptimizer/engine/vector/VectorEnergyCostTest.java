package com.syaru.ae2craftingoptimizer.engine.vector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

final class VectorEnergyCostTest {
    @Test
    void chargesTwentyPatternNodesOnceRegardlessOfOrderQuantity() {
        // 初期値6,400 AEをmicro-AEへ変換した既存Config値。
        BigInteger perNode = BigInteger.valueOf(6_400_000_000L);

        BigInteger total = VectorEnergyCost.forPatternNodes(
                20,
                perNode,
                4096);

        assertEquals(
                BigInteger.valueOf(128_000_000_000L),
                total);
    }

    @Test
    void rejectsInvalidNodeCountsAndMagnitudeOverflow() {
        assertThrows(
                IllegalArgumentException.class,
                () -> VectorEnergyCost.forPatternNodes(
                        0,
                        BigInteger.ONE,
                        64));
        assertThrows(
                ArithmeticException.class,
                () -> VectorEnergyCost.forPatternNodes(
                        2,
                        BigInteger.ONE.shiftLeft(63),
                        64));
    }
}
