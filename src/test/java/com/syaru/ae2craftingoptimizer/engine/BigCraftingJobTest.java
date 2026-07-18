package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class BigCraftingJobTest {
    @Test
    void rootWindowPlanningGenerationsSurvivePersistence() {
        BigCraftingJob<String> job = BigCraftingJob.rootWindowed(
                UUID.randomUUID(),
                "output",
                BigInteger.TEN.pow(64).subtract(BigInteger.ONE),
                BigInteger.valueOf(123),
                456L,
                789L);

        BigCraftingJob<String> restored = BigCraftingJob.load(job.save(STRINGS, 1024), STRINGS, 1024);

        assertTrue(restored.isRootWindowed());
        assertEquals(456L, restored.patternGeneration());
        assertEquals(789L, restored.recipeGeneration());
        assertEquals(job.requestedAmount(), restored.requestedAmount());
    }
    @Test
    void migratesSignedLongJobStateWithoutNarrowing() {
        BigCraftingJob<String> job = BigCraftingJob.fromLong(
                UUID.randomUUID(),
                "output",
                Long.MAX_VALUE,
                Long.MAX_VALUE,
                Map.of("pattern", Long.MAX_VALUE),
                Map.of("waiting", Long.MAX_VALUE));

        assertEquals(BigInteger.valueOf(Long.MAX_VALUE), job.requestedAmount());
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE), job.remainingTasks().get("pattern"));
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE), job.waitingFor().get("waiting"));
    }
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
    void executionWindowsCannotBeReplayed() {
        BigCraftingJob<String> job = job(BigInteger.TEN);
        BigCraftingJob.PreparedExecution prepared = job.prepareWindow("pattern", 4L);
        long projectedBytes = job.estimatedCountBytesAfterCommit(
                4L, Map.of("output", BigInteger.valueOf(4)));
        job.commitPreparedWindow(
                prepared.transactionId(), 4L, Map.of("output", BigInteger.valueOf(4)));

        assertEquals(BigInteger.valueOf(6), job.remainingTasks().get("pattern"));
        assertEquals(BigInteger.valueOf(4), job.waitingFor().get("output"));
        assertEquals(projectedBytes, job.estimatedCountBytes());
        assertThrows(
                IllegalStateException.class,
                () -> job.commitPreparedWindow(
                        prepared.transactionId(), 4L, Map.of("output", BigInteger.valueOf(4))));

        job.acceptOutput("output", BigInteger.valueOf(4));
        assertTrue(job.waitingFor().isEmpty());
    }

    @Test
    void savesAndRestoresLargeCountsExactly() {
        BigInteger huge = BigInteger.TEN.pow(127);
        BigCraftingJob<String> original = job(huge);
        CompoundTag saved = original.save(STRINGS, 1024);
        BigCraftingJob<String> restored = BigCraftingJob.load(saved, STRINGS, 1024);

        assertEquals(original.id(), restored.id());
        assertEquals(huge, restored.requestedAmount());
        assertEquals(huge, restored.remainingTasks().get("pattern"));
        assertEquals(original.reservedCapacity(), restored.reservedCapacity());
    }

    @Test
    void onlyOneWindowCanBePreparedAndRollbackAllowsAReplacement() {
        BigCraftingJob<String> job = job(BigInteger.TEN);
        BigCraftingJob.PreparedExecution first = job.prepareWindow("pattern", 4L);

        assertThrows(IllegalStateException.class, () -> job.prepareWindow("pattern", 4L));
        job.rollbackPreparedWindow(first.transactionId());

        BigCraftingJob.PreparedExecution replacement = job.prepareWindow("pattern", 4L);
        assertEquals(first.window(), replacement.window());
        assertNotEquals(first.transactionId(), replacement.transactionId());
        assertThrows(
                IllegalStateException.class,
                () -> job.commitPreparedWindow(first.transactionId(), 4L, Map.of()));
    }

    @Test
    void unresolvedPreparedWindowKeepsItsTransactionForHostRecovery() {
        BigCraftingJob<String> job = job(BigInteger.TEN);
        BigCraftingJob.PreparedExecution prepared = job.prepareWindow("pattern", 4L);

        BigCraftingJob<String> loaded = BigCraftingJob.load(job.save(STRINGS, 4096), STRINGS, 4096);

        assertEquals(BigCraftingJob.State.RUNNING, loaded.state());
        assertTrue(loaded.hasPreparedExecution());
        assertThrows(IllegalStateException.class, () -> loaded.prepareWindow("pattern", 4L));
        loaded.commitPreparedWindow(
                prepared.transactionId(), 4L, Map.of("output", BigInteger.valueOf(4L)));
        assertEquals(BigInteger.valueOf(6L), loaded.remainingTasks().get("pattern"));
    }

    @Test
    void cancellingAJobWithUnknownPreparedOutcomeQuarantinesIt() {
        BigCraftingJob<String> job = job(BigInteger.TEN);
        job.prepareWindow("pattern", 4L);

        job.cancel();

        assertEquals(BigCraftingJob.State.QUARANTINED, job.state());
    }

    private static BigCraftingJob<String> job(BigInteger count) {
        return new BigCraftingJob<>(
                UUID.randomUUID(),
                "output",
                count,
                count,
                Map.of("pattern", count),
                Map.of());
    }
}
