package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.access.AqeStandardVectorHost;
import com.syaru.ae2craftingoptimizer.integration.AqeStandardVectorExecutionRuntime;
import net.minecraft.nbt.CompoundTag;
import net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU;
import net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * AdvancedAE標準JobをACOの永続Exact Vector Runtimeへ接続する。
 */
@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic", remap = false)
public abstract class AdvancedAeStandardVectorExecutionMixin
        implements AqeStandardVectorHost {
    @Shadow
    @Final
    private AdvCraftingCPU cpu;

    @Shadow
    private ExecutingCraftingJob job;

    @Shadow
    @Final
    private ListCraftingInventory inventory;

    @Shadow(remap = false)
    public abstract long insert(
            AEKey key,
            long amount,
            Actionable actionable);

    @Unique
    private final AqeStandardVectorExecutionRuntime
            aco$standardVectorRuntime =
                    new AqeStandardVectorExecutionRuntime();

    @Inject(
            method = "tickCraftingLogic",
            at = @At("HEAD"),
            cancellable = true,
            require = 1)
    private void aco$holdOrphanedStandardVectorReceipt(
            IEnergyService energyService,
            CraftingService craftingService,
            CallbackInfo ci) {
        /*
         * finishJob後にReceiptだけ残る場合は通常storeItemsへ進ませず、
         * cross-chunk会計の証拠を保持して隔離する。
         */
        if (job == null
                && aco$standardVectorRuntime.hasUnresolvedState()
                && aco$standardVectorRuntime.tick(this)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "tickCraftingLogic",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/pedroksl/advanced_ae/common/cluster/AdvCraftingCPU;getCoProcessors()I"),
            cancellable = true,
            require = 1)
    private void aco$tickStandardVectorBeforePatternPush(
            IEnergyService energyService,
            CraftingService craftingService,
            CallbackInfo ci) {
        // trueはACO/AACが同じJobを所有中であり、通常Pattern Pushを重ねてはいけないことを示す。
        if (aco$standardVectorRuntime.tick(this)) {
            ci.cancel();
        }
    }

    @Inject(method = "cancel", at = @At("HEAD"), cancellable = true, require = 1)
    private void aco$cancelStandardVectorBeforeVanilla(
            CallbackInfo ci) {
        // 外部Receiptまたは入力Rollbackを証明できない場合は、vanilla cancelで状態を消さない。
        if (aco$standardVectorRuntime.interceptCancel(this)) {
            ci.cancel();
        }
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"), require = 1)
    private void aco$saveStandardVector(
            CompoundTag tag,
            CallbackInfo ci) {
        aco$standardVectorRuntime.save(tag);
    }

    @Inject(method = "readFromNBT", at = @At("RETURN"), require = 1)
    private void aco$loadStandardVector(
            CompoundTag tag,
            CallbackInfo ci) {
        aco$standardVectorRuntime.load(tag);
    }

    @Override
    public AdvCraftingCPU aco$getStandardVectorCpu() {
        return cpu;
    }

    @Override
    public Object aco$getStandardVectorJob() {
        return job;
    }

    @Override
    public ListCraftingInventory aco$getStandardVectorInventory() {
        return inventory;
    }

    @Override
    public long aco$insertStandardVectorOutput(
            AEKey key,
            long amount,
            Actionable actionable) {
        return insert(key, amount, actionable);
    }

    @Override
    public void aco$notifyStandardVectorTaskChanges() {
        // nullはAdvancedAEで全Task差分を一回だけ通知する既存の全体更新値。
        ((AdvancedAeCraftingCpuLogicIslandAccessor) (Object) this)
                .aco$invokePostChange(null);
    }

    @Override
    public void aco$markStandardVectorDirty() {
        cpu.markDirty();
    }
}
