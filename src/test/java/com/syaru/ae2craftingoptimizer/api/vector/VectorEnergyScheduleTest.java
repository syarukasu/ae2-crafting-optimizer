package com.syaru.ae2craftingoptimizer.api.vector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class VectorEnergyScheduleTest {
    @Test
    void distributesRemainderWithoutChangingTheExactTotal() {
        VectorEnergySchedule schedule =
                VectorEnergySchedule.divide(BigInteger.valueOf(203L), 20);
        BigInteger observed = BigInteger.ZERO;

        // 余り3は先頭3tickへ一ずつ入り、残り17tickは基準値10になる。
        for (int tick = 0; tick < 20; tick++) {
            BigInteger expected = tick < 3
                    ? BigInteger.valueOf(11L)
                    : BigInteger.TEN;
            assertEquals(expected, schedule.microAeForTick(tick));
            observed = observed.add(schedule.microAeForTick(tick));
        }

        assertEquals(BigInteger.valueOf(203L), observed);
    }

    @Test
    void handlesAOneThousandDigitEnergyTotalInConstantTickCount() {
        BigInteger total =
                BigInteger.TEN.pow(1_000).subtract(BigInteger.ONE);
        VectorEnergySchedule schedule =
                VectorEnergySchedule.divide(total, 20);
        BigInteger observed = BigInteger.ZERO;

        // 値の桁数に関係なく、実行時の反復数はdurationTicksの20回だけである。
        for (int tick = 0; tick < schedule.totalTicks(); tick++) {
            observed = observed.add(schedule.microAeForTick(tick));
        }

        assertEquals(total, observed);
        assertThrows(
                IndexOutOfBoundsException.class,
                () -> schedule.microAeForTick(20));
    }

    @Test
    void sumsAnActiveRangeWithoutPerStageIteration() {
        VectorEnergySchedule schedule =
                VectorEnergySchedule.divide(BigInteger.valueOf(203L), 20);

        assertEquals(
                BigInteger.valueOf(203L),
                schedule.microAeForRange(0, 20));
        assertEquals(
                BigInteger.valueOf(53L),
                schedule.microAeForRange(0, 5));
        assertEquals(
                BigInteger.valueOf(50L),
                schedule.microAeForRange(5, 5));
        assertThrows(
                IndexOutOfBoundsException.class,
                () -> schedule.microAeForRange(19, 2));
    }
}
