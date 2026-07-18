package com.syaru.ae2craftingoptimizer.api.big;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syaru.ae2craftingoptimizer.engine.BigCraftingJob;
import com.syaru.ae2craftingoptimizer.engine.BigCraftingKeyCodec;
import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class BigCraftingRuntimeTest {
    private static final BigCraftingKeyCodec<String> STRINGS = new BigCraftingKeyCodec<>() {
        @Override
        public CompoundTag encode(String key) {
            CompoundTag tag = new CompoundTag();
            tag.putString("value", key);
            return tag;
        }

        @Override
        public String decode(CompoundTag tag) {
            return tag.getString("value");
        }
    };

    @Test
    void schedulesBoundedLeasesAndPersistsMultipleJobs() {
        BigInteger capacity = BigInteger.TEN.pow(64);
        BigCraftingRuntime<String> runtime = new BigCraftingRuntime<>(capacity, STRINGS, 512, 4);
        BigCraftingJob<String> first = job(BigInteger.valueOf(10), BigInteger.valueOf(10));
        BigCraftingJob<String> second = job(BigInteger.valueOf(8), BigInteger.valueOf(8));
        assertTrue(runtime.submit(first));
        assertTrue(runtime.submit(second));

        var windows = runtime.schedule(8L);
        assertEquals(2, windows.size());
        assertTrue(windows.stream().allMatch(window -> window.prepared().window().executions() == 4L));
        runtime.commit(windows.get(0), 4L, Map.of("out", BigInteger.valueOf(4)));
        runtime.rollback(windows.get(1));

        BigCraftingRuntime<String> restored = BigCraftingRuntime.load(
                runtime.save(), STRINGS, 512, 4);
        assertEquals(capacity, restored.capacity());
        assertEquals(runtime.runtimeId(), restored.runtimeId());
        assertEquals(runtime.reserved(), restored.reserved());
        assertEquals(runtime.jobIds().size(), restored.jobIds().size());
        assertEquals(runtime.estimatedRuntimeCountBytes(), restored.estimatedRuntimeCountBytes());
        var status = restored.statusPage(0, 1);
        assertEquals(2, status.totalJobs());
        assertEquals(1, status.jobs().size());
        assertTrue(status.jobs().get(0).remainingExecutions().signum() > 0);
    }

    @Test
    void compatibilityFacadeSaturatesWithoutTruncation() {
        BigCraftingRuntime<String> runtime = new BigCraftingRuntime<>(
                BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE), STRINGS, 128, 1);
        assertEquals(Long.MAX_VALUE, runtime.capacityAsSaturatedLong());
        assertEquals(0L, runtime.reservedAsSaturatedLong());
    }

    @Test
    void rejectsJobsAboveConfiguredMagnitude() {
        BigCraftingRuntime<String> runtime = new BigCraftingRuntime<>(
                BigInteger.ONE.shiftLeft(127), STRINGS, 128, 1);
        BigCraftingJob<String> oversized = job(BigInteger.ONE.shiftLeft(128), BigInteger.ONE);
        assertThrows(IllegalArgumentException.class, () -> runtime.submit(oversized));
    }

    @Test
    void rejectsDerivedTaskAndWaitingTotalsAboveConfiguredMagnitude() {
        BigInteger largest64BitCount = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
        BigCraftingRuntime<String> runtime = new BigCraftingRuntime<>(
                largest64BitCount, STRINGS, 64, 8);
        BigInteger half = BigInteger.ONE.shiftLeft(63);
        BigCraftingJob<String> oversizedTaskTotal = new BigCraftingJob<>(
                UUID.randomUUID(),
                "out",
                BigInteger.ONE,
                BigInteger.ONE,
                Map.of("first", half, "second", half),
                Map.of());

        assertThrows(IllegalArgumentException.class, () -> runtime.submit(oversizedTaskTotal));

        BigCraftingJob<String> waitingOverflow = new BigCraftingJob<>(
                UUID.randomUUID(),
                "out",
                BigInteger.ONE,
                BigInteger.ONE,
                Map.of("pattern", BigInteger.ONE),
                Map.of("out", largest64BitCount));
        assertTrue(runtime.submit(waitingOverflow));
        var lease = runtime.schedule(1L).get(0);
        assertThrows(
                IllegalArgumentException.class,
                () -> runtime.commit(lease, 1L, Map.of("out", BigInteger.ONE)));
    }

    @Test
    void tracksRuntimeCountBytesIncrementallyAcrossTerminalRemoval() {
        BigCraftingRuntime<String> runtime = new BigCraftingRuntime<>(
                BigInteger.valueOf(100), STRINGS, 128, 8);
        long baseline = runtime.estimatedRuntimeCountBytes();
        BigCraftingJob<String> job = job(BigInteger.ONE, BigInteger.ONE);

        assertTrue(runtime.submit(job));
        assertTrue(runtime.estimatedRuntimeCountBytes() > baseline);
        runtime.commit(runtime.schedule(1L).get(0), 1L, Map.of());

        assertEquals(baseline, runtime.estimatedRuntimeCountBytes());
        assertTrue(runtime.jobIds().isEmpty());
    }

    @Test
    void outputCompletionReleasesTheAtomicCapacityReservation() {
        BigCraftingRuntime<String> runtime = new BigCraftingRuntime<>(
                BigInteger.valueOf(100), STRINGS, 128, 8);
        BigCraftingJob<String> job = job(BigInteger.ONE, BigInteger.TEN);
        assertTrue(runtime.submit(job));
        var scheduled = runtime.schedule(1L).get(0);
        runtime.commit(scheduled, 1L, Map.of("out", BigInteger.ONE));

        assertEquals(BigInteger.TEN, runtime.reserved());
        runtime.acceptOutput(job.id(), "out", BigInteger.ONE);
        assertEquals(BigInteger.ZERO, runtime.reserved());
        assertTrue(runtime.jobIds().isEmpty());
    }

    @Test
    void reformedCpuCanResizeWithoutInvalidatingReservations() {
        BigCraftingRuntime<String> runtime = new BigCraftingRuntime<>(
                BigInteger.valueOf(100), STRINGS, 128, 8);
        assertTrue(runtime.submit(job(BigInteger.ONE, BigInteger.valueOf(80))));

        assertFalse(runtime.resizeCapacity(BigInteger.valueOf(79)));
        assertEquals(BigInteger.valueOf(100), runtime.capacity());
        assertTrue(runtime.resizeCapacity(BigInteger.valueOf(90)));
        assertEquals(BigInteger.TEN, runtime.available());
    }

    @Test
    void rejectsMalformedPersistedExecutionWindowLimit() {
        BigCraftingRuntime<String> runtime = new BigCraftingRuntime<>(
                BigInteger.valueOf(100), STRINGS, 128, 8);
        assertTrue(runtime.submit(job(BigInteger.ONE, BigInteger.ONE)));
        runtime.schedule(1L);
        CompoundTag saved = runtime.save();
        saved.putInt("maximumExecutionsPerWindow", 0);

        assertThrows(
                IllegalArgumentException.class,
                () -> BigCraftingRuntime.load(saved, STRINGS, 128, 8));
    }

    @Test
    void cancellingWithOutstandingOutputsQuarantinesAndRetainsCapacity() {
        BigCraftingRuntime<String> runtime = new BigCraftingRuntime<>(
                BigInteger.valueOf(100), STRINGS, 128, 8);
        BigCraftingJob<String> job = job(BigInteger.ONE, BigInteger.TEN);
        assertTrue(runtime.submit(job));
        var scheduled = runtime.schedule(1L).get(0);
        runtime.commit(scheduled, 1L, Map.of("out", BigInteger.ONE));

        assertTrue(runtime.cancel(job.id()));
        assertEquals(
                BigCraftingJob.State.QUARANTINED,
                runtime.statusPage(0, 1).jobs().get(0).state());
        assertEquals(BigInteger.TEN, runtime.reserved());
    }

    @Test
    void submittedJobsAndExecutionLeasesCannotMutateRuntimeStateOutOfBand() {
        BigCraftingRuntime<String> runtime = new BigCraftingRuntime<>(
                BigInteger.valueOf(100), STRINGS, 128, 8);
        BigCraftingJob<String> callerOwned = job(BigInteger.valueOf(2L), BigInteger.TEN);
        assertTrue(runtime.submit(callerOwned));

        callerOwned.prepareWindow("pattern", 1L);
        var lease = runtime.schedule(1L).get(0);

        assertEquals(BigInteger.valueOf(2L), runtime.statusPage(0, 1)
                .jobs().get(0).remainingExecutions());
        assertThrows(
                IllegalStateException.class,
                () -> new BigCraftingRuntime<>(BigInteger.valueOf(100), STRINGS, 128, 8)
                        .rollback(lease));
    }

    @Test
    void rejectsJobsBeforeTheConfiguredCountMemoryBudgetIsExceeded() {
        BigCraftingRuntime<String> runtime = new BigCraftingRuntime<>(
                BigInteger.TEN.pow(100), STRINGS, 512, 8, 16, 48L);
        BigCraftingJob<String> job = job(BigInteger.TEN.pow(20), BigInteger.ONE);

        assertThrows(IllegalStateException.class, () -> runtime.submit(job));
        assertTrue(runtime.jobIds().isEmpty());
    }

    @Test
    void hostCanCommitAPersistedExecutionLeaseExactlyOnce() {
        BigCraftingRuntime<String> runtime = new BigCraftingRuntime<>(
                BigInteger.valueOf(100), STRINGS, 128, 8);
        BigCraftingJob<String> job = job(BigInteger.ONE, BigInteger.TEN);
        assertTrue(runtime.submit(job));
        var prepared = runtime.schedule(1L).get(0).prepared();

        BigCraftingRuntime<String> restored = BigCraftingRuntime.load(
                runtime.save(), STRINGS, 128, 8);
        var recovered = restored.unresolvedExecutions();
        assertEquals(1, recovered.size());
        assertEquals(prepared.transactionId(), recovered.get(0).prepared().transactionId());

        restored.commitRecovered(
                job.id(), prepared.transactionId(), 1L, Map.of("out", BigInteger.ONE));
        assertTrue(restored.unresolvedExecutions().isEmpty());
        assertThrows(
                IllegalStateException.class,
                () -> restored.commitRecovered(
                        job.id(), prepared.transactionId(), 1L, Map.of("out", BigInteger.ONE)));
        restored.acceptOutput(job.id(), "out", BigInteger.ONE);
        assertTrue(restored.jobIds().isEmpty());
    }

    @Test
    void hostCanRollbackAPersistedExecutionLeaseWithoutAdvancingProgress() {
        BigCraftingRuntime<String> runtime = new BigCraftingRuntime<>(
                BigInteger.valueOf(100), STRINGS, 128, 8);
        BigCraftingJob<String> job = job(BigInteger.valueOf(2L), BigInteger.TEN);
        assertTrue(runtime.submit(job));
        var first = runtime.schedule(1L).get(0).prepared();

        BigCraftingRuntime<String> restored = BigCraftingRuntime.load(
                runtime.save(), STRINGS, 128, 8);
        restored.rollbackRecovered(job.id(), first.transactionId());
        var replacement = restored.schedule(1L).get(0).prepared();

        assertEquals(first.window(), replacement.window());
        assertNotEquals(first.transactionId(), replacement.transactionId());
    }

    private static BigCraftingJob<String> job(BigInteger amount, BigInteger reservation) {
        return new BigCraftingJob<>(
                UUID.randomUUID(),
                "out",
                amount,
                reservation,
                Map.of("pattern", amount),
                Map.of());
    }
}
