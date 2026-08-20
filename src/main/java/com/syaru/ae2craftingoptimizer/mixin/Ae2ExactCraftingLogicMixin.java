package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.access.ExactCraftingJobAccess;
import com.syaru.ae2craftingoptimizer.access.ExactCraftingLogicAccess;
import com.syaru.ae2craftingoptimizer.integration.Ae2BigCraftingExecutionManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Issue #115: 標準AE2 exact Jobを保存・取消・物理実行境界へ接続する。 */
@Mixin(value = CraftingCpuLogic.class, remap = false, priority = 1200)
public abstract class Ae2ExactCraftingLogicMixin implements ExactCraftingLogicAccess {
    @Shadow
    private ExecutingCraftingJob job;

    @Shadow
    @Final
    private CraftingCPUCluster cluster;

    @Override
    @Invoker("finishJob")
    public abstract void aco$finishExactJob(boolean successful);

    @Inject(method = "executeCrafting", at = @At("HEAD"), cancellable = true, require = 1)
    private void aco$keepExactJobOnPhysicalExecutor(
            int maxPatterns,
            CraftingService craftingService,
            IEnergyService energyService,
            Level level,
            CallbackInfoReturnable<Integer> cir) {
        // exact Jobだけを止め、通常AE2 Jobと他アドオンの通常実行は変更しない。
        if (job instanceof ExactCraftingJobAccess exact && exact.aco$isExactJob()) {
            cir.setReturnValue(0);
        }
    }

    @Inject(method = "insert", at = @At("HEAD"), cancellable = true, require = 1)
    private void aco$rejectUnreceiptedExactOutput(
            AEKey key,
            long amount,
            Actionable mode,
            CallbackInfoReturnable<Long> cir) {
        /* exact出力は物理Receiptで証明後に同じJobへ反映し、通常挿入との二重会計を防ぐ。 */
        if (job instanceof ExactCraftingJobAccess exact && exact.aco$isExactJob()) {
            cir.setReturnValue(0L);
        }
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"), require = 1)
    private void aco$saveExactJob(
            CompoundTag owner,
            CallbackInfo ci) {
        if (!(job instanceof ExactCraftingJobAccess exact) || !exact.aco$isExactJob()) {
            return;
        }
        CompoundTag jobTag = owner.getCompound("job");
        exact.aco$writeExactState(jobTag);
        owner.put("job", jobTag);
    }

    @Inject(method = "readFromNBT", at = @At("RETURN"), require = 1)
    private void aco$loadExactJob(
            CompoundTag owner,
            CallbackInfo ci) {
        if (!(job instanceof ExactCraftingJobAccess exact)) {
            return;
        }
        exact.aco$loadExactState(owner.getCompound("job"));
        // 保存済みexact Jobだけを再登録し、通常Jobのtick経路を増やさない。
        if (exact.aco$isExactJob()) {
            Ae2BigCraftingExecutionManager.register(cluster);
        }
    }

    @Inject(method = "cancel", at = @At("HEAD"), cancellable = true, require = 1)
    private void aco$cancelExactJobSafely(CallbackInfo ci) {
        if (!(job instanceof ExactCraftingJobAccess exact) || !exact.aco$isExactJob()) {
            return;
        }
        var state = exact.aco$getExactState();
        if (state == null) {
            throw new IllegalStateException("Exact AE2 job lost its accounting state");
        }
        /* 物理所有権取得後はManagerへ取消要求だけを渡し、Escrow所有者を先に消さない。 */
        if (state.hasPhysicalExecution()) {
            state.requestCancellation();
            cluster.markDirty();
            ci.cancel();
            return;
        }
        // 入力へ触る前ならAE2本来のLink通知とJob破棄をそのまま使える。
        aco$finishExactJob(false);
        cluster.updateOutput(null);
        Ae2BigCraftingExecutionManager.unregister(cluster);
        ci.cancel();
    }
}
