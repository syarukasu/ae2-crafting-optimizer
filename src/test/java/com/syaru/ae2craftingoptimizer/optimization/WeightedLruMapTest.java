package com.syaru.ae2craftingoptimizer.optimization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Issue #167: root program cacheの件数・総ノード上限とLRU退避を固定する。 */
class WeightedLruMapTest {
    @Test
    void entryLimitEvictsTheLeastRecentlyUsedEntry() {
        WeightedLruMap<String, Entry> cache = cache(2, 10);
        List<String> evicted = new ArrayList<>();
        Entry first = new Entry("first", 1);
        Entry second = new Entry("second", 1);
        Entry third = new Entry("third", 1);

        cache.putIfAbsent("first", first, evicted::add);
        cache.putIfAbsent("second", second, evicted::add);
        assertSame(first, cache.get("first"));
        cache.putIfAbsent("third", third, evicted::add);

        assertEquals(List.of("second"), evicted);
        assertTrue(cache.containsExact("first", first));
        assertTrue(cache.containsExact("third", third));
        assertFalse(cache.containsExact("second", second));
        assertEquals(2, cache.size());
        assertEquals(2, cache.currentWeight());
    }

    @Test
    void weightLimitEvictsUntilTheNewEntryFits() {
        WeightedLruMap<String, Entry> cache = cache(4, 5);
        List<String> evicted = new ArrayList<>();
        cache.putIfAbsent("first", new Entry("first", 2), evicted::add);
        cache.putIfAbsent("second", new Entry("second", 2), evicted::add);

        Entry third = new Entry("third", 4);
        cache.putIfAbsent("third", third, evicted::add);

        assertEquals(List.of("first", "second"), evicted);
        assertTrue(cache.containsExact("third", third));
        assertEquals(1, cache.size());
        assertEquals(4, cache.currentWeight());
    }

    @Test
    void oversizedSingleResultIsReturnedWithoutEvictingOrCaching() {
        WeightedLruMap<String, Entry> cache = cache(2, 3);
        List<String> evicted = new ArrayList<>();
        Entry retained = new Entry("retained", 2);
        cache.putIfAbsent("retained", retained, evicted::add);

        Entry oversized = new Entry("oversized", 4);
        assertSame(oversized, cache.putIfAbsent("oversized", oversized, evicted::add));

        assertTrue(evicted.isEmpty());
        assertTrue(cache.containsExact("retained", retained));
        assertFalse(cache.containsExact("oversized", oversized));
        assertEquals(2, cache.currentWeight());
    }

    @Test
    void existingValueWinsWithoutChangingItsWeight() {
        WeightedLruMap<String, Entry> cache = cache(2, 5);
        Entry first = new Entry("first", 2);
        cache.putIfAbsent("same", first, ignored -> {
        });

        Entry replacement = new Entry("replacement", 5);
        assertSame(first, cache.putIfAbsent("same", replacement, ignored -> {
        }));
        assertEquals(2, cache.currentWeight());
    }

    @Test
    void invalidLimitsAndWeightsFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> cache(0, 1));
        assertThrows(IllegalArgumentException.class, () -> cache(1, 0));

        WeightedLruMap<String, Entry> cache = cache(1, 1);
        assertThrows(
                IllegalArgumentException.class,
                () -> cache.putIfAbsent("zero", new Entry("zero", 0), ignored -> {
                }));
    }

    private static WeightedLruMap<String, Entry> cache(int entries, int weight) {
        return new WeightedLruMap<>(entries, weight, Entry::weight);
    }

    private record Entry(String id, int weight) {
    }
}
