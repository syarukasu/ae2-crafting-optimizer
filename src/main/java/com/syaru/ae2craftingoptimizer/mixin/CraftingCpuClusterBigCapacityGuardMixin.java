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
        // Long.MAXは真の容量ではないため、対応Sidecarを持たない標準CPUでは実行しない。
        boolean exactPlan = Ae2CraftingPlanSidecars.bigInteger(plan).isPresent();
        boolean integratedExactPlan = exactPlan
                && BigCraftingEngineApi.hasExternalBigIntegerPlanConsumer();
        // 外部CPUの登録が無い計画は、容量だけをlongへ飽和した標準CPUへ渡さない。
        if (Ae2CraftingPlanSidecars.isWide(plan) && !integratedExactPlan) {
            String detail = exactPlan
                    ? "wide plan has an exact sidecar, but no external BigInteger consumer is registered"
                    : "wide plan has no exact BigInteger sidecar";
            BigIntegerPlanDiagnostics.record(
                    BigIntegerPlanDeclineReason.SUBMISSION_BACKING_MISSING,
                    null,
                    detail);
            if (ACO_LOGGED_MISSING_BIG_PLAN_BACKING.compareAndSet(false, true)) {
                AE2CraftingOptimizer.LOGGER.warn(
                        "ACO rejected a wide crafting plan because its exact BigInteger execution backing "
                                + "was unavailable ({}). This is not a CPU storage-capacity failure.",
                        detail);
            }
            // CPU_TOO_SMALL means actual storage shortage in AE2. Keep this fail-closed rejection distinct.
            cir.setReturnValue(CraftingSubmitResult.INCOMPLETE_PLAN);
        }
    }
}
