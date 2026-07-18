package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BigCraftingCapacityLedgerTest {
    @Test
    void reservesAndReleasesMultipleJobsExactly() {
        BigInteger capacity = BigInteger.TEN.pow(128);
        BigCraftingCapacityLedger ledger = new BigCraftingCapacityLedger(capacity);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertTrue(ledger.reserve(first, capacity.subtract(BigInteger.TEN)));
        assertTrue(ledger.reserve(second, BigInteger.TEN));
        assertFalse(ledger.reserve(UUID.randomUUID(), BigInteger.ONE));
        assertEquals(BigInteger.ZERO, ledger.available());
        assertEquals(BigInteger.TEN, ledger.release(second));
        assertEquals(BigInteger.TEN, ledger.available());
        assertThrows(IllegalStateException.class, () -> ledger.reserve(first, BigInteger.ONE));
    }

    @Test
    void rejectsAnOversubscribedRestoreAtomically() {
        BigCraftingCapacityLedger ledger = new BigCraftingCapacityLedger(BigInteger.TEN);
        assertThrows(IllegalArgumentException.class, () -> ledger.restore(Map.of(
                UUID.randomUUID(), BigInteger.valueOf(6L),
                UUID.randomUUID(), BigInteger.valueOf(5L))));
        assertEquals(BigInteger.ZERO, ledger.reserved());
    }

    @Test
    void resizesOnlyWhenEveryReservationStillFits() {
        BigCraftingCapacityLedger ledger = new BigCraftingCapacityLedger(BigInteger.valueOf(100));
        assertTrue(ledger.reserve(UUID.randomUUID(), BigInteger.valueOf(80)));

        assertFalse(ledger.resize(BigInteger.valueOf(79)));
        assertEquals(BigInteger.valueOf(100), ledger.capacity());
        assertTrue(ledger.resize(BigInteger.valueOf(90)));
        assertEquals(BigInteger.TEN, ledger.available());
    }
}
