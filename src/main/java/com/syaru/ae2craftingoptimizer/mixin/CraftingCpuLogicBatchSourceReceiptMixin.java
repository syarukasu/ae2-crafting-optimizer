package com.syaru.ae2craftingoptimizer.mixin;

import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ICraftingInventory;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.api.stacks.GenericStack;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.syaru.ae2craftingoptimizer.access.CraftingLogicTransactionAccess;
import com.syaru.ae2craftingoptimizer.access.CraftingOwnerTransactionAccess;
import com.syaru.ae2craftingoptimizer.api.batch.v2.BatchSourceReceipt;
import com.syaru.ae2craftingoptimizer.api.batch.v2.BatchSourceReceiptStore;
import com.syaru.ae2craftingoptimizer.batch.BatchSourceReceiptLedger;
import com.syaru.ae2craftingoptimizer.scheduler.FairSchedulerPersistentState;
import com.syaru.ae2craftingoptimizer.scheduler.FairSchedulerStateStore;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CraftingCpuLogic.class, remap = false)
public abstract class CraftingCpuLogicBatchSourceReceiptMixin
        implements BatchSourceReceiptStore, FairSchedulerStateStore, CraftingLogicTransactionAccess {
    @Shadow
    @Final
    private CraftingCPUCluster cluster;

    @Shadow
    private ExecutingCraftingJob job;

    @Shadow
    @Final
    private ListCraftingInventory inventory;
    @Unique
    private final BatchSourceReceiptLedger aco$batchSourceReceipts = new BatchSourceReceiptLedger();

    @Unique
    private final FairSchedulerPersistentState aco$fairSchedulerState = new FairSchedulerPersistentState();

    @Inject(method = "writeToNBT", at = @At("RETURN"))
    private void aco$saveBatchSourceReceipts(CompoundTag tag, CallbackInfo ci) {
        if (!aco$batchSourceReceipts.isEmpty()) {
            tag.put("acoBatchSourceReceipts", aco$batchSourceReceipts.save());
        }
        if (aco$fairSchedulerState.initialized()) {
            tag.put("acoFairScheduler", aco$fairSchedulerState.save());
        }
    }

    @Inject(method = "readFromNBT", at = @At("RETURN"))
    private void aco$loadBatchSourceReceipts(CompoundTag tag, CallbackInfo ci) {
        aco$batchSourceReceipts.load(tag.getCompound("acoBatchSourceReceipts"));
        aco$fairSchedulerState.load(tag.getCompound("acoFairScheduler"));
    }

    @Override
    public boolean aco$isBatchSourceReceiptLedgerHealthy() {
        return aco$batchSourceReceipts.isHealthy();
    }

    @Override
    public boolean aco$hasAnyUnresolvedBatchSourceReceipt() {
        return aco$batchSourceReceipts.hasUnresolved();
    }

    @Override
    public BatchSourceReceipt aco$getBatchSourceReceipt(UUID transactionId) {
        return aco$batchSourceReceipts.get(transactionId);
    }

    @Override
    public boolean aco$hasUnresolvedBatchSourceReceipt(String taskFingerprint) {
        return aco$batchSourceReceipts.hasUnresolved(taskFingerprint);
    }

    @Override
    public boolean aco$stageBatchSourceReceipt(BatchSourceReceipt receipt) {
        return aco$batchSourceReceipts.stage(receipt);
    }

    @Override
    public void aco$recordExtractedBatchSourceInput(
            UUID transactionId,
            GenericStack extracted,
            long updatedTick) {
        aco$batchSourceReceipts.recordExtraction(transactionId, extracted, updatedTick);
    }

    @Override
    public void aco$advanceBatchSourceReceipt(
            UUID transactionId,
            BatchSourceReceipt.State state,
            int accountedOutputs,
            long updatedTick) {
        aco$batchSourceReceipts.advance(transactionId, state, accountedOutputs, updatedTick);
    }

    @Override
    public boolean aco$removeTerminalBatchSourceReceipt(UUID transactionId) {
        return aco$batchSourceReceipts.removeTerminal(transactionId);
    }

    @Override
    public FairSchedulerPersistentState aco$getFairSchedulerState() {
        return aco$fairSchedulerState;
    }

    @Override
    public Object aco$getExecutingJob() {
        return job;
    }

    @Override
    public ICraftingInventory aco$getCraftingInventory() {
        return inventory;
    }

    @Override
    public CraftingOwnerTransactionAccess aco$getCraftingOwner() {
        return (CraftingOwnerTransactionAccess) (Object) cluster;
    }
}
