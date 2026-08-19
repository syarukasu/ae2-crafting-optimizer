package com.syaru.ae2craftingoptimizer.api.contract;

import java.util.Objects;

/** Rejects stale snapshots so consumers cannot confuse an older generation with a new one. */
public final class SnapshotRevisionTracker {
    private long latestRevision = -1L;

    public synchronized CraftingTableBatchSnapshot accept(CraftingTableBatchSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.revision() < latestRevision) {
            throw new IllegalArgumentException("snapshot revision moved backwards");
        }
        latestRevision = snapshot.revision();
        return snapshot;
    }

    public synchronized long latestRevision() {
        return latestRevision;
    }
}
