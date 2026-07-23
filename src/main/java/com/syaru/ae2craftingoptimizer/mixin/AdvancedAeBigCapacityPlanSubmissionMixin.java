package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.crafting.execution.CraftingSubmitResult;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.access.BigCapacityPlanBoundaryAccess;
import com.syaru.ae2craftingoptimizer.api.big.BigCraftingHostRegistry;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.engine.BigCapacityCraftingPlan;
import com.syaru.ae2craftingoptimizer.engine.BigIntegerCraftingPlan;
import com.syaru.ae2craftingoptimizer.integration.AqeBigCraftingExecutionContext;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU;
import net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPUCluster;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 個別カウンタがlongに収まる大容量計画をAdvanced AEへ渡し、CPU容量予約だけを
 * ACO SidecarのBigInteger真値へ昇格する。
 */
@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPUCluster", remap = false)
public abstract class AdvancedAeBigCapacityPlanSubmissionMixin
        implements BigCapacityPlanBoundaryAccess {
    @Shadow
    @Final
    private HashMap<UUID, AdvCraftingCPU> activeCpus;

    /** 同じ計算スレッド上で、submitJobが追加したCPUだけを特定するための直前Snapshot。 */
    @Unique
    private static final ThreadLocal<SubmissionAttempt> ACO_SUBMISSION = new ThreadLocal<>();

    @Inject(method = "submitJob", at = @At("HEAD"), cancellable = true, require = 1)
    private void aco$validateBigCapacityPlan(
            IGrid grid,
            ICraftingPlan plan,
            IActionSource source,
            ICraftingRequester requester,
            CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        // 個別カウンタもlongを超える計画は、Advanced AE Jobを作らずBig親Jobへ移譲する。
        if (plan instanceof BigIntegerCraftingPlan bigIntegerPlan) {
            aco$submitBigIntegerParent(bigIntegerPlan, requester, cir);
            return;
        }
        // 通常AE2計画には一切介入せず、Big容量マーカーだけを対象にする。
        if (!(plan instanceof BigCapacityCraftingPlan bigPlan)) {
            return;
        }
        // 実験機能OFF、Missing計画、古いPattern世代は入力を抜く前に拒否する。
        if (!ACOConfig.enableAtomicBigCapacityPlans()
                || bigPlan.simulation()
                || !bigPlan.missingItems().isEmpty()
                || !bigPlan.generationsAreCurrent()) {
            cir.setReturnValue(CraftingSubmitResult.INCOMPLETE_PLAN);
            return;
        }
        var host = BigCraftingHostRegistry.find(this).orElse(null);
        BigInteger parentOwnedAllowance =
                AqeBigCraftingExecutionContext.exactAllowanceFor(this);
        /*
         * 親BigInteger Jobが既に予約した子Windowは空き容量を再要求しない。
         * 文脈に真値があるのにPlanと一致しない場合は、別Windowの取り違えとして拒否する。
         */
        boolean parentOwnedWindow = parentOwnedAllowance.signum() > 0;
        if (host == null
                || (parentOwnedWindow
                        && !parentOwnedAllowance.equals(bigPlan.exactBytes()))
                || (!parentOwnedWindow
                        && host.available().compareTo(bigPlan.exactBytes()) < 0)) {
            cir.setReturnValue(CraftingSubmitResult.CPU_TOO_SMALL);
            return;
        }
        ACO_SUBMISSION.set(new SubmissionAttempt(
                this,
                Set.copyOf(activeCpus.keySet()),
                parentOwnedWindow));
    }

    @Inject(method = "submitJob", at = @At("RETURN"), cancellable = true, require = 1)
    private void aco$promoteBigCapacityReservation(
            IGrid grid,
            ICraftingPlan plan,
            IActionSource source,
            ICraftingRequester requester,
            CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        // 通常計画のRETURNではThreadLocalにも容量台帳にも触れない。
        if (!(plan instanceof BigCapacityCraftingPlan bigPlan)) {
            return;
        }
        SubmissionAttempt attempt = ACO_SUBMISSION.get();
        ACO_SUBMISSION.remove();
        // HEADで拒否された計画、またはAdvanced AE側が拒否した計画には予約が存在しない。
        if (attempt == null
                || attempt.cluster() != this
                || cir.getReturnValue() == null
                || !cir.getReturnValue().successful()) {
            return;
        }

        Set<UUID> added = new LinkedHashSet<>(activeCpus.keySet());
        added.removeAll(attempt.activeCpuIds());
        // 一回のsubmitで追加CPUが一個と証明できない場合、全候補をキャンセルして誤予約を防ぐ。
        if (added.size() != 1) {
            for (UUID cpuId : added) {
                ((AdvCraftingCPUCluster) (Object) this).cancelJob(cpuId);
            }
            AE2CraftingOptimizer.LOGGER.error(
                    "ACO could not identify exactly one Advanced AE CPU for a Big-capacity plan; added={}",
                    added.size());
            cir.setReturnValue(CraftingSubmitResult.INCOMPLETE_PLAN);
            return;
        }

        UUID cpuId = added.iterator().next();
        /*
         * 親Job所有の子WindowはManagerが直後にBindingへ移す。
         * ここで通常予約をBigIntegerへ昇格すると、親予約と二重計上になる。
         */
        if (attempt.parentOwnedWindow()) {
            return;
        }
        var host = BigCraftingHostRegistry.find(this).orElse(null);
        boolean promoted = host != null
                && host.promoteExternalReservation(cpuId, bigPlan.exactBytes());
        // Sidecar昇格に失敗したJobを互換値Long.MAXのまま動かさず、Advanced AEの取消処理へ戻す。
        if (!promoted) {
            ((AdvCraftingCPUCluster) (Object) this).cancelJob(cpuId);
            AE2CraftingOptimizer.LOGGER.error(
                    "ACO cancelled Advanced AE CPU {} because its exact BigInteger capacity reservation failed",
                    cpuId);
            cir.setReturnValue(CraftingSubmitResult.CPU_TOO_SMALL);
            return;
        }

        AdvCraftingCPUCluster cluster = (AdvCraftingCPUCluster) (Object) this;
        cluster.recalculateRemainingStorage();
        cluster.markDirty();
    }

    @Unique
    private void aco$submitBigIntegerParent(
            BigIntegerCraftingPlan plan,
            ICraftingRequester requester,
            CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        // 自動要求は永続CraftingLinkを必要とするため、現段階ではrequesterなしの手動注文だけを受理する。
        if (requester != null
                || !ACOConfig.enableBigIntegerGameplayExecution()
                || plan.simulation()
                || !plan.generationsAreCurrent()) {
            cir.setReturnValue(CraftingSubmitResult.INCOMPLETE_PLAN);
            return;
        }
        var host = BigCraftingHostRegistry.find(this).orElse(null);
        // AQE BigInteger HostがないCPUまたは正確な空き容量不足では、Facade値を信用しない。
        if (host == null || host.available().compareTo(plan.exactBytes()) < 0) {
            cir.setReturnValue(CraftingSubmitResult.CPU_TOO_SMALL);
            return;
        }
        // 同じ確認画面からの二重送信は、Hostへ所有権を渡す前に一件へ絞る。
        if (!plan.claimSubmission()) {
            cir.setReturnValue(CraftingSubmitResult.CPU_BUSY);
            return;
        }

        var job = plan.preparedRoot().job();
        try {
            // Hostが容量予約とJob UUIDを原子的に受理した場合だけ成功を返す。
            if (!host.submit(job)) {
                plan.releaseSubmissionClaim();
                cir.setReturnValue(CraftingSubmitResult.CPU_TOO_SMALL);
                return;
            }
            AdvCraftingCPUCluster cluster = (AdvCraftingCPUCluster) (Object) this;
            cluster.recalculateRemainingStorage();
            cluster.markDirty();
            // CraftConfirmMenuの手動注文はrequester=nullなので、永続CraftingLinkを偽造しない。
            cir.setReturnValue(CraftingSubmitResult.successful(null));
        } catch (RuntimeException failure) {
            // 提出途中の例外では親Jobを残さず、次回は再計算された新しいPlanからやり直す。
            host.cancel(job.id());
            AE2CraftingOptimizer.LOGGER.error(
                    "ACO failed to submit BigInteger parent job {} to AQE",
                    job.id(),
                    failure);
            cir.setReturnValue(CraftingSubmitResult.INCOMPLETE_PLAN);
        }
    }

    private record SubmissionAttempt(
            Object cluster,
            Set<UUID> activeCpuIds,
            boolean parentOwnedWindow) {
    }
}
