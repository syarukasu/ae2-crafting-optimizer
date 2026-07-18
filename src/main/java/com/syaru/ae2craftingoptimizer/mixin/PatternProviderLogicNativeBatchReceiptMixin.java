package com.syaru.ae2craftingoptimizer.mixin;

import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import com.syaru.ae2craftingoptimizer.access.PatternProviderTransactionAccess;
import com.syaru.ae2craftingoptimizer.api.batch.v2.NativeBatchReceipt;
import com.syaru.ae2craftingoptimizer.api.batch.v2.NativeBatchReceiptStore;
import com.syaru.ae2craftingoptimizer.batch.NativeBatchReceiptLedger;
import java.util.UUID;
import java.util.Collection;
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
    public abstract IConfigManager getConfigManager();

    @Shadow
    public abstract boolean isBlocking();

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
}
