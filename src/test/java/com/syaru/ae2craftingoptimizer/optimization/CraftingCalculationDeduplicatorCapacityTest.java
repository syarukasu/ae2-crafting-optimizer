package com.syaru.ae2craftingoptimizer.optimization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Issue #167: 完了Plan cacheの0件上限と縮小時evictionを固定する。 */
class CraftingCalculationDeduplicatorCapacityTest {
    @Test
    void zeroCapacityRejectsTheNewEntryAndClearsOldEntries() {
        Map<Integer, String> entries = new LinkedHashMap<>();
        entries.put(1, "old");

        assertFalse(CraftingCalculationDeduplicator.reserveCompletedPlanSlot(entries, 0));
        assertTrue(entries.isEmpty());
    }

    @Test
    void shrinkingCapacityMakesExactlyOneInsertionSlot() {
        Map<Integer, String> entries = new LinkedHashMap<>();
        entries.put(1, "first");
        entries.put(2, "second");
        entries.put(3, "third");

        assertTrue(CraftingCalculationDeduplicator.reserveCompletedPlanSlot(entries, 2));
        assertEquals(1, entries.size());
        entries.put(4, "new");
        assertEquals(2, entries.size());
    }

    @Test
    void activeIndexEvictionDoesNotNeedTheFutureValue() {
        Map<Integer, String> entries = new LinkedHashMap<>();
        entries.put(1, "first");
        entries.put(2, "second");
        entries.put(3, "third");

        int evicted = CraftingCalculationDeduplicator.reserveActiveCalculationSlot(entries, 2);

        assertEquals(2, evicted);
        assertEquals(1, entries.size());
        assertTrue(entries.containsKey(3));
    }

    @Test
    void completedPlanEvictionRemovesTheLeastRecentlyUsedEntry() {
        Map<Integer, String> entries = new LinkedHashMap<>(16, 0.75F, true);
        entries.put(1, "first");
        entries.put(2, "second");
        entries.get(1);

        assertTrue(CraftingCalculationDeduplicator.reserveCompletedPlanSlot(entries, 2));

        assertTrue(entries.containsKey(1));
        assertFalse(entries.containsKey(2));
    }
}
