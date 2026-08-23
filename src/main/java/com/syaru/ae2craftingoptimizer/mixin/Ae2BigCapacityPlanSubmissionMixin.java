package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.access.CraftingLogicTransactionAccess;
import com.syaru.ae2craftingoptimizer.access.ExactCraftingJobAccess;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.integration.ExactSubmissionScope;
import com.syaru.ae2craftingoptimizer.engine.Ae2CraftingPlanSidecars;
import com.syaru.ae2craftingoptimizer.engine.BigIntegerCraftingPlan;
import com.syaru.ae2craftingoptimizer.engine.ExactPlanPatternRevalidator;
import com.syaru.ae2craftingoptimizer.integration.Ae2BigCraftingExecutionManager;
import com.syaru.ae2craftingoptimizer.optimization.BigIntegerPlanDeclineReason;
import com.syaru.ae2craftingoptimizer.optimization.BigIntegerPlanDiagnostics;
import java.util.LinkedHashMap;
import java.util.Map;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Issue #115: 標準AE2クラスタが受理したexact計画を同じAE2 Jobへ昇格する。 */
@Mixin(value = CraftingCPUCluster.class, remap = false)
public abstract class Ae2BigCapacityPlanSubmissionMixin {
    @Shadow
    @Final
    public CraftingCpuLogic craftingLogic;

    @Shadow
    public abstract void cancelJob();

    @Shadow
    public abstract void markDirty();

    /** 同じサーバースレッド上の一回のsubmitだけへ元exact計画を渡す。 */
    @Unique
    private static final ThreadLocal<SubmissionAttempt> ACO_SUBMISSION = new ThreadLocal<>();

    @Inject(method = "submitJob", at = @At("HEAD"), cancellable = true, require = 1)
    private void aco$validateExactPlan(
            IGrid grid,
            ICraftingPlan plan,
            IActionSource source,
            ICraftingRequester requester,
            CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        ACO_SUBMISSION.remove();
        BigIntegerCraftingPlan exact = Ae2CraftingPlanSidecars.bigInteger(plan).orElse(null);
        // 通常long計画は、ACOが物理Targetを要求せずAE2本来の提出経路へ完全に委譲する。
        if (exact == null || exact.fitsStandardLongExecution()) {
            return;
        }
        // 自動要求のExact最終出力転送は未対応なので、手動注文だけを対象にする。
        if (requester != null
                || !ACOConfig.enableBigIntegerGameplayExecution()
                || exact.simulation()) {
            cir.setReturnValue(CraftingSubmitResult.INCOMPLETE_PLAN);
            return;
        }
        ExactPlanPatternRevalidator.Result validation = exact.validateForSubmission(grid);
        // Issue #90: 参照Patternが変わった計画だけを入力所有権移転前に拒否する。
        if (!validation.valid()) {
            BigIntegerPlanDiagnostics.record(
                    BigIntegerPlanDeclineReason.GENERATION_CHANGED,
                    exact.finalOutput().what().getId().toString(),
                    exact.exactPlan().requestedAmount(),
                    exact.patternGeneration(),
                    exact.recipeGeneration(),
                    validation.detail());
            cir.setReturnValue(CraftingSubmitResult.INCOMPLETE_PLAN);
            return;
        }
        // 同じ確認画面から二重送信されたPlanは、一つの実Jobへだけ結び付ける。
        if (!exact.claimSubmission()) {
            cir.setReturnValue(CraftingSubmitResult.CPU_BUSY);
            return;
        }
        ACO_SUBMISSION.set(new SubmissionAttempt(this, plan, exact));
    }

    /**
     * CPU使用中判定とCraftingLink生成は標準AE2へ任せ、Job初期化時だけ一回分Facadeを渡す。
     */
    @Redirect(
            method = "submitJob",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/crafting/execution/CraftingCpuLogic;trySubmitJob(Lappeng/api/networking/IGrid;Lappeng/api/networking/crafting/ICraftingPlan;Lappeng/api/networking/security/IActionSource;Lappeng/api/networking/crafting/ICraftingRequester;)Lappeng/api/networking/crafting/ICraftingSubmitResult;"),
            require = 1)
    private ICraftingSubmitResult aco$submitExactJobFacade(
            CraftingCpuLogic logic,
            IGrid grid,
            ICraftingPlan plan,
            IActionSource source,
            ICraftingRequester requester) {
        SubmissionAttempt attempt = ACO_SUBMISSION.get();
        if (attempt == null
                || attempt.cluster() != this
                || attempt.originalPlan() != plan) {
            return logic.trySubmitJob(grid, plan, source, requester);
        }
        ICraftingPlan facade = aco$singleExecutionFacade(attempt.exactPlan());
        try {
            // 容量はBigInteger台帳で承認済み。下流のlongゲートへ測り直させない。
            ExactSubmissionScope.enter(facade);
            return logic.trySubmitJob(grid, facade, source, requester);
        } catch (RuntimeException | LinkageError failure) {
            ACO_SUBMISSION.remove();
            attempt.exactPlan().releaseSubmissionClaim();
            throw failure;
        } finally {
            ExactSubmissionScope.exit();
        }
    }

    @Inject(method = "submitJob", at = @At("RETURN"), cancellable = true, require = 1)
    private void aco$installExactJob(
            IGrid grid,
            ICraftingPlan plan,
            IActionSource source,
            ICraftingRequester requester,
            CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        BigIntegerCraftingPlan exact = Ae2CraftingPlanSidecars.bigInteger(plan).orElse(null);
        if (exact == null) {
            return;
        }
        SubmissionAttempt attempt = ACO_SUBMISSION.get();
        ACO_SUBMISSION.remove();
        // AE2が容量・使用中・安全性で拒否した場合は、ACOから成功へ上書きしない。
        if (attempt == null
                || attempt.cluster() != this
                || cir.getReturnValue() == null
                || !cir.getReturnValue().successful()) {
            if (attempt != null && attempt.exactPlan() == exact) {
                exact.releaseSubmissionClaim();
            }
            return;
        }
        try {
            Object job = ((CraftingLogicTransactionAccess) (Object) craftingLogic)
                    .aco$getExecutingJob();
            if (!(job instanceof ExactCraftingJobAccess exactJob)) {
                throw new IllegalStateException("AE2 exact-job access mixin is missing");
            }
            exactJob.aco$installExactState(exact);
            Ae2BigCraftingExecutionManager.register((CraftingCPUCluster) (Object) this);
            markDirty();
        } catch (RuntimeException | LinkageError failure) {
            cancelJob();
            exact.releaseSubmissionClaim();
            AE2CraftingOptimizer.LOGGER.error(
                    "ACO cancelled an AE2 exact CPU because exact job accounting could not be installed",
                    failure);
            cir.setReturnValue(CraftingSubmitResult.INCOMPLETE_PLAN);
        }
    }

    @Unique
    private static CraftingPlan aco$singleExecutionFacade(BigIntegerCraftingPlan plan) {
        Map<appeng.api.crafting.IPatternDetails, Long> oneExecution = new LinkedHashMap<>();
        // 固有Patternを一件ずつ登録し、AE2のJob構造だけを数量非依存で初期化する。
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

    private record SubmissionAttempt(
            Object cluster,
            ICraftingPlan originalPlan,
            BigIntegerCraftingPlan exactPlan) {
    }
}
