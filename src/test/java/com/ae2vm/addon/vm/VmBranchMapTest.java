package com.ae2vm.addon.vm;

import static org.junit.jupiter.api.Assertions.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class VmBranchMapTest {
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
