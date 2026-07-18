package com.syaru.ae2craftingoptimizer.scheduler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * tick予算付きDeficit Round Robin Scheduler。
 * 既知の実行可能Jobへ最低枠を予約してから余剰枠を貸すため、呼び出し順や巨大Jobによって
 * 小さいJobが永久に実行されない状態を防ぐ。
 */
public final class DeficitRoundRobinScheduler {
    private static final long ACTIVE_WINDOW_TICKS = 1L;
    private final Map<UUID, RuntimeJob> jobs = new HashMap<>();
    private long currentTick = Long.MIN_VALUE;
    private int remainingOperations;
    private long consumedNanos;
    private long timeBudgetNanos;
    private long reservedForUncalledJobs;
    private long sequence = 1L;
    private int operationBudget;
    private int quantum;

    public synchronized int grant(
            FairSchedulerPersistentState persistent,
            int requestedOperations,
            long gameTick,
            int maximumOperations,
            int operationQuantum,
            long maximumNanos) {
        if (requestedOperations <= 0 || maximumOperations <= 0 || operationQuantum <= 0 || maximumNanos <= 0L) {
            return 0;
        }
        beginTick(gameTick, maximumOperations, operationQuantum, maximumNanos);

        UUID id = persistent.jobId();
        RuntimeJob job = jobs.get(id);
        if (job == null) {
            job = new RuntimeJob(persistent, gameTick);
            jobs.put(id, job);
            sequence = Math.max(sequence, saturatingIncrement(persistent.cursor()));
            if (jobs.size() == 1 && reservedForUncalledJobs == 0L && remainingOperations > 0) {
                reserve(job, remainingOperations);
            }
        } else {
            job.persistent = persistent;
        }
        job.lastSeenTick = gameTick;

        if (!job.calledThisTick) {
            job.calledThisTick = true;
            reservedForUncalledJobs = Math.max(0L, reservedForUncalledJobs - job.reservedOperations);
        }
        if (consumedNanos >= timeBudgetNanos || remainingOperations <= 0) {
            return 0;
        }

        long safeAvailable = Math.max(0L, (long) remainingOperations - reservedForUncalledJobs);
        long granted = Math.min(requestedOperations, Math.min(persistent.deficit(), safeAvailable));
        if (granted <= 0L) {
            return 0;
        }
        persistent.consume(granted);
        remainingOperations -= (int) granted;
        return (int) granted;
    }

    public synchronized void recordElapsed(long gameTick, long elapsedNanos) {
        if (gameTick == currentTick && elapsedNanos > 0L) {
            consumedNanos = saturatingAdd(consumedNanos, elapsedNanos);
        }
    }

    public synchronized int remainingOperations() {
        return remainingOperations;
    }

    private void beginTick(long gameTick, int maximumOperations, int operationQuantum, long maximumNanos) {
        if (currentTick == gameTick) {
            return;
        }
        currentTick = gameTick;
        operationBudget = maximumOperations;
        quantum = operationQuantum;
        remainingOperations = maximumOperations;
        consumedNanos = 0L;
        timeBudgetNanos = maximumNanos;
        reservedForUncalledJobs = 0L;

        jobs.values().removeIf(job -> elapsedTicks(gameTick, job.lastSeenTick) > ACTIVE_WINDOW_TICKS);
        List<RuntimeJob> active = new ArrayList<>(jobs.values());
        active.sort(Comparator
                .comparingLong((RuntimeJob job) -> job.persistent.cursor())
                .thenComparing(job -> job.persistent.jobId()));
        for (RuntimeJob job : active) {
            job.calledThisTick = false;
            job.reservedOperations = 0;
        }
        allocateRoundRobin(active);
    }

    private void allocateRoundRobin(List<RuntimeJob> active) {
        if (active.isEmpty()) {
            return;
        }
        long roundCost = (long) active.size() * quantum;
        long fullRounds = operationBudget / roundCost;
        long base = fullRounds * quantum;
        long unallocated = operationBudget - base * active.size();
        long maximumDeficit = Math.max((long) operationBudget * 4L, (long) quantum * 4L);

        for (RuntimeJob job : active) {
            long allocation = base;
            if (unallocated > 0L) {
                long partial = Math.min(quantum, unallocated);
                allocation += partial;
                unallocated -= partial;
            }
            reserve(job, allocation, maximumDeficit);
        }
    }

    private void reserve(RuntimeJob job, long operations) {
        long maximumDeficit = Math.max((long) operationBudget * 4L, (long) quantum * 4L);
        reserve(job, operations, maximumDeficit);
    }

    private void reserve(RuntimeJob job, long operations, long maximumDeficit) {
        job.reservedOperations = operations;
        job.persistent.credit(operations, maximumDeficit);
        if (operations > 0L) {
            job.persistent.updateCursor(nextSequence());
        }
        reservedForUncalledJobs = saturatingAdd(reservedForUncalledJobs, operations);
    }

    private long nextSequence() {
        if (sequence == Long.MAX_VALUE) {
            List<RuntimeJob> ordered = new ArrayList<>(jobs.values());
            ordered.sort(Comparator
                    .comparingLong((RuntimeJob job) -> job.persistent.cursor())
                    .thenComparing(job -> job.persistent.jobId()));
            long next = 1L;
            for (RuntimeJob job : ordered) {
                job.persistent.updateCursor(next++);
            }
            sequence = next;
        }
        return sequence++;
    }

    private static long elapsedTicks(long now, long then) {
        if (now >= then) {
            return now - then;
        }
        return Long.MAX_VALUE;
    }

    private static long saturatingIncrement(long value) {
        return value == Long.MAX_VALUE ? Long.MAX_VALUE : value + 1L;
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static final class RuntimeJob {
        private FairSchedulerPersistentState persistent;
        private long lastSeenTick;
        private long reservedOperations;
        private boolean calledThisTick;

        private RuntimeJob(FairSchedulerPersistentState persistent, long lastSeenTick) {
            this.persistent = persistent;
            this.lastSeenTick = lastSeenTick;
        }
    }
}
