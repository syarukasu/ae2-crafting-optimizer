package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

final class BigCountMathAqeLimitTest {
    @Test
    void acceptsCountsAboveTenToSixtyFourAtTheAqeLimit() {
        BigInteger aboveLegacyLimit = BigInteger.TEN.pow(65);

        assertEquals(
                aboveLegacyLimit,
                BigCountMath.requireMaximumBits(
                        aboveLegacyLimit,
                        "above legacy limit",
                        BigCountMath.HARD_MAXIMUM_BITS));
    }

    @Test
    void rejectsTheFirstValueAboveTheAqeDecimalLimit() {
        BigInteger aboveAqeLimit = BigCountMath.hardMaximumValue().add(BigInteger.ONE);

        assertThrows(
                IllegalArgumentException.class,
                () -> BigCountMath.requireMaximumBits(
                        aboveAqeLimit,
                        "above AQE limit",
                        BigCountMath.HARD_MAXIMUM_BITS));
    }
}
