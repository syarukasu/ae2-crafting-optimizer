package com.syaru.ae2craftingoptimizer.transaction;

import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.api.batch.v2.BatchRecoveryResult;
import com.syaru.ae2craftingoptimizer.api.batch.v2.PatternBatchV2Api;
import com.syaru.ae2craftingoptimizer.api.batch.v2.SourceRecoveryResult;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class BatchTransactionRecovery {
    private static final int MAX_RECORDS_PER_PASS = 64;
    private static final Set<UUID> LOGGED_UNRESOLVED = ConcurrentHashMap.newKeySet();

    private BatchTransactionRecovery() {
    }

    public static void tick(MinecraftServer server, long gameTick) {
        if (gameTick % ACOConfig.getBatchTransactionReconciliationIntervalTicks() != 0L) {
            return;
        }
        BatchTransactionJournal journal = BatchTransactionCoordinator.journal(server);
        for (BatchTransactionRecord record : journal.pending(MAX_RECORDS_PER_PASS)) {
            reconcile(server, journal, record, gameTick);
        }
    }

    public static void clearRuntimeState() {
        LOGGED_UNRESOLVED.clear();
    }

    private static void reconcile(
            MinecraftServer server,
            BatchTransactionJournal journal,
            BatchTransactionRecord record,
            long gameTick) {
        var adapter = PatternBatchV2Api.adapter(record.adapterId()).orElse(null);
        var source = PatternBatchV2Api.source(record.sourceId()).orElse(null);
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, record.dimensionId()));
        if (adapter == null || source == null || level == null) {
            logOnce(record, "adapter, source, or dimension is unavailable");
            return;
        }
        try {
            BatchRecoveryResult target = adapter.reconcileTarget(level, record);
            switch (target.targetState()) {
                case RETRY -> {
                    return;
                }
                case QUARANTINE -> quarantine(journal, record, gameTick, target.detail());
                case NOT_ACCEPTED -> reconcileNotAccepted(
                        journal, record, gameTick, adapter, source, level);
                case ACCEPTED -> reconcileAccepted(
                        journal, record, gameTick, adapter, source, level, target);
            }
        } catch (Throwable throwable) {
            logOnce(record, "recovery threw " + throwable);
        }
    }

    private static void reconcileNotAccepted(
            BatchTransactionJournal journal,
            BatchTransactionRecord record,
            long gameTick,
            com.syaru.ae2craftingoptimizer.api.batch.v2.TransactionalPatternBatchAdapter adapter,
            com.syaru.ae2craftingoptimizer.api.batch.v2.BatchSourceReconciler source,
            ServerLevel level) {
        if (record.phase() == BatchTransactionPhase.TARGET_ACCEPTED
                || record.acceptedExecutions() > 0L) {
            quarantine(journal, record, gameTick, "target rejected a transaction recorded as accepted");
            return;
        }
        finishSourceRecovery(
                journal,
                record,
                gameTick,
                adapter,
                source.rollbackPrepared(level, record),
                source,
                level,
                BatchTransactionPhase.ROLLED_BACK);
    }

    private static void reconcileAccepted(
            BatchTransactionJournal journal,
            BatchTransactionRecord record,
            long gameTick,
            com.syaru.ae2craftingoptimizer.api.batch.v2.TransactionalPatternBatchAdapter adapter,
            com.syaru.ae2craftingoptimizer.api.batch.v2.BatchSourceReconciler source,
            ServerLevel level,
            BatchRecoveryResult target) {
        long accepted = target.acceptedExecutions();
        if (accepted != record.offeredExecutions()) {
            quarantine(journal, record, gameTick, "V2 target receipt is not all-or-zero");
            return;
        }
        BatchTransactionRecord acceptedRecord = record;
        if (record.phase() == BatchTransactionPhase.PREPARED
                || record.phase() == BatchTransactionPhase.RECONCILIATION_REQUIRED) {
            acceptedRecord = journal.transition(
                    record.id(),
                    BatchTransactionPhase.TARGET_ACCEPTED,
                    accepted,
                    gameTick,
                    record.adapterData(),
                    record.receipt().isEmpty() ? target.detail() : record.receipt());
        } else if (record.phase() != BatchTransactionPhase.TARGET_ACCEPTED
                || record.acceptedExecutions() != accepted) {
            quarantine(journal, record, gameTick, "journal and target acceptance counts disagree");
            return;
        }
        finishSourceRecovery(
                journal,
                acceptedRecord,
                gameTick,
                adapter,
                source.accountAccepted(level, acceptedRecord),
                source,
                level,
                BatchTransactionPhase.ACCOUNTED);
    }

    private static void finishSourceRecovery(
            BatchTransactionJournal journal,
            BatchTransactionRecord record,
            long gameTick,
            com.syaru.ae2craftingoptimizer.api.batch.v2.TransactionalPatternBatchAdapter adapter,
            SourceRecoveryResult result,
            com.syaru.ae2craftingoptimizer.api.batch.v2.BatchSourceReconciler source,
            ServerLevel level,
            BatchTransactionPhase successPhase) {
        switch (result) {
            case RETRY -> {
                return;
            }
            case QUARANTINE -> quarantine(journal, record, gameTick, "source reconciler quarantined transaction");
            case COMPLETE -> {
                long accepted = successPhase == BatchTransactionPhase.ACCOUNTED
                        ? record.acceptedExecutions()
                        : 0L;
                BatchTransactionRecord terminalRecord = journal.transition(
                        record.id(),
                        successPhase,
                        accepted,
                        gameTick,
                        record.adapterData(),
                        record.receipt());
                journal.removeTerminal(record.id());
                forgetResolved(adapter, source, level, terminalRecord);
                LOGGED_UNRESOLVED.remove(record.id());
            }
        }
    }

    private static void forgetResolved(
            com.syaru.ae2craftingoptimizer.api.batch.v2.TransactionalPatternBatchAdapter adapter,
            com.syaru.ae2craftingoptimizer.api.batch.v2.BatchSourceReconciler source,
            ServerLevel level,
            BatchTransactionRecord record) {
        try {
            source.forgetResolvedSource(level, record);
        } catch (Throwable cleanupFailure) {
            AE2CraftingOptimizer.LOGGER.warn(
                    "ACO retained a recovered terminal source receipt {}: {}",
                    record.id(), cleanupFailure.toString());
        }
        try {
            adapter.forgetResolvedTarget(level, record);
        } catch (Throwable cleanupFailure) {
            AE2CraftingOptimizer.LOGGER.warn(
                    "ACO retained a recovered terminal target receipt {}: {}",
                    record.id(), cleanupFailure.toString());
        }
    }

    private static void quarantine(
            BatchTransactionJournal journal,
            BatchTransactionRecord record,
            long gameTick,
            String detail) {
        CompoundTag data = record.adapterData();
        data.putString("quarantineReason", detail);
        journal.transition(
                record.id(),
                BatchTransactionPhase.QUARANTINED,
                record.acceptedExecutions(),
                gameTick,
                data,
                record.receipt());
        AE2CraftingOptimizer.LOGGER.error(
                "ACO quarantined V2 batch transaction {}: {}. The record remains in SavedData for manual inspection.",
                record.id(), detail);
    }

    private static void logOnce(BatchTransactionRecord record, String detail) {
        if (LOGGED_UNRESOLVED.add(record.id())) {
            AE2CraftingOptimizer.LOGGER.warn(
                    "ACO cannot yet reconcile V2 batch transaction {} in phase {}: {}",
                    record.id(), record.phase(), detail);
        }
    }
}
