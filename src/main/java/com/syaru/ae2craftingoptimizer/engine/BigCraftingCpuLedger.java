package com.syaru.ae2craftingoptimizer.engine;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Atomic capacity reservation and fair execution-window ownership for one integrated BigInteger CPU host. */
public final class BigCraftingCpuLedger<K> {
    public static final int SCHEMA_VERSION = 1;
    private static final int MAX_JOBS = 16_384;

    private final BigCraftingCapacityLedger capacity;
    private final Map<UUID, BigCraftingJob<K>> jobs = new LinkedHashMap<>();
    private int roundRobinCursor;

    public BigCraftingCpuLedger(BigInteger capacity) {
        this.capacity = new BigCraftingCapacityLedger(capacity);
    }

    public synchronized boolean submit(BigCraftingJob<K> job) {
        Objects.requireNonNull(job, "job");
        if (jobs.containsKey(job.id())) {
            throw new IllegalStateException("duplicate BigInteger crafting job " + job.id());
        }
        if (jobs.size() >= MAX_JOBS || !capacity.reserve(job.id(), job.reservedCapacity())) {
            return false;
        }
        jobs.put(job.id(), job);
        return true;
    }

    public synchronized boolean cancel(UUID jobId) {
        BigCraftingJob<K> job = jobs.get(Objects.requireNonNull(jobId, "jobId"));
        if (job == null) {
            return false;
        }
        job.cancel();
        removeTerminal(jobId);
        return true;
    }

    public synchronized boolean discardQuarantined(UUID jobId) {
        BigCraftingJob<K> job = jobs.get(Objects.requireNonNull(jobId, "jobId"));
        if (job == null || job.state() != BigCraftingJob.State.QUARANTINED) {
            return false;
        }
        jobs.remove(jobId);
        capacity.release(jobId);
        normalizeCursor();
        return true;
    }

    public synchronized BigCraftingJob<K> get(UUID jobId) {
        return jobs.get(Objects.requireNonNull(jobId, "jobId"));
    }

    public synchronized List<ScheduledWindow<K>> schedule(
            long operationBudget,
            long maximumExecutionsPerWindow) {
        return schedule(operationBudget, maximumExecutionsPerWindow, Integer.MAX_VALUE);
    }

    public synchronized List<ScheduledWindow<K>> schedule(
            long operationBudget,
            long maximumExecutionsPerWindow,
            int maximumWindows) {
        if (operationBudget <= 0L
                || maximumExecutionsPerWindow <= 0L
                || maximumWindows <= 0
                || jobs.isEmpty()) {
            return List.of();
        }
        List<BigCraftingJob<K>> runnable = jobs.values().stream()
                .filter(job -> !job.terminal()
                        && !job.hasPreparedExecution()
                        && job.hasRemainingTasks())
                .toList();
        if (runnable.isEmpty()) {
            return List.of();
        }
        int start = Math.floorMod(roundRobinCursor, runnable.size());
        List<ScheduledWindow<K>> result = new ArrayList<>();
        long remainingBudget = operationBudget;
        int jobsToVisit = (int) Math.min(
                (long) maximumWindows,
                Math.min((long) runnable.size(), remainingBudget));
        int visited = 0;
        while (remainingBudget > 0L && visited < jobsToVisit) {
            int index = (start + visited) % runnable.size();
            BigCraftingJob<K> job = runnable.get(index);
            String patternId = job.nextRunnableTaskId();
            if (patternId == null) {
                visited++;
                continue;
            }
            long remainingJobs = jobsToVisit - visited;
            long fairShare = Math.floorDiv(remainingBudget, remainingJobs);
            if (remainingBudget % remainingJobs != 0L) {
                fairShare++;
            }
            long granted = Math.min(
                    remainingBudget,
                    Math.min(maximumExecutionsPerWindow, fairShare));
            BigCraftingJob.PreparedExecution prepared = job.prepareWindow(patternId, granted);
            result.add(new ScheduledWindow<>(job, prepared));
            remainingBudget -= prepared.window().executions();
            visited++;
        }
        roundRobinCursor = (start + Math.max(1, jobsToVisit)) % runnable.size();
        return List.copyOf(result);
    }

