package com.syaru.ae2craftingoptimizer.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class BigAmountFormatterTest {
    @Test
    void formatsPastLongWithoutSaturatingAtExa() {
        assertEquals(
                "9.22e18",
                BigAmountFormatter.formatCompact(
                        BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)));
    }

    @Test
    void formatsRuntimeMaximumUnambiguously() {
        assertEquals(
                "1e64",
                BigAmountFormatter.formatCompact(BigInteger.TEN.pow(64)));
    }
}
