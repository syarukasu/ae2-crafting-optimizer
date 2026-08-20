package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import appeng.crafting.execution.CraftingSubmitResult;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.access.AdvancedAeExactCraftingJobAccess;
import com.syaru.ae2craftingoptimizer.access.BigCapacityPlanBoundaryAccess;
import com.syaru.ae2craftingoptimizer.access.CraftingLogicTransactionAccess;
import com.syaru.ae2craftingoptimizer.api.big.BigCraftingHostRegistry;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.engine.Ae2CraftingPlanSidecars;
import com.syaru.ae2craftingoptimizer.engine.BigCapacityCraftingPlan;
import com.syaru.ae2craftingoptimizer.engine.BigIntegerCraftingPlan;
import com.syaru.ae2craftingoptimizer.engine.WidePlanSubmissionGuard;
import com.syaru.ae2craftingoptimizer.engine.ExactPlanPatternRevalidator;
import com.syaru.ae2craftingoptimizer.integration.AqeBigCraftingExecutionContext;
import com.syaru.ae2craftingoptimizer.optimization.BigIntegerPlanDeclineReason;
import com.syaru.ae2craftingoptimizer.optimization.BigIntegerPlanDiagnostics;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU;
import net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPUCluster;
import net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Advanced AEが作る同じ実CPU Jobを維持したまま、容量と実行カウンタを正確値へ昇格する。
 *
 * <p>通常計画は既存long経路を変更しない。個別カウンタがlongを超える計画だけ、
 * 一回分Facadeで実Jobを初期化してからBigInteger正本を同じJobへ設置する。</p>
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
        /*
         * 前回submitJobが例外終了してRETURNへ到達しなかった場合のPlan参照を先に破棄する。
         * 同じサーバースレッド上でも、提出文脈は常に現在の一呼出しだけへ限定する。
         */
        ACO_SUBMISSION.remove();
        // 個別カウンタもlongを超える計画は、同じAdvanced AE実JobへBigInteger Sidecarを装着する。
        BigIntegerCraftingPlan bigIntegerPlan =
                Ae2CraftingPlanSidecars.bigInteger(plan).orElse(null);
        if (bigIntegerPlan != null) {
            // 自動要求は最終出力をCraftingLinkへ戻すExact転送が未対応なので、手動注文だけを受理する。
            if (requester != null
                    || !ACOConfig.enableBigIntegerGameplayExecution()
                    || bigIntegerPlan.simulation()) {
                cir.setReturnValue(CraftingSubmitResult.INCOMPLETE_PLAN);
                return;
            }
            ExactPlanPatternRevalidator.Result validation =
                    bigIntegerPlan.validateForSubmission(grid);
            // Issue #90: 無関係なProvider更新は通し、参照Patternの変更だけを拒否する。
            if (!validation.valid()) {
                aco$recordGenerationDecline(
                        bigIntegerPlan.finalOutput(),
                        bigIntegerPlan.exactPlan().requestedAmount(),
                        bigIntegerPlan.patternGeneration(),
                        bigIntegerPlan.recipeGeneration(),
                        validation);
                cir.setReturnValue(CraftingSubmitResult.INCOMPLETE_PLAN);
                return;
            }
            var host = BigCraftingHostRegistry.find(this).orElse(null);
            // BigInteger台帳が無いことは容量不足ではないため、別の拒否理由を返す。
            if (host == null) {
                cir.setReturnValue(WidePlanSubmissionGuard.declineOnUnsupportedCpu(
                        plan,
                        ((AdvCraftingCPUCluster) (Object) this).getAvailableStorage()));
                return;
            }
            if (host.available().compareTo(bigIntegerPlan.exactBytes()) < 0) {
                cir.setReturnValue(CraftingSubmitResult.CPU_TOO_SMALL);
                return;
            }
            // 同じ確認画面の二重送信は、実CPU生成前に一件へ絞る。
            if (!bigIntegerPlan.claimSubmission()) {
                cir.setReturnValue(CraftingSubmitResult.CPU_BUSY);
                return;
            }
            ACO_SUBMISSION.set(new SubmissionAttempt(
                    this,
                    plan,
                    Set.copyOf(activeCpus.keySet()),
                    false,
                    bigIntegerPlan));
            return;
        }
        // 通常AE2計画には一切介入せず、Big容量マーカーだけを対象にする。
        BigCapacityCraftingPlan bigPlan =
                Ae2CraftingPlanSidecars.bigCapacity(plan).orElse(null);
        if (bigPlan == null) {
            return;
        }
        // 実験機能OFF、Missing計画、古いPattern世代は入力を抜く前に拒否する。
        if (!ACOConfig.enableAtomicBigCapacityPlans()
                || bigPlan.simulation()
                || !bigPlan.missingItems().isEmpty()) {
            cir.setReturnValue(CraftingSubmitResult.INCOMPLETE_PLAN);
            return;
        }
        ExactPlanPatternRevalidator.Result validation = bigPlan.validateForSubmission(grid);
        // Issue #90: 合計容量だけがwideな計画にも、同じ対象限定の再検証を適用する。
        if (!validation.valid()) {
            aco$recordGenerationDecline(
                    bigPlan.finalOutput(),
                    BigInteger.valueOf(bigPlan.finalOutput().amount()),
                    bigPlan.patternGeneration(),
                    bigPlan.recipeGeneration(),
                    validation);
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
        // BigInteger台帳が無いことは容量不足ではないため、別の拒否理由を返す。
        if (host == null) {
            cir.setReturnValue(WidePlanSubmissionGuard.declineOnUnsupportedCpu(
                    plan,
                    ((AdvCraftingCPUCluster) (Object) this).getAvailableStorage()));
            return;
        }
        if ((parentOwnedWindow
                        && !parentOwnedAllowance.equals(bigPlan.exactBytes()))
                || (!parentOwnedWindow
                        && host.available().compareTo(bigPlan.exactBytes()) < 0)) {
            cir.setReturnValue(CraftingSubmitResult.CPU_TOO_SMALL);
            return;
        }
        ACO_SUBMISSION.set(new SubmissionAttempt(
                this,
                plan,
                Set.copyOf(activeCpus.keySet()),
                parentOwnedWindow,
                null));
    }

    /**
     * Advanced AEには実JobとCraftingLinkを作らせる一方、初期抽出とlong積算には一回分Facadeだけを渡す。
     */
    @Redirect(
            method = "submitJob",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/pedroksl/advanced_ae/common/logic/AdvCraftingCPULogic;trySubmitJob(Lappeng/api/networking/IGrid;Lappeng/api/networking/crafting/ICraftingPlan;Lappeng/api/networking/security/IActionSource;Lappeng/api/networking/crafting/ICraftingRequester;)Lappeng/api/networking/crafting/ICraftingSubmitResult;"),
            require = 1)
    private ICraftingSubmitResult aco$submitExactJobFacade(
            AdvCraftingCPULogic logic,
            IGrid grid,
            ICraftingPlan plan,
            IActionSource source,
            ICraftingRequester requester) {
        SubmissionAttempt attempt = ACO_SUBMISSION.get();
        if (attempt == null
                || attempt.cluster() != this
                || attempt.originalPlan() != plan
                || attempt.bigIntegerPlan() == null) {
            return logic.trySubmitJob(
                    grid,
                    plan,
                    source,
                    requester);
        }
        try {
            return logic.trySubmitJob(
                    grid,
                    aco$singleExecutionFacade(attempt.bigIntegerPlan()),
                    source,
                    requester);
        } catch (RuntimeException | LinkageError failure) {
            /*
             * Advanced AEが実CPU生成途中で失敗した場合は、この提出だけが追加したCPUを閉じる。
             * 次回submitのHEADへ後始末を先送りすると、容量予約と提出Claimが残留する。
             */
            Set<UUID> added = new LinkedHashSet<>(activeCpus.keySet());
            added.removeAll(attempt.activeCpuIds());
            // 失敗した一提出が追加した候補だけをAdvanced AE本来の取消経路へ渡す。
            for (UUID cpuId : added) {
                ((AdvCraftingCPUCluster) (Object) this).cancelJob(cpuId);
            }
            ACO_SUBMISSION.remove();
            attempt.bigIntegerPlan().releaseSubmissionClaim();
            throw failure;
        }
    }

    @Inject(method = "submitJob", at = @At("RETURN"), cancellable = true, require = 1)
    private void aco$promoteBigCapacityReservation(
            IGrid grid,
            ICraftingPlan plan,
            IActionSource source,
            ICraftingRequester requester,
            CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        // 通常計画のRETURNではThreadLocalにも容量台帳にも触れない。
        BigIntegerCraftingPlan bigIntegerPlan =
                Ae2CraftingPlanSidecars.bigInteger(plan).orElse(null);
        BigCapacityCraftingPlan bigPlan =
                Ae2CraftingPlanSidecars.bigCapacity(plan).orElse(null);
        if (bigPlan == null && bigIntegerPlan == null) {
            return;
        }
        SubmissionAttempt attempt = ACO_SUBMISSION.get();
        ACO_SUBMISSION.remove();
        boolean ownsBigIntegerClaim =
                attempt != null
                        && attempt.bigIntegerPlan() == bigIntegerPlan;
        // HEADで拒否された計画、またはAdvanced AE側が拒否した計画には予約が存在しない。
        if (attempt == null
                || attempt.cluster() != this
                || cir.getReturnValue() == null
                || !cir.getReturnValue().successful()) {
            /*
             * 二重クリックでclaimSubmissionに失敗した呼出しは、先行提出のClaimを所有しない。
             * 現在のSubmissionAttemptが同じPlanを所有する場合だけ解除する。
             */
            if (ownsBigIntegerClaim) {
                bigIntegerPlan.releaseSubmissionClaim();
            }
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
            if (bigIntegerPlan != null) {
                bigIntegerPlan.releaseSubmissionClaim();
            }
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
        BigInteger exactBytes = bigIntegerPlan != null
                ? bigIntegerPlan.exactBytes()
                : bigPlan.exactBytes();
        boolean promoted = host != null
                && host.promoteExternalReservation(cpuId, exactBytes);
        // Sidecar昇格に失敗したJobを互換値Long.MAXのまま動かさず、Advanced AEの取消処理へ戻す。
        if (!promoted) {
            ((AdvCraftingCPUCluster) (Object) this).cancelJob(cpuId);
            if (bigIntegerPlan != null) {
                bigIntegerPlan.releaseSubmissionClaim();
            }
            AE2CraftingOptimizer.LOGGER.error(
                    "ACO cancelled Advanced AE CPU {} because its exact BigInteger capacity reservation failed",
                    cpuId);
            cir.setReturnValue(CraftingSubmitResult.CPU_TOO_SMALL);
            return;
        }

        if (bigIntegerPlan != null) {
            try {
                AdvCraftingCPU cpu = activeCpus.get(cpuId);
                Object job = cpu == null
                        ? null
                        : ((CraftingLogicTransactionAccess) (Object) cpu.craftingLogic)
                                .aco$getExecutingJob();
                if (!(job instanceof AdvancedAeExactCraftingJobAccess exactJob)) {
                    throw new IllegalStateException(
                            "Advanced AE exact-job access mixin is missing");
                }
                exactJob.aco$installExactState(bigIntegerPlan);
                cpu.markDirty();
            } catch (RuntimeException | LinkageError failure) {
                ((AdvCraftingCPUCluster) (Object) this).cancelJob(cpuId);
                host.releaseExternal(cpuId);
                bigIntegerPlan.releaseSubmissionClaim();
                AE2CraftingOptimizer.LOGGER.error(
                        "ACO cancelled Advanced AE CPU {} because exact job accounting could not be installed",
                        cpuId,
                        failure);
                cir.setReturnValue(CraftingSubmitResult.INCOMPLETE_PLAN);
                return;
            }
        }

        AdvCraftingCPUCluster cluster = (AdvCraftingCPUCluster) (Object) this;
        cluster.recalculateRemainingStorage();
        cluster.markDirty();
    }

    @Unique
    private static CraftingPlan aco$singleExecutionFacade(
            BigIntegerCraftingPlan plan) {
        Map<appeng.api.crafting.IPatternDetails, Long> oneExecution =
                new LinkedHashMap<>();
        // 固有Patternを一件ずつ登録し、Advanced AEのJob構造だけを安全に初期化する。
        for (var pattern : plan.exactPatternTimes().keySet()) {
            oneExecution.put(pattern, 1L);
        }
        return new CraftingPlan(
                plan.finalOutput(),
                Long.MAX_VALUE,
                false,
                false,
                new KeyCounter(),
                new KeyCounter(),
                new KeyCounter(),
                Map.copyOf(oneExecution));
    }

    /** 世代再検証の拒否理由を、設定可能なBigInteger診断へ集約する。 */
    @Unique
    private static void aco$recordGenerationDecline(
            GenericStack output,
            BigInteger requestedAmount,
            long patternGeneration,
            long recipeGeneration,
            ExactPlanPatternRevalidator.Result validation) {
        BigIntegerPlanDiagnostics.record(
                BigIntegerPlanDeclineReason.GENERATION_CHANGED,
                output.what().getId().toString(),
                requestedAmount,
                patternGeneration,
                recipeGeneration,
                validation.detail());
    }

    private record SubmissionAttempt(
            Object cluster,
            ICraftingPlan originalPlan,
            Set<UUID> activeCpuIds,
            boolean parentOwnedWindow,
            BigIntegerCraftingPlan bigIntegerPlan) {
    }
}
