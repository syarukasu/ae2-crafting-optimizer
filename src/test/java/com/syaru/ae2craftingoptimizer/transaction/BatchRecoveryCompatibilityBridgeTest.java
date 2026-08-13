package com.syaru.ae2craftingoptimizer.transaction;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.syaru.ae2craftingoptimizer.api.batch.PatternBatchBudget;
import com.syaru.ae2craftingoptimizer.api.batch.PatternBatchContext;
import com.syaru.ae2craftingoptimizer.api.batch.v2.BatchRecoveryResult;
import com.syaru.ae2craftingoptimizer.api.batch.v2.BatchSourceReconciler;
import com.syaru.ae2craftingoptimizer.api.batch.v2.PatternBatchCommit;
import com.syaru.ae2craftingoptimizer.api.batch.v2.PreparedPatternBatch;
import com.syaru.ae2craftingoptimizer.api.batch.v2.SourceRecoveryResult;
import com.syaru.ae2craftingoptimizer.api.batch.v2.TransactionalPatternBatchAdapter;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.Test;

class BatchRecoveryCompatibilityBridgeTest {
    private static final BatchRecoveryResult NOT_ACCEPTED =
            new BatchRecoveryResult(
                    BatchRecoveryResult.TargetState.NOT_ACCEPTED,
                    0L,
                    "test");

    @Test
    void invokesThePublicRecoveryContractWithAReadOnlySnapshot() {
        PublicAdapter adapter = new PublicAdapter();

        BatchRecoveryResult result =
                BatchRecoveryCompatibilityBridge.reconcileTarget(
                        adapter,
                        null,
                        record());

        assertEquals(NOT_ACCEPTED, result);
        assertNotNull(adapter.received);
        assertEquals("PREPARED", adapter.received.phase());
    }

    @Test
    void invokesTheLegacyRecoveryDescriptorForExistingAdapters() {
        LegacyAdapter adapter = new LegacyAdapter();

        BatchRecoveryResult result =
                BatchRecoveryCompatibilityBridge.reconcileTarget(
                        adapter,
                        null,
                        record());

        assertEquals(NOT_ACCEPTED, result);
        assertNotNull(adapter.received);
    }

    @Test
    void preservesTheLegacyDefaultNoOpCleanup() {
        LegacyAdapter adapter = new LegacyAdapter();
        LegacySource source = new LegacySource();
        BatchTransactionRecord record = record();

        assertDoesNotThrow(
                () -> BatchRecoveryCompatibilityBridge.forgetTarget(
                        adapter,
                        null,
                        record));
        assertDoesNotThrow(
                () -> BatchRecoveryCompatibilityBridge.forgetSource(
                        source,
                        null,
                        record));
    }

    @Test
    void invokesLegacySourceRecoveryDescriptors() {
        LegacySource source = new LegacySource();
        BatchTransactionRecord record = record();

        assertEquals(
                SourceRecoveryResult.COMPLETE,
                BatchRecoveryCompatibilityBridge.rollbackPrepared(
                        source,
                        null,
                        record));
        assertEquals(
                SourceRecoveryResult.COMPLETE,
                BatchRecoveryCompatibilityBridge.accountAccepted(
                        source,
                        null,
                        record));
        assertEquals(2, source.calls);
    }

    private static BatchTransactionRecord record() {
        return new BatchTransactionRecord(
                UUID.randomUUID(),
                ResourceLocation.fromNamespaceAndPath("test", "adapter"),
                ResourceLocation.fromNamespaceAndPath("test", "source"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                BlockPos.ZERO,
                "pattern",
                1L,
                0L,
                1L,
                1L,
                BatchTransactionPhase.PREPARED,
                List.of(),
                List.of(),
                new CompoundTag(),
                new CompoundTag(),
                "");
    }

    private abstract static class BaseAdapter
            implements TransactionalPatternBatchAdapter {
        @Override
        public ResourceLocation id() {
            return ResourceLocation.fromNamespaceAndPath("test", "adapter");
        }

        @Override
        public boolean supports(PatternBatchContext context) {
            return false;
        }

        @Override
        public PreparedPatternBatch prepare(
                PatternBatchContext context,
                PatternBatchBudget budget,
                UUID transactionId) {
            throw new UnsupportedOperationException("not used by this recovery test");
        }

        @Override
        public PatternBatchCommit commit(
                PatternBatchContext context,
                PreparedPatternBatch prepared) {
            throw new UnsupportedOperationException("not used by this recovery test");
        }

        @Override
        public void rollback(
                PatternBatchContext context,
                PreparedPatternBatch prepared) {
            throw new UnsupportedOperationException("not used by this recovery test");
        }
    }

    private static final class PublicAdapter extends BaseAdapter {
        private com.syaru.ae2craftingoptimizer.api.batch.v2.BatchTransactionRecord received;

        @Override
        public BatchRecoveryResult reconcileTarget(
                ServerLevel level,
                com.syaru.ae2craftingoptimizer.api.batch.v2.BatchTransactionRecord record) {
            received = record;
            return NOT_ACCEPTED;
        }
    }

    /** ACO 1.5.xでコンパイル済みのAdapterと同じ旧method descriptorを持つfixture。 */
    private static final class LegacyAdapter extends BaseAdapter {
        private BatchTransactionRecord received;

        public BatchRecoveryResult reconcileTarget(
                ServerLevel level,
                BatchTransactionRecord record) {
            received = record;
            return NOT_ACCEPTED;
        }
    }

    /** ACO 1.5.xのSource実装と同じ旧method descriptorを持つfixture。 */
    private static final class LegacySource implements BatchSourceReconciler {
        private int calls;

        @Override
        public ResourceLocation id() {
            return ResourceLocation.fromNamespaceAndPath("test", "source");
        }

        public SourceRecoveryResult rollbackPrepared(
                ServerLevel level,
                BatchTransactionRecord record) {
            calls++;
            return SourceRecoveryResult.COMPLETE;
        }

        public SourceRecoveryResult accountAccepted(
                ServerLevel level,
                BatchTransactionRecord record) {
            calls++;
            return SourceRecoveryResult.COMPLETE;
        }
    }
}
