package com.ae2vm.addon.vm;

import static org.junit.jupiter.api.Assertions.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class VmBranchMapTest {
    @Test void nestedSnapshotCopiesTheBaseOnlyOnce() {
        var hashes = new java.util.concurrent.atomic.AtomicInteger();
        var original = new LinkedHashMap<CountedKey, Integer>();
        for (int i = 0; i < 256; i++) original.put(new CountedKey(i, hashes), i);
        java.util.Map<CountedKey, Integer> parent = original;
        for (int depth = 0; depth < 48; depth++) {
            var layer = new VmBranchMap<>(parent);
            layer.put(new CountedKey(depth, hashes), -depth - 1);
            layer.put(new CountedKey(256 + depth, hashes), depth);
            parent = layer;
        }
        hashes.set(0);
        var snapshot = new LinkedHashMap<>(parent);
        int work = hashes.get();
        assertEquals(304, snapshot.size());
        assertEquals(java.util.stream.IntStream.range(0, 304).boxed().toList(),
                snapshot.keySet().stream().map(CountedKey::value).toList());
        for (int i = 0; i < 48; i++) assertEquals(-i - 1, snapshot.get(new CountedKey(i, hashes)));
        assertEquals(0, original.get(new CountedKey(0, hashes)));
        System.out.println("Issue218 nested snapshot: layers=48 base=256 hashWork=" + work);
        assertTrue(work <= 4 * 304, "Do not copy the whole base at every parent layer: " + work);
    }

    private record CountedKey(int value, java.util.concurrent.atomic.AtomicInteger hashes) {
        @Override public int hashCode() { hashes.incrementAndGet(); return value; }
    }

    @Test void nestedCommitAndDiscardDoNotMutateOriginalOrReorderKeys() {
        var original = new LinkedHashMap<String, BigInteger>();
        original.put("a", BigInteger.ONE); original.put("b", BigInteger.TWO);
        var outer = new VmBranchMap<>(original);
        outer.put("b", BigInteger.ZERO); outer.put("c", BigInteger.TEN);
        var failed = new VmBranchMap<>(outer);
        failed.put("a", BigInteger.TEN.pow(64)); failed.put("d", BigInteger.ZERO);
        assertEquals(BigInteger.ONE, outer.get("a")); assertFalse(outer.containsKey("d"));
        var nested = new VmBranchMap<>(outer);
        nested.merge("c", BigInteger.ONE, BigInteger::add);
        nested.put("e", BigInteger.TEN.pow(64)); nested.commit();
        assertEquals(4, outer.size()); assertEquals(BigInteger.valueOf(11), outer.get("c"));
        assertEquals(2, original.size()); assertEquals(BigInteger.TWO, original.get("b"));
        assertEquals(List.of("a", "b", "c", "e"), List.copyOf(outer.keySet()));
        outer.commit();
        assertEquals(BigInteger.ZERO, original.get("b"));
        assertEquals(BigInteger.TEN.pow(64), original.get("e"));
        assertFalse(original.containsKey("d"));
    }
    @Test void trialSnapshotsAreIndependentAndWritesAreSparse() {
        var original = new LinkedHashMap<Integer, Integer>();
        for (int i = 0; i < 10000; i++) original.put(i, i);
        var trial = new VmBranchMap<>(original);
        trial.put(7, -1);
        var snapshot = new LinkedHashMap<>(trial);
        trial.put(7, -2); trial.put(10000, 0);
        assertEquals(-1, snapshot.get(7)); assertEquals(10000, snapshot.size());
        assertEquals(2, trial.changedEntries()); assertEquals(10001, trial.size());
        assertEquals(7, original.get(7));
        assertThrows(UnsupportedOperationException.class, trial::clear);
        assertThrows(UnsupportedOperationException.class, () -> trial.entrySet().iterator().next().setValue(1));
    }
}
