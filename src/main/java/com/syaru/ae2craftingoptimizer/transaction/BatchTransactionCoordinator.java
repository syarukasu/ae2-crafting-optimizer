package com.syaru.ae2craftingoptimizer.transaction;

import com.syaru.ae2craftingoptimizer.api.batch.v2.PatternBatchCommit;
import com.syaru.ae2craftingoptimizer.api.batch.v2.PreparedPatternBatch;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

public final class BatchTransactionCoordinator {
    private BatchTransactionCoordinator() {
    }

    public static Handle open(
            MinecraftServer server,
            ResourceLocation adapterId,
            ResourceLocation sourceId,
            PreparedPatternBatch prepared,
            ResourceLocation dimensionId,
            net.minecraft.core.BlockPos targetPos,
            String patternFingerprint,
            CompoundTag sourceData,
            long gameTick) {
        Objects.requireNonNull(server, "server");
        BatchTransactionJournal journal = journal(server);
        BatchTransactionRecord record = new BatchTransactionRecord(
                prepared.transactionId(),
                adapterId,
                sourceId,
                dimensionId,
                targetPos,
                patternFingerprint,
                prepared.offeredExecutions(),
                0L,
                gameTick,
                gameTick,
                BatchTransactionPhase.PREPARED,
                prepared.aggregateInputs(),
                prepared.expectedOutputs(),
                sourceData,
                prepared.adapterData(),
                "");
        if (!journal.putPrepared(record, ACOConfig.getBatchTransactionJournalMaximumEntries())) {
            return null;
        }
        return new Handle(journal, record.id());
    }

    public static BatchTransactionJournal journal(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                BatchTransactionJournal::load,
                BatchTransactionJournal::new,
                BatchTransactionJournal.DATA_NAME);
    }

    public static final class Handle {
        private final BatchTransactionJournal journal;
        private final java.util.UUID id;
        private boolean targetAccepted;
        private boolean closed;

        private Handle(BatchTransactionJournal journal, java.util.UUID id) {
            this.journal = journal;
            this.id = id;
        }

        public void targetAccepted(PatternBatchCommit commit, long gameTick) {
            ensureOpen();
            BatchTransactionRecord current = journal.get(id);
            if (current == null || commit.acceptedExecutions() != current.offeredExecutions()) {
                throw new IllegalArgumentException("target acceptance must exactly match the offered execution count");
            }
            journal.transition(
                    id,
                    BatchTransactionPhase.TARGET_ACCEPTED,
                    commit.acceptedExecutions(),
                    gameTick,
                    commit.adapterData(),
                    commit.receipt());
            targetAccepted = true;
        }

        public BatchTransactionRecord record() {
            ensureOpen();
            BatchTransactionRecord current = journal.get(id);
            if (current == null) {
                throw new IllegalStateException("transaction is missing from the journal: " + id);
            }
            return current;
        }

        public void accounted(long gameTick) {
            ensureOpen();
            BatchTransactionRecord current = journal.get(id);
            if (!targetAccepted || current == null) {
                throw new IllegalStateException("transaction target has not been accepted");
            }
            journal.transition(
                    id,
                    BatchTransactionPhase.ACCOUNTED,
                    current.acceptedExecutions(),
                    gameTick,
                    current.adapterData(),
                    current.receipt());
            journal.removeTerminal(id);
            closed = true;
        }

        public void rolledBack(long gameTick) {
            ensureOpen();
            if (targetAccepted) {
                throw new IllegalStateException("accepted target cannot be rolled back by the source");
            }
            BatchTransactionRecord current = journal.get(id);
            journal.transition(
                    id,
                    BatchTransactionPhase.ROLLED_BACK,
                    0L,
                    gameTick,
                    current.adapterData(),
                    current.receipt());
            journal.removeTerminal(id);
            closed = true;
        }

        public void reconciliationRequired(long gameTick, String detail) {
            ensureOpen();
            BatchTransactionRecord current = journal.get(id);
            CompoundTag data = current.adapterData();
            data.putString("reconciliationDetail", detail);
            journal.transition(
                    id,
                    BatchTransactionPhase.RECONCILIATION_REQUIRED,
                    current.acceptedExecutions(),
                    gameTick,
                    data,
                    current.receipt());
            closed = true;
        }

        public void quarantine(long gameTick, String detail) {
            ensureOpen();
            BatchTransactionRecord current = journal.get(id);
            CompoundTag data = current.adapterData();
            data.putString("quarantineReason", detail);
            journal.transition(
                    id,
                    BatchTransactionPhase.QUARANTINED,
                    current.acceptedExecutions(),
                    gameTick,
                    data,
                    current.receipt());
            closed = true;
        }

        private void ensureOpen() {
            if (closed) {
                throw new IllegalStateException("transaction handle is closed");
            }
        }
    }
}
