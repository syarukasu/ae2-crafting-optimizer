package com.syaru.ae2craftingoptimizer.engine.craftingtable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ExactMutationReconcilerTest {
    @Test
    void returnsEveryKeyWhenNothingWasApplied() {
        Map<String, BigInteger> before =
                ordered(
                        10L,
                        20L);
        Map<String, BigInteger> after =
                ordered(
                        7L,
                        15L);

        assertEquals(
                ordered(
                        3L,
                        5L),
                ExactMutationReconciler.remainingAmounts(
                                before,
                                after,
                                before)
                        .orElseThrow());
    }

    @Test
    void returnsOnlyKeysThatWereNotAppliedBeforeShutdown() {
        Map<String, BigInteger> before =
                ordered(
                        10L,
                        20L);
        Map<String, BigInteger> after =
                ordered(
                        7L,
                        15L);
        Map<String, BigInteger> mixed =
                ordered(
                        7L,
                        20L);

        assertEquals(
                Map.of(
                        "second",
                        BigInteger.valueOf(
                                5L)),
                ExactMutationReconciler.remainingAmounts(
                                before,
                                after,
                                mixed)
                        .orElseThrow());
    }

    @Test
    void returnsEmptyMapWhenEveryKeyWasApplied() {
        Map<String, BigInteger> before =
                ordered(
                        10L,
                        20L);
        Map<String, BigInteger> after =
                ordered(
                        7L,
                        15L);

        assertTrue(
                ExactMutationReconciler.remainingAmounts(
                                before,
                                after,
                                after)
                        .orElseThrow()
                        .isEmpty());
    }

    @Test
    void rejectsAValueThatMatchesNeitherSnapshot() {
        Map<String, BigInteger> before =
                ordered(
                        10L,
                        20L);
        Map<String, BigInteger> after =
                ordered(
                        7L,
                        15L);
        Map<String, BigInteger> changedByAnotherOwner =
                ordered(
                        8L,
                        20L);

        assertTrue(
                ExactMutationReconciler.remainingAmounts(
                                before,
                                after,
                                changedByAnotherOwner)
                        .isEmpty());
    }

    private static Map<String, BigInteger> ordered(
            long first,
            long second) {
        Map<String, BigInteger> result =
                new LinkedHashMap<>();
        result.put(
                "first",
                BigInteger.valueOf(
                        first));
        result.put(
                "second",
                BigInteger.valueOf(
                        second));
        return result;
    }
}
