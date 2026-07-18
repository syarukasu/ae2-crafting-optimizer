package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BigCraftingInventoryTest {
    @Test
    void transactionRollsBackUnlessCommitted() {
        BigCraftingInventory<String> inventory = new BigCraftingInventory<>(Map.of("a", BigInteger.TEN));
        try (var transaction = inventory.beginTransaction()) {
            transaction.extractExact("a", BigInteger.valueOf(4));
            transaction.insert("b", BigInteger.valueOf(2));
        }
        assertEquals(Map.of("a", BigInteger.TEN), inventory.snapshot());
        assertEquals(BigInteger.TEN, inventory.totalAmount());
        assertEquals(1, inventory.distinctKeys());
        assertEquals(1L, inventory.encodedCountBytes());

        try (var transaction = inventory.beginTransaction()) {
            transaction.extractExact("a", BigInteger.valueOf(4));
            transaction.commit();
        }
        assertEquals(Map.of("a", BigInteger.valueOf(6)), inventory.snapshot());
        assertEquals(BigInteger.valueOf(6), inventory.totalAmount());
        assertEquals(1L, inventory.encodedCountBytes());
    }

    @Test
    void exactExtractionNeverUnderflows() {
        BigCraftingInventory<String> inventory = new BigCraftingInventory<>(Map.of("a", BigInteger.ONE));
        assertThrows(IllegalStateException.class, () -> inventory.extractExact("a", BigInteger.TWO));
        assertEquals(BigInteger.ONE, inventory.amount("a"));
    }

    @Test
    void transactionDoesNotExposePartialStateAndRejectsOverlap() {
        BigCraftingInventory<String> inventory = new BigCraftingInventory<>(
                Map.of("iron", BigInteger.TEN));

        try (BigCraftingInventory.Transaction<String> transaction = inventory.beginTransaction()) {
            transaction.extractExact("iron", BigInteger.valueOf(4));
            transaction.insert("gold", BigInteger.valueOf(2));

            assertEquals(BigInteger.TEN, inventory.amount("iron"));
            assertEquals(BigInteger.ZERO, inventory.amount("gold"));
            assertThrows(IllegalStateException.class, inventory::beginTransaction);
            assertThrows(
                    IllegalStateException.class,
                    () -> inventory.insert("copper", BigInteger.ONE));
            transaction.commit();
        }

        assertEquals(BigInteger.valueOf(6), inventory.amount("iron"));
        assertEquals(BigInteger.valueOf(2), inventory.amount("gold"));
        assertEquals(BigInteger.valueOf(8), inventory.totalAmount());
        assertEquals(2, inventory.distinctKeys());
        assertEquals(2L, inventory.encodedCountBytes());
    }

    @Test
    void injectedFailureBeforeCommitRestoresTheExactSnapshot() {
        BigCraftingInventory<String> inventory = new BigCraftingInventory<>(
                Map.of("item", BigInteger.valueOf(100), "fluid", BigInteger.valueOf(50)));
        Map<String, BigInteger> before = inventory.snapshot();

        assertThrows(InjectedFailure.class, () -> {
            try (var transaction = inventory.beginTransaction()) {
                transaction.extractExact("item", BigInteger.valueOf(75));
                transaction.insert("chemical", BigInteger.valueOf(25));
                throw new InjectedFailure();
            }
        });
        assertEquals(before, inventory.snapshot());
        assertEquals(BigInteger.valueOf(150), inventory.totalAmount());
        assertEquals(2L, inventory.encodedCountBytes());
    }

    @Test
    void zeroExtractionDoesNotCreateOrRemoveAccountingBytes() {
        BigCraftingInventory<String> inventory = new BigCraftingInventory<>();

        inventory.extractExact("absent", BigInteger.ZERO);
        try (var transaction = inventory.beginTransaction()) {
            transaction.extractExact("absent", BigInteger.ZERO);
            transaction.commit();
        }

        assertEquals(BigInteger.ZERO, inventory.totalAmount());
        assertEquals(0L, inventory.encodedCountBytes());
        assertEquals(0, inventory.distinctKeys());
    }

    private static final class InjectedFailure extends RuntimeException {
    }
}
