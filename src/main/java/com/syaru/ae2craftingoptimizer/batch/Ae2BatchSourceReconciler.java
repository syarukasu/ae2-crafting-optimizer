package com.syaru.ae2craftingoptimizer.batch;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.energy.IEnergyService;
import appeng.crafting.inv.ICraftingInventory;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.api.batch.v2.BatchSourceReceipt;
import com.syaru.ae2craftingoptimizer.api.batch.v2.BatchSourceReceiptStore;
import com.syaru.ae2craftingoptimizer.api.batch.v2.BatchSourceReconciler;
import com.syaru.ae2craftingoptimizer.api.batch.v2.SourceRecoveryResult;
import com.syaru.ae2craftingoptimizer.access.CraftingClusterHostTransactionAccess;
import com.syaru.ae2craftingoptimizer.access.CraftingJobTransactionAccess;
import com.syaru.ae2craftingoptimizer.access.CraftingLogicTransactionAccess;
import com.syaru.ae2craftingoptimizer.access.CraftingOwnerTransactionAccess;
import com.syaru.ae2craftingoptimizer.access.CraftingTaskProgressAccess;
import com.syaru.ae2craftingoptimizer.transaction.BatchTransactionRecord;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

public final class Ae2BatchSourceReconciler implements BatchSourceReconciler {
    public static final Ae2BatchSourceReconciler INSTANCE = new Ae2BatchSourceReconciler();
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            AE2CraftingOptimizer.MODID, "ae2_crafting_cpu");

    private Ae2BatchSourceReconciler() {
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    public static CompoundTag createSourceData(
            Object logic,
            Object owner,
            IPatternDetails details,
            double power,
            long taskProgressBefore) {
        if (!Double.isFinite(power) || power < 0.0D) {
            throw new IllegalArgumentException("source power must be finite and non-negative");
        }
        if (taskProgressBefore <= 0L) {
            throw new IllegalArgumentException("source task progress must be positive");
        }
        if (!(logic instanceof CraftingLogicTransactionAccess)
                || !(owner instanceof CraftingOwnerTransactionAccess ownerAccess)) {
            throw new IllegalStateException("ACO transaction access mixins are missing from the crafting CPU");
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", 1);
        tag.putString("logicClass", logic.getClass().getName());
        tag.putString("task", PatternTaskFingerprint.of(details));
        tag.putDouble("power", power);
        tag.putLong("taskProgressBefore", taskProgressBefore);
        tag.putString("ownerKind", ownerAccess.aco$getCraftingOwnerKind());
        UUID ownerId = ownerAccess.aco$getCraftingOwnerId();
        if (ownerId != null) {
            tag.putUUID("cpuId", ownerId);
        }
        tag.putLong("clusterPos", ownerAccess.aco$getCraftingClusterPosition().asLong());
        return tag;
    }

    @Nullable
    public static BatchSourceReceiptStore receiptStore(Object logic) {
        return logic instanceof BatchSourceReceiptStore store ? store : null;
    }

    @Override
    public SourceRecoveryResult rollbackPrepared(ServerLevel level, BatchTransactionRecord record) {
        CompoundTag sourceData = record.sourceData();
        if (!validSourceData(sourceData)) {
            return SourceRecoveryResult.QUARANTINE;
        }
        LocatedSource source = locate(level, sourceData);
        if (source == null) {
            return SourceRecoveryResult.RETRY;
        }
        if (!source.receipts().aco$isBatchSourceReceiptLedgerHealthy()) {
            return SourceRecoveryResult.QUARANTINE;
        }
        BatchSourceReceipt receipt = source.receipts().aco$getBatchSourceReceipt(record.id());
        if (receipt == null) {
            return SourceRecoveryResult.QUARANTINE;
        }
        if (record.acceptedExecutions() != 0L
                || receipt.executions() != record.offeredExecutions()
                || !receipt.taskFingerprint().equals(sourceData.getString("task"))) {
            return SourceRecoveryResult.QUARANTINE;
        }
        if (receipt.state() == BatchSourceReceipt.State.ROLLED_BACK) {
            return SourceRecoveryResult.COMPLETE;
        }
        if (receipt.state() == BatchSourceReceipt.State.EXTRACTING) {
            // Some inputs may already have moved, but the completed aggregate was
            // never durably recorded. Re-inserting the full aggregate could duplicate.
            return SourceRecoveryResult.QUARANTINE;
        }
        if (receipt.state() != BatchSourceReceipt.State.STAGED
                && receipt.state() != BatchSourceReceipt.State.EXTRACTED) {
            return SourceRecoveryResult.QUARANTINE;
        }
        try {
            if (receipt.state() == BatchSourceReceipt.State.EXTRACTED) {
                ICraftingInventory inventory = inventory(source.logic());
                for (var stack : record.extractedInputs()) {
                    inventory.insert(stack.what(), stack.amount(), Actionable.MODULATE);
                }
            }
            source.receipts().aco$advanceBatchSourceReceipt(
                    record.id(), BatchSourceReceipt.State.ROLLED_BACK, 0, level.getGameTime());
            source.owner().aco$markCraftingOwnerDirty();
            return SourceRecoveryResult.COMPLETE;
        } catch (RuntimeException exception) {
            return SourceRecoveryResult.QUARANTINE;
        }
    }

    @Override
    public SourceRecoveryResult accountAccepted(ServerLevel level, BatchTransactionRecord record) {
        CompoundTag sourceData = record.sourceData();
        if (!validSourceData(sourceData)) {
            return SourceRecoveryResult.QUARANTINE;
        }
        LocatedSource source = locate(level, sourceData);
        if (source == null) {
            return SourceRecoveryResult.RETRY;
        }
        if (!source.receipts().aco$isBatchSourceReceiptLedgerHealthy()) {
            return SourceRecoveryResult.QUARANTINE;
        }
        BatchSourceReceipt receipt = source.receipts().aco$getBatchSourceReceipt(record.id());
        if (receipt == null) {
            return SourceRecoveryResult.QUARANTINE;
        }
        if (record.acceptedExecutions() <= 0L
                || record.acceptedExecutions() != record.offeredExecutions()
                || receipt.executions() != record.acceptedExecutions()
                || !receipt.taskFingerprint().equals(sourceData.getString("task"))
                || receipt.accountedOutputs() > record.expectedOutputs().size()
                || sourceData.getLong("taskProgressBefore") < record.acceptedExecutions()
                || !Double.isFinite(sourceData.getDouble("power"))
                || sourceData.getDouble("power") < 0.0D) {
            return SourceRecoveryResult.QUARANTINE;
        }
        try {
            if (receipt.state() == BatchSourceReceipt.State.ACCOUNTED) {
                return receipt.accountedOutputs() == record.expectedOutputs().size()
                        ? SourceRecoveryResult.COMPLETE
                        : SourceRecoveryResult.QUARANTINE;
            }
            if (receipt.state() == BatchSourceReceipt.State.EXTRACTED) {
                source.receipts().aco$advanceBatchSourceReceipt(
                        record.id(), BatchSourceReceipt.State.TARGET_ACCEPTED, 0, level.getGameTime());
                source.owner().aco$markCraftingOwnerDirty();
                receipt = source.receipts().aco$getBatchSourceReceipt(record.id());
            }
            if (receipt == null
                    || receipt.state() == BatchSourceReceipt.State.STAGED
                    || receipt.state() == BatchSourceReceipt.State.ROLLED_BACK) {
                return SourceRecoveryResult.QUARANTINE;
            }
            if (receipt.state() == BatchSourceReceipt.State.ENERGY_ACCOUNTING) {
                // The previous call may have charged some or all power before it failed.
                // Replaying that side effect would risk charging the batch twice.
                return SourceRecoveryResult.QUARANTINE;
            }
            if (receipt.state() == BatchSourceReceipt.State.OUTPUT_ACCOUNTING) {
                // The output insertion may have completed before its cursor was
                // persisted. Replaying it could duplicate an accepted output.
                return SourceRecoveryResult.QUARANTINE;
            }

            if (receipt.state() == BatchSourceReceipt.State.TARGET_ACCEPTED) {
                source.receipts().aco$advanceBatchSourceReceipt(
                        record.id(), BatchSourceReceipt.State.ENERGY_ACCOUNTING, 0, level.getGameTime());
                source.owner().aco$markCraftingOwnerDirty();
                receipt = source.receipts().aco$getBatchSourceReceipt(record.id());
            }

            if (receipt.state() == BatchSourceReceipt.State.ENERGY_ACCOUNTING) {
                chargePower(source.owner(), sourceData.getDouble("power"));
                source.receipts().aco$advanceBatchSourceReceipt(
                        record.id(), BatchSourceReceipt.State.ENERGY_ACCOUNTED, 0, level.getGameTime());
                source.owner().aco$markCraftingOwnerDirty();
                receipt = source.receipts().aco$getBatchSourceReceipt(record.id());
            }

            Object job = source.logic().aco$getExecutingJob();
            if (job == null) {
                return SourceRecoveryResult.RETRY;
            }
            if (!(job instanceof CraftingJobTransactionAccess jobAccess)) {
                return SourceRecoveryResult.QUARANTINE;
            }
            if (receipt.state() == BatchSourceReceipt.State.ENERGY_ACCOUNTED) {
                Map<IPatternDetails, Object> tasks = jobAccess.aco$getTasks();
                Map.Entry<IPatternDetails, Object> task = findTask(tasks, sourceData.getString("task"));
                if (task == null) {
                    return SourceRecoveryResult.QUARANTINE;
                }
                if (!(task.getValue() instanceof CraftingTaskProgressAccess progress)) {
                    return SourceRecoveryResult.QUARANTINE;
                }
                long remaining = progress.aco$getTaskProgress();
                long before = sourceData.getLong("taskProgressBefore");
                long next = before - record.acceptedExecutions();
                if (remaining != before && remaining != next) {
                    return SourceRecoveryResult.QUARANTINE;
                }
                if (remaining == before) {
                    progress.aco$setTaskProgress(next);
                    if (next == 0L) {
                        tasks.remove(task.getKey());
                    }
                }
                source.receipts().aco$advanceBatchSourceReceipt(
                        record.id(), BatchSourceReceipt.State.PROGRESS_ACCOUNTED, 0, level.getGameTime());
                source.owner().aco$markCraftingOwnerDirty();
                receipt = source.receipts().aco$getBatchSourceReceipt(record.id());
            }

            if (receipt.state() == BatchSourceReceipt.State.PROGRESS_ACCOUNTED) {
                source.receipts().aco$advanceBatchSourceReceipt(
                        record.id(), BatchSourceReceipt.State.OUTPUTS_ACCOUNTING, 0, level.getGameTime());
                source.owner().aco$markCraftingOwnerDirty();
                receipt = source.receipts().aco$getBatchSourceReceipt(record.id());
            }

            if (receipt.state() != BatchSourceReceipt.State.OUTPUTS_ACCOUNTING) {
                return SourceRecoveryResult.QUARANTINE;
            }
            ICraftingInventory waitingFor = jobAccess.aco$getWaitingFor();
            for (int index = receipt.accountedOutputs(); index < record.expectedOutputs().size(); index++) {
                var output = record.expectedOutputs().get(index);
                source.receipts().aco$advanceBatchSourceReceipt(
                        record.id(), BatchSourceReceipt.State.OUTPUT_ACCOUNTING, index, level.getGameTime());
                source.owner().aco$markCraftingOwnerDirty();
                waitingFor.insert(output.what(), output.amount(), Actionable.MODULATE);
                source.receipts().aco$advanceBatchSourceReceipt(
                        record.id(), BatchSourceReceipt.State.OUTPUTS_ACCOUNTING, index + 1, level.getGameTime());
                source.owner().aco$markCraftingOwnerDirty();
            }
            source.receipts().aco$advanceBatchSourceReceipt(
                    record.id(),
                    BatchSourceReceipt.State.ACCOUNTED,
                    record.expectedOutputs().size(),
                    level.getGameTime());
            source.owner().aco$markCraftingOwnerDirty();
            return SourceRecoveryResult.COMPLETE;
        } catch (RuntimeException exception) {
            return SourceRecoveryResult.QUARANTINE;
        }
    }

    @Override
    public void forgetResolvedSource(ServerLevel level, BatchTransactionRecord record) {
        LocatedSource source = locate(level, record.sourceData());
        if (source != null
                && source.receipts().aco$removeTerminalBatchSourceReceipt(record.id())) {
            source.owner().aco$markCraftingOwnerDirty();
        }
    }

    @Nullable
    private static LocatedSource locate(ServerLevel level, CompoundTag data) {
        try {
            var blockEntity = level.getBlockEntity(net.minecraft.core.BlockPos.of(data.getLong("clusterPos")));
            if (!(blockEntity instanceof CraftingClusterHostTransactionAccess host)) {
                return null;
            }
            var cluster = host.aco$getCraftingClusterForRecovery();
            if (cluster == null) {
                return null;
            }
            UUID ownerId = data.hasUUID("cpuId") ? data.getUUID("cpuId") : null;
            CraftingOwnerTransactionAccess owner =
                    cluster.aco$findCraftingOwner(data.getString("ownerKind"), ownerId);
            if (owner == null || !(owner.aco$getCraftingLogic() instanceof CraftingLogicTransactionAccess logic)) {
                return null;
            }
            BatchSourceReceiptStore receipts = receiptStore(logic);
            return receipts == null ? null : new LocatedSource(logic, owner, receipts);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Map.Entry<IPatternDetails, Object> findTask(
            Map<IPatternDetails, Object> tasks,
            String fingerprint) {
        for (Map.Entry<IPatternDetails, Object> task : tasks.entrySet()) {
            if (PatternTaskFingerprint.of(task.getKey()).equals(fingerprint)) {
                return task;
            }
        }
        return null;
    }

    private static ICraftingInventory inventory(CraftingLogicTransactionAccess logic) {
        return logic.aco$getCraftingInventory();
    }

    private static void chargePower(CraftingOwnerTransactionAccess owner, double power) {
        if (power <= 0.0D) {
            return;
        }
        var grid = owner.aco$getCraftingGrid();
        if (grid == null) {
            throw new IllegalStateException("crafting CPU grid is unavailable during batch accounting");
        }
        double extracted = grid.getEnergyService().extractAEPower(
                power, Actionable.MODULATE, PowerMultiplier.CONFIG);
        double tolerance = Math.max(1.0E-9D, Math.ulp(power) * 8.0D);
        if (!Double.isFinite(extracted) || extracted + tolerance < power) {
            throw new IllegalStateException("AE2 energy service could not account the accepted batch exactly");
        }
    }

    private static boolean validSourceData(CompoundTag data) {
        String ownerKind = data.getString("ownerKind");
        double power = data.getDouble("power");
        return data.contains("schema", Tag.TAG_INT)
                && data.getInt("schema") == 1
                && data.contains("logicClass", Tag.TAG_STRING)
                && !data.getString("logicClass").isEmpty()
                && data.contains("task", Tag.TAG_STRING)
                && !data.getString("task").isEmpty()
                && data.contains("clusterPos", Tag.TAG_LONG)
                && data.contains("taskProgressBefore", Tag.TAG_LONG)
                && data.getLong("taskProgressBefore") > 0L
                && data.contains("power", Tag.TAG_DOUBLE)
                && Double.isFinite(power)
                && power >= 0.0D
                && data.contains("ownerKind", Tag.TAG_STRING)
                && (ownerKind.equals("ae2")
                        || (ownerKind.equals("advanced_ae") && data.hasUUID("cpuId")));
    }

    private record LocatedSource(
            CraftingLogicTransactionAccess logic,
            CraftingOwnerTransactionAccess owner,
            BatchSourceReceiptStore receipts) {
    }
}
