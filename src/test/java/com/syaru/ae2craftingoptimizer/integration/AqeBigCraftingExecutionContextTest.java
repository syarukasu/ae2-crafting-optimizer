package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class AqeBigCraftingExecutionContextTest {
    @Test
    void exposesCompatibleAndExactCapacityOnlyInsideTheOwnerCall() {
        Object cluster = new Object();
        Object otherCluster = new Object();
        BigInteger exact = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);

        String result = AqeBigCraftingExecutionContext.withAllowance(
                cluster,
                Long.MAX_VALUE,
                exact,
                () -> {
                    assertEquals(Long.MAX_VALUE, AqeBigCraftingExecutionContext.allowanceFor(cluster));
                    assertEquals(exact, AqeBigCraftingExecutionContext.exactAllowanceFor(cluster));
                    assertEquals(0L, AqeBigCraftingExecutionContext.allowanceFor(otherCluster));
                    assertEquals(
                            BigInteger.ZERO,
                            AqeBigCraftingExecutionContext.exactAllowanceFor(otherCluster));
                    return "submitted";
                });

        assertEquals("submitted", result);
        assertEquals(0L, AqeBigCraftingExecutionContext.allowanceFor(cluster));
        assertEquals(BigInteger.ZERO, AqeBigCraftingExecutionContext.exactAllowanceFor(cluster));
    }

    @Test
    void rejectsNestedOrNarrowedExactAllowances() {
        Object cluster = new Object();

        assertThrows(
                IllegalStateException.class,
                () -> AqeBigCraftingExecutionContext.withAllowance(
                        cluster,
                        10L,
                        BigInteger.valueOf(9L),
                        () -> "invalid"));
        assertThrows(
                IllegalStateException.class,
                () -> AqeBigCraftingExecutionContext.withAllowance(
                        cluster,
                        10L,
                        BigInteger.TEN,
                        () -> AqeBigCraftingExecutionContext.withAllowance(
                                cluster,
                                1L,
                                BigInteger.ONE,
                                () -> "nested")));
    }
}
