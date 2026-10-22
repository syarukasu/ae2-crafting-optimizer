package com.syaru.ae2craftingoptimizer.engine.craftingtable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Issue #87: 物理取引とEscrowが共有するexact数量Map契約を固定する。 */
class ExactCountMapTest {
    /** 2件上限の境界内・境界超過を小さいfixtureで検査する。 */
    private static final int TWO_KEYS = 2;

    /** longを十分超えるexact値を軽量に検査する。 */
    private static final int LARGE_DECIMAL_DIGITS = 128;

    @Test
    void mutablePositiveCopyPreservesOrderAndExactAmounts() {
        BigInteger beyondLong = BigInteger.TEN.pow(LARGE_DECIMAL_DIGITS);
        LinkedHashMap<String, BigInteger> source = new LinkedHashMap<>();
        source.put("first", beyondLong);
        source.put("second", BigInteger.ONE);

        Map<String, BigInteger> copy =
                ExactCountMap.mutablePositiveCopy(source, "source");
        source.clear();

        assertEquals(java.util.List.of("first", "second"), new ArrayList<>(copy.keySet()));
        assertEquals(beyondLong, copy.get("first"));
        copy.put("third", BigInteger.TWO);
        assertEquals(BigInteger.TWO, copy.get("third"));
    }

    @Test
    void immutablePositiveCopyPreservesEmptyAndMaximumKeyContracts() {
        Map<String, BigInteger> empty = ExactCountMap.immutablePositiveCopy(
                Map.of(),
                "empty",
                true,
                TWO_KEYS);
        assertTrue(empty.isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> empty.put("key", BigInteger.ONE));

        Map<String, BigInteger> tooMany = new LinkedHashMap<>();
        tooMany.put("one", BigInteger.ONE);
        tooMany.put("two", BigInteger.ONE);
        tooMany.put("three", BigInteger.ONE);
        assertThrows(
                IllegalArgumentException.class,
                () -> ExactCountMap.immutablePositiveCopy(
                        tooMany,
                        "counts",
                        true,
                        TWO_KEYS));
        assertThrows(
                IllegalArgumentException.class,
                () -> ExactCountMap.immutablePositiveCopy(
                        Map.of(),
                        "required",
                        false,
                        TWO_KEYS));
    }

    @Test
    void positiveCopiesRejectZeroNegativeAndNullAmounts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ExactCountMap.mutablePositiveCopy(
                        Map.of("zero", BigInteger.ZERO),
                        "counts"));
        assertThrows(
                IllegalArgumentException.class,
                () -> ExactCountMap.mutablePositiveCopy(
                        Map.of("negative", BigInteger.ONE.negate()),
                        "counts"));

        Map<String, BigInteger> nullAmount = new LinkedHashMap<>();
        nullAmount.put("null", null);
        assertThrows(
                NullPointerException.class,
                () -> ExactCountMap.mutablePositiveCopy(nullAmount, "counts"));
    }

    @Test
    void containsAllIsExactAndDoesNotMutateEitherMap() {
        BigInteger beyondLong = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        Map<String, BigInteger> available = new LinkedHashMap<>(Map.of("key", beyondLong));
        Map<String, BigInteger> exact = new LinkedHashMap<>(Map.of("key", beyondLong));
        Map<String, BigInteger> tooLarge =
                new LinkedHashMap<>(Map.of("key", beyondLong.add(BigInteger.ONE)));

        assertTrue(ExactCountMap.containsAll(available, exact));
        assertFalse(ExactCountMap.containsAll(available, tooLarge));
        assertEquals(Map.of("key", beyondLong), available);
        assertEquals(Map.of("key", beyondLong), exact);
    }

    @Test
    void mergePositiveAddsWithoutLongProjection() {
        BigInteger beyondLong = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        Map<String, BigInteger> target = new LinkedHashMap<>();

        ExactCountMap.mergePositive(target, "key", beyondLong);
        ExactCountMap.mergePositive(target, "key", beyondLong);

        assertEquals(beyondLong.multiply(BigInteger.TWO), target.get("key"));
        assertThrows(
                IllegalArgumentException.class,
                () -> ExactCountMap.mergePositive(target, "key", BigInteger.ZERO));
    }
}
