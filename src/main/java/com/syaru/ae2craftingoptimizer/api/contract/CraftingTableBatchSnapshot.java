package com.syaru.ae2craftingoptimizer.api.contract;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable, revisioned output snapshot for external consumers. */
public final class CraftingTableBatchSnapshot {
    private final long revision;
    private final SnapshotState state;
    private final NavigableMap<String, BigInteger> outputCounts;

    private CraftingTableBatchSnapshot(
            long revision,
            SnapshotState state,
            NavigableMap<String, BigInteger> outputCounts) {
        this.revision = revision;
        this.state = state;
        this.outputCounts = Collections.unmodifiableNavigableMap(outputCounts);
    }

    public static CraftingTableBatchSnapshot of(
            long revision,
            SnapshotState state,
            Map<String, BigInteger> outputCounts,
            ExactCountLimits limits) {
        if (revision < 0L) {
            throw new IllegalArgumentException("revision must not be negative");
        }
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(outputCounts, "outputCounts");
        Objects.requireNonNull(limits, "limits").validateKeyCount(outputCounts.size());
        NavigableMap<String, BigInteger> ordered = new TreeMap<>();
        for (Map.Entry<String, BigInteger> entry : outputCounts.entrySet()) {
            limits.validateRequiredIdentifier(entry.getKey());
            limits.validateNonNegative(entry.getValue());
            ordered.put(entry.getKey(), entry.getValue());
        }
        return new CraftingTableBatchSnapshot(revision, state, ordered);
    }

    public long revision() {
        return revision;
    }

    public SnapshotState state() {
        return state;
    }

    /** Returns an immutable, lexicographically ordered view. */
    public Map<String, BigInteger> outputCounts() {
        return outputCounts;
    }
}
