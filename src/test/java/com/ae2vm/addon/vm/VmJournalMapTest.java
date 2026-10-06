package com.ae2vm.addon.vm;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class VmJournalMapTest {
    @Test void checkpointWorkDependsOnWritesNotVisitedKeys() {
        var hashes = new AtomicInteger();
        var map = new VmJournalMap<CountedKey, BigInteger>();
        for (int i = 0; i < 10000; i++) map.put(new CountedKey(i, hashes), BigInteger.ONE);
        hashes.set(0);
        try (var before = map.snapshot()) {
            assertEquals(0, hashes.get(), "Creating a checkpoint does not traverse the ledger");
            map.put(new CountedKey(5, hashes), BigInteger.TEN.pow(1024));
            map.put(new CountedKey(5, hashes), BigInteger.ZERO);
            map.put(new CountedKey(10000, hashes), BigInteger.ZERO);
            assertTrue(hashes.get() < 30);
            assertEquals(2, before.changedKeys().size());
            assertEquals(BigInteger.ONE, before.get(new CountedKey(5, hashes)));
            assertNull(before.get(new CountedKey(10000, hashes)));
            assertEquals(10000, before.size());
        }
    }

    @Test void nestedCheckpointsRetainDistinctExactBeforeValuesAndOrder() {
        var map = new VmJournalMap<String, BigInteger>();
        map.put("a", BigInteger.ONE); map.put("b", BigInteger.TWO);
        try (var first = map.snapshot()) {
            map.put("a", BigInteger.TEN); map.put("c", BigInteger.ZERO);
            try (var second = map.snapshot()) {
                map.put("a", BigInteger.TEN.pow(1024)); map.put("b", BigInteger.ZERO);
                map.put("c", BigInteger.ONE); map.put("d", BigInteger.TEN);
                assertEquals(Map.of("a", BigInteger.ONE, "b", BigInteger.TWO), first);
                assertEquals(List.of("a", "b"), List.copyOf(first.keySet()));
                assertEquals(Map.of("a", BigInteger.TEN, "b", BigInteger.TWO, "c", BigInteger.ZERO), second);
                assertEquals(List.of("a", "b", "c"), List.copyOf(second.keySet()));
            }
            map.put("a", BigInteger.ZERO);
            assertEquals(BigInteger.ONE, first.get("a"));
        }
    }

    @Test void discardedBranchDoesNotRecordParentChangesButCommitDoes() {
        var parent = new VmJournalMap<String, BigInteger>();
        parent.put("a", BigInteger.ONE);
        try (var parentBefore = parent.snapshot()) {
            var discarded = new VmJournalMap<>(new VmBranchMap<>(parent));
            try (var childBefore = discarded.snapshot()) {
                discarded.put("a", BigInteger.TEN); discarded.put("b", BigInteger.ZERO);
                assertEquals(BigInteger.ONE, childBefore.get("a"));
                assertTrue(parentBefore.changedKeys().isEmpty());
                assertEquals(Map.of("a", BigInteger.ONE), parent);
            }
            var committed = new VmJournalMap<>(new VmBranchMap<>(parent));
            committed.put("a", BigInteger.TWO); committed.put("c", BigInteger.ZERO);
            assertEquals(2, committed.changedEntries());
            committed.commit();
            assertEquals(Map.of("a", BigInteger.TWO, "c", BigInteger.ZERO), parent);
            assertEquals(Map.of("a", BigInteger.ONE), parentBefore);
            assertEquals(2, parentBefore.changedKeys().size());
        }
    }

    @Test void flatteningJournalWrappedBranchesDoesNotRecursivelyCloneTheBase() {
        var hashes = new AtomicInteger();
        Map<CountedKey, Integer> map = new VmJournalMap<>();
        for (int i = 0; i < 256; i++) map.put(new CountedKey(i, hashes), i);
        for (int i = 0; i < 48; i++) {
            map = new VmJournalMap<>(new VmBranchMap<>(map));
            map.put(new CountedKey(i, hashes), -i - 1);
        }
        hashes.set(0);
        var copy = new LinkedHashMap<>(map);
        assertTrue(hashes.get() < 4 * 304, "Journal wrappers must not reintroduce recursive snapshots");
        assertEquals(256, copy.size());
        assertEquals(-48, copy.get(new CountedKey(47, hashes)));
    }

    @Test void identicalWritesAreNotRecordedAndClosedSnapshotsAreNotUsed() {
        var map = new VmJournalMap<String, BigInteger>();
        map.put("a", BigInteger.ONE);
        var before = map.snapshot();
        map.put("a", BigInteger.ONE);
        assertTrue(before.changedKeys().isEmpty());
        before.close(); before.close();
        assertThrows(IllegalStateException.class, () -> before.get("a"));
        assertThrows(IllegalStateException.class, before::changedKeys);
        map.put("a", BigInteger.TWO);
        assertEquals(BigInteger.TWO, map.get("a"));
        assertThrows(UnsupportedOperationException.class, () -> map.entrySet().iterator().next().setValue(BigInteger.TEN));
        assertThrows(UnsupportedOperationException.class, map::clear);
    }

    private record CountedKey(int value, AtomicInteger hashes) {
        @Override public int hashCode() { hashes.incrementAndGet(); return value; }
    }
}
