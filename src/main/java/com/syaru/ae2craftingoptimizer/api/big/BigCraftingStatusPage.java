package com.syaru.ae2craftingoptimizer.api.big;

import com.syaru.ae2craftingoptimizer.engine.BigCraftingJob;
import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable, bounded status page suitable for a crafting-status GUI packet. */
public record BigCraftingStatusPage<K>(
        UUID runtimeId,
        BigInteger capacity,
        BigInteger reserved,
        BigInteger available,
        int totalJobs,
        int offset,
        List<JobSummary<K>> jobs) {
    public BigCraftingStatusPage {
        Objects.requireNonNull(runtimeId, "runtimeId");
        requireNonNegative(capacity, "capacity");
        requireNonNegative(reserved, "reserved");
        requireNonNegative(available, "available");
        if (!reserved.add(available).equals(capacity)) {
            throw new IllegalArgumentException("status capacity accounting is inconsistent");
        }
        if (totalJobs < 0 || offset < 0 || offset > totalJobs) {
            throw new IllegalArgumentException("invalid status page bounds");
        }
        jobs = List.copyOf(Objects.requireNonNull(jobs, "jobs"));
        if ((long) offset + jobs.size() > totalJobs) {
            throw new IllegalArgumentException("status page exceeds total job count");
        }
    }

    public record JobSummary<K>(
            UUID id,
            K requestedKey,
            BigInteger requestedAmount,
            BigInteger reservedCapacity,
            BigInteger remainingExecutions,
            BigInteger waitingAmount,
            int remainingTaskTypes,
            int waitingTypes,
            BigCraftingJob.State state,
            boolean executionPrepared) {
        public JobSummary {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(requestedKey, "requestedKey");
            requireNonNegative(requestedAmount, "requestedAmount");
            requireNonNegative(reservedCapacity, "reservedCapacity");
            requireNonNegative(remainingExecutions, "remainingExecutions");
            requireNonNegative(waitingAmount, "waitingAmount");
            if (remainingTaskTypes < 0 || waitingTypes < 0) {
                throw new IllegalArgumentException("status entry type counts must be non-negative");
            }
            Objects.requireNonNull(state, "state");
        }
    }

    private static void requireNonNegative(BigInteger value, String name) {
        if (Objects.requireNonNull(value, name).signum() < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }
}
