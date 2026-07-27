package com.syaru.ae2craftingoptimizer.optimization;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExactBatchInputLimiterTest {
    @Test
    void aggregatesNineIdenticalCraftingSlotsBeforeLimiting() {
        /*
         * 9入力slotを別々に照会すると81個を9回使えるように誤判定する。
         * キー別合計9を使えば、正しい実行上限は9回である。
         */
        long limited = ExactBatchInputLimiter.limit(
                100L,
                Map.of("iron", BigInteger.valueOf(9L)),
                (key, requested) -> 81L);

        assertEquals(9L, limited);
    }

    @Test
    void keepsOneCraftOnTheSameExactPath() {
        long limited = ExactBatchInputLimiter.limit(
                1L,
                Map.of("iron", BigInteger.valueOf(9L)),
                (key, requested) -> 9L);

        assertEquals(1L, limited);
    }

    @Test
    void probesOnlyARepresentableSignedLongAmount() {
        long limited = ExactBatchInputLimiter.limit(
                Long.MAX_VALUE,
                Map.of("iron", BigInteger.valueOf(9L)),
                (key, requested) -> {
                    assertEquals(
                            Long.MAX_VALUE / 9L * 9L,
                            requested);
                    return requested;
                });

        assertEquals(Long.MAX_VALUE / 9L, limited);
    }

    @Test
    void rejectsInvalidInventoryResponses() {
        long limited = ExactBatchInputLimiter.limit(
                10L,
                Map.of("iron", BigInteger.ONE),
                (key, requested) -> -1L);

        assertEquals(0L, limited);
    }
}