    public synchronized void removeTerminal(UUID jobId) {
        BigCraftingJob<K> job = jobs.get(Objects.requireNonNull(jobId, "jobId"));
        if (job == null || !job.releasable()) {
            return;
        }
        jobs.remove(jobId);
        capacity.release(jobId);
        normalizeCursor();
    }

    public synchronized boolean resizeCapacity(BigInteger replacement) {
        return capacity.resize(replacement);
    }

    private void normalizeCursor() {
        if (jobs.isEmpty()) {
            roundRobinCursor = 0;
        } else {
            roundRobinCursor %= jobs.size();
        }
    }

    public synchronized CompoundTag save(BigCraftingKeyCodec<K> codec, int maximumBits) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", SCHEMA_VERSION);
        tag.putInt("cursor", roundRobinCursor);
        BigIntegerNbtCodec.putNonNegative(tag, "capacity", capacity.capacity(), maximumBits);
        ListTag savedJobs = new ListTag();
        for (BigCraftingJob<K> job : jobs.values()) {
            if (!job.releasable()) {
                savedJobs.add(job.save(codec, maximumBits));
            }
        }
        tag.put("jobs", savedJobs);
        return tag;
    }

    public static <K> BigCraftingCpuLedger<K> load(
            CompoundTag tag,
            BigCraftingKeyCodec<K> codec,
            int maximumBits) {
        return load(tag, codec, maximumBits, Long.MAX_VALUE);
    }

    public static <K> BigCraftingCpuLedger<K> load(
            CompoundTag tag,
            BigCraftingKeyCodec<K> codec,
            int maximumBits,
            long maximumCountBytes) {
        if (maximumCountBytes < 1L) {
            throw new IllegalArgumentException("maximumCountBytes must be positive");
        }
        if (tag.getInt("schema") != SCHEMA_VERSION || !tag.contains("jobs", Tag.TAG_LIST)) {
            throw new IllegalArgumentException("unsupported BigInteger CPU ledger schema");
        }
        ListTag savedJobs = tag.getList("jobs", Tag.TAG_COMPOUND);
        Tag raw = tag.get("jobs");
        if (!(raw instanceof ListTag rawList)
                || (!rawList.isEmpty() && rawList.getElementType() != Tag.TAG_COMPOUND)
                || savedJobs.size() > MAX_JOBS) {
            throw new IllegalArgumentException("malformed or oversized BigInteger CPU job list");
        }
        BigInteger loadedCapacity = BigIntegerNbtCodec.getNonNegative(tag, "capacity", maximumBits);
        long countBytes = BigCountMath.encodedBytes(loadedCapacity);
        if (countBytes > maximumCountBytes) {
            throw new IllegalArgumentException("saved BigInteger CPU ledger exceeds its count-byte budget");
        }
        BigCraftingCpuLedger<K> ledger = new BigCraftingCpuLedger<>(loadedCapacity);
        for (int index = 0; index < savedJobs.size(); index++) {
            BigCraftingJob<K> job = BigCraftingJob.load(savedJobs.getCompound(index), codec, maximumBits);
            countBytes = Math.addExact(countBytes, job.estimatedCountBytes());
            if (countBytes > maximumCountBytes) {
                throw new IllegalArgumentException(
                        "saved BigInteger CPU jobs exceed their count-byte budget");
            }
            if (!ledger.submit(job)) {
                throw new IllegalArgumentException("saved BigInteger jobs exceed CPU capacity");
            }
        }
        ledger.roundRobinCursor = ledger.jobs.isEmpty()
                ? 0
                : Math.floorMod(tag.getInt("cursor"), ledger.jobs.size());
        return ledger;
    }

    public BigInteger capacity() {
        return capacity.capacity();
    }

    public BigInteger reserved() {
        return capacity.reserved();
    }

    public BigInteger available() {
        return capacity.available();
    }

    public synchronized List<UUID> jobIds() {
        return List.copyOf(jobs.keySet());
    }

    public record ScheduledWindow<K>(
            BigCraftingJob<K> job,
            BigCraftingJob.PreparedExecution prepared) {
        public ScheduledWindow {
            Objects.requireNonNull(job, "job");
            Objects.requireNonNull(prepared, "prepared");
        }
    }
}
