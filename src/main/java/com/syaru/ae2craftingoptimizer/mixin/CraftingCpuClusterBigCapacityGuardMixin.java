package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.access.BigCapacityPlanBoundaryAccess;
import com.syaru.ae2craftingoptimizer.api.big.BigCraftingEngineApi;
import com.syaru.ae2craftingoptimizer.engine.Ae2CraftingPlanSidecars;
import com.syaru.ae2craftingoptimizer.optimization.BigIntegerPlanDeclineReason;
import com.syaru.ae2craftingoptimizer.optimization.BigIntegerPlanDiagnostics;
import java.util.concurrent.atomic.AtomicBoolean;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** BigInteger容量台帳を持たない標準AE2 CPUへ、大容量互換値だけの計画を誤投入させない。 */
@Mixin(value = CraftingCPUCluster.class, remap = false)
public abstract class CraftingCpuClusterBigCapacityGuardMixin
        implements BigCapacityPlanBoundaryAccess {
    @Unique
    private static final AtomicBoolean ACO_LOGGED_MISSING_BIG_PLAN_BACKING =
            new AtomicBoolean();

    @Inject(method = "submitJob", at = @At("HEAD"), cancellable = true, require = 1)
    private void aco$rejectBigCapacityFacade(
            IGrid grid,
            ICraftingPlan plan,
            IActionSource source,
            ICraftingRequester requester,
            CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        // 通常long計画はIssue #98の保護対象ではなく、AE2本来の提出処理へそのまま渡す。
        if (!Ae2CraftingPlanSidecars.isWide(plan)) {
            return;
        }

        boolean externalConsumer = BigCraftingEngineApi.hasExternalBigIntegerPlanConsumer();
        var exactView = BigCraftingEngineApi.inspectBigIntegerPlan(plan).orElse(null);
        boolean executableExactPlan = exactView != null && !exactView.simulation();
        // Issue #98: 個別数量がlong内でも合計bytesだけ超過するBigCapacity計画を許可する。
        if (externalConsumer && executableExactPlan) {
            return;
        }

        String detail;
        // 診断ではSidecar欠落、simulation、外部Consumer欠落を別々に記録する。
        if (exactView == null) {
            detail = "wide plan has no exact BigInteger execution view";
        } else if (exactView.simulation()) {
            detail = "wide plan is a simulation and cannot be submitted for execution";
        } else {
            detail = "wide plan has exact backing, but no external BigInteger consumer is registered";
        }
        BigIntegerPlanDiagnostics.record(
                BigIntegerPlanDeclineReason.SUBMISSION_BACKING_MISSING,
                null,
                detail);
        // 同一原因を注文ごとに積み上げず、起動中の最初の一件だけ明示する。
        if (ACO_LOGGED_MISSING_BIG_PLAN_BACKING.compareAndSet(false, true)) {
            AE2CraftingOptimizer.LOGGER.warn(
                    "ACO rejected a wide crafting plan because its exact BigInteger execution backing "
                            + "was unavailable ({}). This is not a CPU storage-capacity failure.",
                    detail);
        }
        // CPU_TOO_SMALLは実容量不足専用。裏付け不足はINCOMPLETE_PLANとしてfail closedする。
        cir.setReturnValue(CraftingSubmitResult.INCOMPLETE_PLAN);
    }
}
