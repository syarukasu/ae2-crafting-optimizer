package com.syaru.ae2craftingoptimizer.scheduler;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Per-crafting-job state embedded in the owning CPU's existing NBT. */
public final class FairSchedulerPersistentState {
    private static final int SCHEMA_VERSION = 1;
    private static final long MAX_PERSISTED_DEFICIT = 1L << 40;

    private UUID jobId;
    private long deficit;
    private long cursor;

    public FairSchedulerPersistentState() {
    }

    public FairSchedulerPersistentState(UUID jobId) {
        this.jobId = Objects.requireNonNull(jobId, "jobId");
    }

    public UUID jobId() {
        if (jobId == null) {
            jobId = UUID.randomUUID();
        }
        return jobId;
    }

    public long deficit() {
        return deficit;
    }

    public long cursor() {
        return cursor;
    }

    public boolean initialized() {
        return jobId != null;
    }

    public void credit(long operations, long maximumDeficit) {
        if (operations < 0L || maximumDeficit < 0L) {
            throw new IllegalArgumentException("scheduler credit and cap must not be negative");
        }
        long cap = Math.min(MAX_PERSISTED_DEFICIT, maximumDeficit);
        long next = deficit > cap - Math.min(cap, operations)
                ? cap
                : deficit + operations;
        deficit = Math.min(cap, next);
    }

    public void consume(long operations) {
        if (operations < 0L || operations > deficit) {
            throw new IllegalArgumentException("cannot consume more scheduler deficit than available");
        }
        deficit -= operations;
    }

    public void updateCursor(long nextCursor) {
        if (nextCursor < 0L) {
            throw new IllegalArgumentException("scheduler cursor must not be negative");
        }
        cursor = nextCursor;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", SCHEMA_VERSION);
        tag.putUUID("jobId", jobId());
        tag.putLong("deficit", deficit);
        tag.putLong("cursor", cursor);
        return tag;
    }

    public void load(CompoundTag tag) {
        jobId = null;
        deficit = 0L;
        cursor = 0L;
        if (tag.getInt("schema") != SCHEMA_VERSION || !tag.hasUUID("jobId")) {
            return;
        }
        jobId = tag.getUUID("jobId");
        deficit = Math.min(MAX_PERSISTED_DEFICIT, Math.max(0L, tag.getLong("deficit")));
        cursor = Math.max(0L, tag.getLong("cursor"));
    }
}
