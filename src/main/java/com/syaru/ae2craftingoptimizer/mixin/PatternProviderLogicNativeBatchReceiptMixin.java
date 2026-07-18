package com.syaru.ae2craftingoptimizer.mixin;

import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.helpers.patternprovider.PatternProviderTarget;
import appeng.api.config.Actionable;
import appeng.api.config.LockCraftingMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.access.PatternProviderTransactionAccess;
import com.syaru.ae2craftingoptimizer.api.batch.v2.BatchOwnershipProof;
import com.syaru.ae2craftingoptimizer.api.batch.v2.BatchPayloadFingerprint;
import com.syaru.ae2craftingoptimizer.api.batch.v2.NativeBatchReceipt;
import com.syaru.ae2craftingoptimizer.api.batch.v2.NativeBatchReceiptStore;
import com.syaru.ae2craftingoptimizer.api.batch.v2.PreparedPatternBatch;
import com.syaru.ae2craftingoptimizer.batch.NativeBatchReceiptLedger;
import java.util.UUID;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import appeng.api.util.IConfigManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PatternProviderLogic.class, remap = false)
public abstract class PatternProviderLogicNativeBatchReceiptMixin
        implements NativeBatchReceiptStore, PatternProviderTransactionAccess {
    @Unique
    private static final String ACO_RECEIPTS_TAG = "acoNativeBatchReceipts";

    @Unique
    private final NativeBatchReceiptLedger aco$nativeBatchReceipts = new NativeBatchReceiptLedger();

    @Shadow
    @Final
    private PatternProviderLogicHost host;

    @Shadow
    @Final
    private IManagedGridNode mainNode;

    @Shadow
    @Final
    private List<IPatternDetails> patterns;

    @Shadow
    @Final
    private List<GenericStack> sendList;

    @Shadow
    @Final
    private Set<AEKey> patternInputs;

    @Shadow
    private Direction sendDirection;

    @Shadow
    public abstract IConfigManager getConfigManager();

    @Shadow
    public abstract boolean isBlocking();

    @Shadow
    public abstract LockCraftingMode getCraftingLockedReason();

    @Shadow
    private void onPushPatternSuccess(IPatternDetails pattern) {
        throw new AssertionError();
    }

    @Shadow
    private PatternProviderTarget findAdapter(Direction direction) {
        throw new AssertionError();
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"))
    private void aco$writeNativeBatchReceipts(CompoundTag tag, CallbackInfo ci) {
        if (!aco$nativeBatchReceipts.isEmpty()) {
            tag.put(ACO_RECEIPTS_TAG, aco$nativeBatchReceipts.save());
        }
    }

    @Inject(method = "readFromNBT", at = @At("RETURN"))
    private void aco$readNativeBatchReceipts(CompoundTag tag, CallbackInfo ci) {
        aco$nativeBatchReceipts.load(tag.getCompound(ACO_RECEIPTS_TAG));
    }

    @Override
    public boolean aco$isNativeBatchReceiptLedgerHealthy() {
        return aco$nativeBatchReceipts.isHealthy();
    }

    @Override
    public NativeBatchReceipt aco$getNativeBatchReceipt(UUID transactionId) {
        return aco$nativeBatchReceipts.get(transactionId);
    }

    @Override
    public boolean aco$prepareNativeBatchReceipt(NativeBatchReceipt receipt) {
        boolean prepared = aco$nativeBatchReceipts.prepare(receipt);
        if (prepared) {
            ((PatternProviderLogic) (Object) this).saveChanges();
        }
        return prepared;
    }

    @Override
    public void aco$finishNativeBatchReceipt(
            UUID transactionId, NativeBatchReceipt.State state, long updatedTick) {
        aco$nativeBatchReceipts.finish(transactionId, state, updatedTick);
        ((PatternProviderLogic) (Object) this).saveChanges();
    }

    @Override
    public boolean aco$removeTerminalNativeBatchReceipt(UUID transactionId) {
        boolean removed = aco$nativeBatchReceipts.removeTerminal(transactionId);
        if (removed) {
            ((PatternProviderLogic) (Object) this).saveChanges();
        }
        return removed;
    }

    @Override
    public BlockEntity aco$getProviderBlockEntity() {
        return host.getBlockEntity();
    }

    @Override
    public Collection<Direction> aco$getProviderTargets() {
        return host.getTargets();
    }

    @Override
    public IConfigManager aco$getProviderConfigManager() {
        return getConfigManager();
    }

    @Override
    public boolean aco$isProviderBlocking() {
        return isBlocking();
    }

    @Override
    public synchronized BatchOwnershipProof aco$stageOwnedBatch(
            IPatternDetails pattern,
            Direction providerSide,
            PreparedPatternBatch prepared,
            String patternFingerprint,
            long gameTick) {
        String payloadDigest = BatchPayloadFingerprint.of(prepared);
        NativeBatchReceipt existing = aco$nativeBatchReceipts.get(prepared.transactionId());
        if (existing != null) {
            validateExistingOwnedReceipt(existing, prepared, patternFingerprint, payloadDigest);
            return existing.state() == NativeBatchReceipt.State.ACCEPTED
                    ? ownershipProof(existing)
                    : null;
        }
        if (!sendList.isEmpty()
                || sendDirection != null
                || !mainNode.isActive()
                || !patterns.contains(pattern)
                || getCraftingLockedReason() != LockCraftingMode.NONE
                || !host.getTargets().contains(providerSide)
                || prepared.aggregateInputs().isEmpty()) {
            return null;
        }
        PatternProviderTarget target = findAdapter(providerSide);
        if (target == null
                || (isBlocking() && target.containsPatternInput(patternInputs))
                || !aco$targetAcceptsInputs(target, prepared.aggregateInputs())) {
            return null;
        }

        NativeBatchReceipt accepted = new NativeBatchReceipt(
                prepared.transactionId(),
                NativeBatchReceipt.State.ACCEPTED,
                prepared.offeredExecutions(),
                patternFingerprint,
                payloadDigest,
                gameTick);
        boolean ownershipRecorded = false;
        try {
            sendList.addAll(prepared.aggregateInputs());
            sendDirection = providerSide;
            if (!aco$nativeBatchReceipts.acceptOwned(accepted)) {
                sendList.clear();
                sendDirection = null;
                return null;
            }
            ownershipRecorded = true;
            onPushPatternSuccess(pattern);
            ((PatternProviderLogic) (Object) this).saveChanges();
            mainNode.ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
            return ownershipProof(accepted);
        } catch (Throwable failure) {
            if (!ownershipRecorded) {
                sendList.clear();
                sendDirection = null;
            }
            // Receipt記録後はpayloadも同じOwnerにある。ここからrollbackすると二重投入の
            // 可能性が出るため、記録済み状態を残してJournal recoveryへ判断を委ねる。
            ((PatternProviderLogic) (Object) this).saveChanges();
            throw failure;
        }
    }

    @Unique
    private static void validateExistingOwnedReceipt(
            NativeBatchReceipt receipt,
            PreparedPatternBatch prepared,
            String patternFingerprint,
            String payloadDigest) {
        if (receipt.executions() != prepared.offeredExecutions()
                || !receipt.patternFingerprint().equals(patternFingerprint)
                || !receipt.payloadDigest().equals(payloadDigest)) {
            throw new IllegalStateException("native batch transaction id was reused with different content");
        }
        if (receipt.state() == NativeBatchReceipt.State.PENDING) {
            throw new IllegalStateException("native batch receipt is unresolved and cannot be replayed");
        }
    }

    @Unique
    private static BatchOwnershipProof ownershipProof(NativeBatchReceipt receipt) {
        if (!receipt.hasDurablePayloadProof()) {
            throw new IllegalStateException("legacy native receipt has no durable payload proof");
        }
        return new BatchOwnershipProof(
                receipt.transactionId(),
                receipt.executions(),
                receipt.payloadDigest(),
                "ae2-pattern-provider-send-buffer-v2");
    }

    @Unique
    private static boolean aco$targetAcceptsInputs(
            PatternProviderTarget target,
            List<GenericStack> inputs) {
        for (GenericStack input : inputs) {
            if (target.insert(input.what(), input.amount(), Actionable.SIMULATE) <= 0L) {
                return false;
            }
        }
        return true;
    }
}
