package com.syaru.ae2craftingoptimizer.mixin;

import org.spongepowered.asm.mixin.injection.Redirect;
import com.syaru.ae2craftingoptimizer.integration.ExactSubmissionScope;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.access.AdvancedAeExactCraftingJobAccess;
import com.syaru.ae2craftingoptimizer.access.AdvancedAeExactCraftingLogicAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU;
import net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * BigInteger SidecarをAdvanced AE標準Jobの保存・完了・取消経路へ接続する。
 *
 * <p>Exact JobのPattern配送だけは物理作業台Managerが所有するため、Advanced AE標準executorへ
 * 同じlong互換タスクを再配送させない。</p>
 */
@Pseudo
@Mixin(
        targets = "net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic",
        remap = false,
        priority = 1100)
public abstract class AdvancedAeExactCraftingLogicMixin
        implements AdvancedAeExactCraftingLogicAccess {
    @Shadow
    private ExecutingCraftingJob job;

    @Shadow
    @Final
    private AdvCraftingCPU cpu;

    @Override
    @Invoker("finishJob")
    public abstract void aco$finishExactJob(boolean successful);

    @Inject(
            method = "executeCrafting",
            at = @At("HEAD"),
            cancellable = true,
            require = 1)
    private void aco$keepExactJobOnPhysicalExecutor(
            int maxPatterns,
            CraftingService craftingService,
            IEnergyService energyService,
            Level level,
            CallbackInfoReturnable<Integer> cir) {
        // Exact Jobだけを止め、通常Advanced AE Jobは既存executorへそのまま渡す。
        if (job instanceof AdvancedAeExactCraftingJobAccess exact
                && exact.aco$isExactJob()) {
            cir.setReturnValue(0);
        }
    }

    @Inject(
            method = "insert",
            at = @At("HEAD"),
            cancellable = true,
            require = 1)
    private void aco$rejectUnreceiptedExactOutput(
            AEKey key,
            long amount,
            Actionable mode,
            CallbackInfoReturnable<Long> cir) {
        /*
         * Exact Jobの出力は物理Transaction Receiptで所有権を証明してから、
         * 同じ実JobのwaitingForとremainingAmountへ反映する。
         * 通常CraftingServiceから同名素材を受け取ると、未証明出力との二重会計になる。
         */
        if (job instanceof AdvancedAeExactCraftingJobAccess exact
                && exact.aco$isExactJob()) {
            cir.setReturnValue(0L);
        }
    }

    @Inject(
            method = "writeToNBT",
            at = @At("RETURN"),
            require = 1)
    private void aco$saveExactJob(
            CompoundTag owner,
            CallbackInfo ci) {
        if (!(job instanceof AdvancedAeExactCraftingJobAccess exact)
                || !exact.aco$isExactJob()) {
            return;
        }
        CompoundTag jobTag = owner.getCompound("job");
        exact.aco$writeExactState(jobTag);
        owner.put("job", jobTag);
    }

    @Inject(
            method = "readFromNBT",
            at = @At("RETURN"),
            require = 1)
    private void aco$loadExactJob(
            CompoundTag owner,
            CallbackInfo ci) {
        if (job instanceof AdvancedAeExactCraftingJobAccess exact) {
            exact.aco$loadExactState(owner.getCompound("job"));
        }
    }

    @Inject(
            method = "cancel",
            at = @At("HEAD"),
            cancellable = true,
            require = 1)
    private void aco$cancelExactJobSafely(CallbackInfo ci) {
        if (!(job instanceof AdvancedAeExactCraftingJobAccess exact)
                || !exact.aco$isExactJob()) {
            return;
        }
        var state = exact.aco$getExactState();
        if (state == null) {
            throw new IllegalStateException(
                    "Exact Advanced AE job lost its accounting state");
        }
        /*
         * 物理ThreadまたはEscrowが存在するJobはManagerへ取消要求だけを渡す。
         * Advanced AE標準cancelでJobを先に消すと、未返却入力の所有者が失われる。
         */
        if (state.hasPhysicalExecution()) {
            state.requestCancellation();
            cpu.markDirty();
            ci.cancel();
            return;
        }
        // 入力へ触る前なら、Advanced AE本来のlink通知・Job破棄をそのまま使用できる。
        aco$finishExactJob(false);
        cpu.updateOutput(null);
        ci.cancel();
    }

    /**
     * ACOが承認済みのwide提出だけ、CPUのlong容量ゲートを通す。
     *
     * <p>{@code trySubmitJob}は先頭で{@code getAvailableStorage() < plan.bytes()}を見る。
     * wide計画のlong Facadeは常に{@code Long.MAX_VALUE}なので、残容量がlong上限
     * ぴったりでない限り<b>必ず不成立</b>になり、理由の出ないCPU_TOO_SMALLで返る。
     * 正確な容量判定は提出前にBigInteger台帳へ対して済ませてあるので、ここでは測り直さない。
     * ACOの提出でない計画には一切触れない。</p>
     */
    @Redirect(
            method = "trySubmitJob",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/networking/crafting/ICraftingPlan;bytes()J",
                    ordinal = 0),
            require = 1)
    private long aco$skipLongCapacityGateForApprovedPlans(ICraftingPlan plan) {
        // ACO承認済みの一回分Facadeだけ0バイト扱いにする。
        if (ExactSubmissionScope.owns(plan)) {
            return 0L;
        }
        return plan.bytes();
    }
}
