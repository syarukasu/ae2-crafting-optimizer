package com.syaru.ae2craftingoptimizer.engine.vector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class LongClampedProgressProjectionTest {
    @Test
    void clampsOnlyTheDisplayFacade() {
        assertEquals(
                Long.MAX_VALUE,
                LongClampedProgressProjection.clamp(
                        BigInteger.TEN.pow(1_024)));
        assertEquals(
                4_096L,
                LongClampedProgressProjection.clamp(
                        BigInteger.valueOf(4_096L)));
    }

    @Test
    void hugeCountsMoveOnEveryLogicalTick() {
        BigInteger exact = BigInteger.TEN.pow(1_024);
        long previous = Long.MAX_VALUE;
        // 20段の演出中、Long.MAX_VALUE表示が各段で必ず減少することを確認する。
        for (int tick = 1; tick <= 20; tick++) {
            long current = LongClampedProgressProjection.remaining(
                    exact,
                    tick,
                    20);
            assertTrue(current < previous);
            previous = current;
        }
        assertEquals(0L, previous);
    }

    @Test
    void signedLongCountsUseTheirActualFacade() {
        assertEquals(
                1_000L,
                LongClampedProgressProjection.remaining(
                        BigInteger.valueOf(1_000L),
                        0,
                        20));
        assertEquals(
                500L,
                LongClampedProgressProjection.remaining(
                        BigInteger.valueOf(1_000L),
                        10,
                        20));
        assertEquals(
                0L,
                LongClampedProgressProjection.remaining(
                        BigInteger.valueOf(1_000L),
                        20,
                        20));
    }

    @Test
    void smallCountsRemainVisibleUntilTheirFinalShare() {
        assertEquals(
                3L,
                LongClampedProgressProjection.remaining(
                        BigInteger.valueOf(3L),
                        1,
                        20));
        assertEquals(
                1L,
                LongClampedProgressProjection.remaining(
                        BigInteger.valueOf(3L),
                        19,
                        20));
    }

    @Test
    void rejectsInvalidProjectionInputs() {
        assertThrows(
                IllegalArgumentException.class,
                () -> LongClampedProgressProjection.remaining(
                        BigInteger.ONE.negate(),
                        0,
                        20));
        assertThrows(
                IllegalArgumentException.class,
                () -> LongClampedProgressProjection.progress(21, 20));
    }
}
