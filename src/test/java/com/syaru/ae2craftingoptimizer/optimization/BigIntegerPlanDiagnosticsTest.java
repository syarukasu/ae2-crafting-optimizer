package com.syaru.ae2craftingoptimizer.optimization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BigIntegerPlanDiagnosticsTest {
    @AfterEach
    void resetDiagnostics() {
        BigIntegerPlanDiagnostics.reset();
    }

    @Test
    void recordsExactDeclineReasonForStats() {
        BigIntegerPlanDiagnostics.record(
                BigIntegerPlanDeclineReason.ARITHMETIC_FAILURE,
                "minecraft:iron",
                BigInteger.TEN.pow(30),
                12L,
                13L,
                "overflow in exact planner");

        assertTrue(BigIntegerPlanDiagnostics.summaryLines().stream()
                .anyMatch(line -> line.contains("ARITHMETIC_FAILURE") && line.endsWith(": 1")));
    }

    @Test
    void formatsHugeRequestsWithoutExpandingEveryDecimalDigit() {
        BigInteger huge = BigInteger.TEN.pow(16_384);

        String formatted = BigIntegerPlanDiagnostics.formatRequestedAmount(huge);

        assertTrue(formatted.startsWith("sign=1,bits="));
        assertFalse(formatted.contains(huge.toString()));
        assertEquals("9223372036854775807", BigIntegerPlanDiagnostics.formatRequestedAmount(
                BigInteger.valueOf(Long.MAX_VALUE)));
    }
}
