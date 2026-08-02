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
                789L,
                65_536L,
                "runtime-epoch",
                "0123456789abcdef");

        BigCraftingJob<String> restored = BigCraftingJob.load(job.save(STRINGS, 1024), STRINGS, 1024);

        assertTrue(restored.isRootWindowed());
        assertEquals(456L, restored.patternGeneration());
        assertEquals(789L, restored.recipeGeneration());
        assertEquals(job.requestedAmount(), restored.requestedAmount());
        assertEquals("runtime-epoch", restored.planningEpoch());
        assertEquals("0123456789abcdef", restored.programFingerprint());
    }

    @Test
    void exactVectorRequirementSurvivesPersistence() {
        BigCraftingJob<String> job = BigCraftingJob.rootWindowed(
                UUID.randomUUID(),
                "output",
                BigInteger.ONE,
                BigInteger.TEN.pow(32),
                456L,
                789L,
                1L,
                "runtime-epoch",
                "0123456789abcdef",
                true);

        BigCraftingJob<String> restored = BigCraftingJob.load(
                job.save(STRINGS, 1024), STRINGS, 1024);

        assertTrue(restored.exactVectorRequired());
        assertTrue(restored.isRootWindowed());
    }

    @Test
    void recipeSpecificWindowLimitSurvivesPersistenceAndCapsDispatch() {
        long safeRecipeWindow = 3L;
        BigCraftingJob<String> job = BigCraftingJob.rootWindowed(
                UUID.randomUUID(),
                "output",
                BigInteger.valueOf(100L),
                BigInteger.valueOf(1_000L),
                12L,
                34L,
                safeRecipeWindow);

        BigCraftingJob<String> restored =
                BigCraftingJob.load(job.save(STRINGS, 1024), STRINGS, 1024);
        BigCraftingJob.PreparedExecution prepared = restored.prepareWindow(
                BigCraftingJob.ROOT_WINDOW_TASK_ID,
                BigCraftingJob.MAX_EXECUTIONS_PER_WINDOW);

        assertEquals(safeRecipeWindow, restored.maximumExecutionsPerWindow());
        assertEquals(safeRecipeWindow, prepared.window().executions());
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

    @Test
    void vectorLeaseSurvivesPersistenceAndCommitsTheWholeParentOnce() {
        BigInteger amount =
                BigInteger.TEN.pow(1_024).subtract(BigInteger.ONE);
        BigCraftingJob<String> job = BigCraftingJob.rootWindowed(
                UUID.randomUUID(),
                "output",
                amount,
                BigInteger.valueOf(42L));
        UUID transactionId = UUID.randomUUID();
        job.prepareVectorExecution(
                transactionId,
                "aac:test-controller",
                "vector-plan-fingerprint");

        BigCraftingJob<String> restored = BigCraftingJob.load(
                job.save(STRINGS, 4096),
                STRINGS,
                4096);

        assertEquals(BigCraftingJob.State.RUNNING, restored.state());
        assertEquals(
                amount,
                restored.preparedVectorExecution().executions());
        restored.commitPreparedVector(transactionId);
        assertEquals(BigCraftingJob.State.COMPLETE, restored.state());
        assertTrue(restored.remainingTasks().isEmpty());
        assertThrows(
                IllegalStateException.class,
                () -> restored.commitPreparedVector(transactionId));
    }

    @Test
    void vectorLeaseRollbackDoesNotAdvanceParentProgress() {
        BigInteger amount = BigInteger.TEN.pow(64);
        BigCraftingJob<String> job = BigCraftingJob.rootWindowed(
                UUID.randomUUID(),
                "output",
                amount,
                BigInteger.ONE);
        UUID first = UUID.randomUUID();
        job.prepareVectorExecution(
                first,
                "aac:first",
                "first-plan");

        job.rollbackPreparedVector(first);
        UUID replacement = UUID.randomUUID();
        var prepared = job.prepareVectorExecution(
                replacement,
                "aac:replacement",
                "replacement-plan");

        assertEquals(BigInteger.ZERO, prepared.offset());
        assertEquals(amount, prepared.executions());
        assertThrows(
                IllegalStateException.class,
                () -> job.rollbackPreparedVector(first));
    }

    @Test
    void vectorLeasePersistsAuthoritativeTreeStateAndDisplaysPhysicalProgress() {
        BigInteger amount =
                BigInteger.TEN.pow(64);
        BigCraftingJob<String> job =
                BigCraftingJob.rootWindowed(
                        UUID.randomUUID(),
                        "output",
                        amount,
                        BigInteger.ONE);
        UUID transactionId =
                UUID.randomUUID();
        CompoundTag treeState =
                new CompoundTag();
        treeState.putInt(
                "stepCursor",
                7);
        job.prepareVectorExecution(
                transactionId,
                "aco:crafting-table-tree-v1",
                "tree-plan",
                treeState,
                25,
                100);

        /*
         * 呼出側が返却Tagを書き換えても、親Job内の正本状態と保存内容は変わらない。
         */
        job.preparedVectorExecution()
                .executionState()
                .putInt(
                        "stepCursor",
                        99);
        BigCraftingJob<String> restored =
                BigCraftingJob.load(
                        job.save(
                                STRINGS,
                                512),
                        STRINGS,
                        512);

        assertEquals(
                7,
                restored.preparedVectorExecution()
                        .executionState()
                        .getInt(
                                "stepCursor"));
        assertEquals(
                amount.multiply(
                                BigInteger.valueOf(75L))
                        .divide(
                                BigInteger.valueOf(100L)),
                restored.compactStatusSnapshot()
                        .remainingExecutions());
        assertEquals(
                amount,
                restored.remainingExecutionTotal());
    }

    @Test
    void vectorProgressRejectsZeroDenominatorAndValuesAboveOneHundredPercent() {
        BigCraftingJob<String> job =
                BigCraftingJob.rootWindowed(
                        UUID.randomUUID(),
                        "output",
                        BigInteger.TEN,
                        BigInteger.ONE);

        assertThrows(
                IllegalArgumentException.class,
                () -> job.prepareVectorExecution(
                        UUID.randomUUID(),
                        "aco:crafting-table-tree-v1",
                        "tree-plan",
                        new CompoundTag(),
                        0,
                        0));
        assertThrows(
                IllegalArgumentException.class,
                () -> job.prepareVectorExecution(
                        UUID.randomUUID(),
                        "aco:crafting-table-tree-v1",
                        "tree-plan",
                        new CompoundTag(),
                        101,
                        100));
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
