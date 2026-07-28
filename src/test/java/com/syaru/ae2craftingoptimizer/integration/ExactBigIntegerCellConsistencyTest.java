package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ExactBigIntegerCellConsistencyTest {
    @AfterEach
    void clearSharedState() {
        ExactBigIntegerCellConsistency.clear();
    }

    @Test
    void derivesTheInitialTotalFromTheSharedStorageMap() {
        Map<String, BigInteger> amounts =
                new LinkedHashMap<>();
        amounts.put(
                "first",
                BigInteger.valueOf(7L));
        amounts.put(
                "second",
                BigInteger.valueOf(11L));

        assertEquals(
                BigInteger.valueOf(18L),
                ExactBigIntegerCellConsistency.authoritativeTotal(
                        amounts));
    }

    @Test
    void everyWrapperReusesTheRecordedSharedMapTotal() {
        Map<String, BigInteger> amounts =
                new LinkedHashMap<>();
        amounts.put(
                "shared",
                BigInteger.TEN);
        BigInteger updated =
                BigInteger.TEN.pow(
                        64);
        amounts.put(
                "shared",
                updated);
        ExactBigIntegerCellConsistency.record(
                amounts,
                updated);

        assertEquals(
                updated,
                ExactBigIntegerCellConsistency.authoritativeTotal(
                        amounts));
    }

    @Test
    void rejectsCorruptNonPositiveStoredAmounts() {
        Map<String, BigInteger> amounts =
                new LinkedHashMap<>();
        amounts.put(
                "invalid",
                BigInteger.ZERO);

        assertThrows(
                IllegalStateException.class,
                () -> ExactBigIntegerCellConsistency.authoritativeTotal(
                        amounts));
    }
}
