package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.syaru.ae2craftingoptimizer.access.BigCapacityPlanBoundaryAccess;
import com.syaru.ae2craftingoptimizer.engine.Ae2CraftingPlanSidecars;
import com.syaru.ae2craftingoptimizer.engine.WidePlanSubmissionGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** BigInteger容量台帳を持たない標準AE2 CPUへ、大容量互換値だけの計画を誤投入させない。 */
@Mixin(value = CraftingCPUCluster.class, remap = false)
public abstract class CraftingCpuClusterBigCapacityGuardMixin
        implements BigCapacityPlanBoundaryAccess {
    @Inject(method = "submitJob", at = @At("HEAD"), cancellable = true, require = 1)
    private void aco$rejectBigCapacityFacade(
            IGrid grid,
            ICraftingPlan plan,
            IActionSource source,
            ICraftingRequester requester,
            CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        // Long.MAXは真の容量ではないため、対応Sidecarを持たない標準CPUでは実行しない。
        if (Ae2CraftingPlanSidecars.isWide(plan)) {
            /*
             * 容量不足コードを返すとプレイヤーはストレージを増やしに行くが、
             * この拒否は空き容量と一切関係がない。理由が伝わる返答へ差し替える。
             */
            cir.setReturnValue(WidePlanSubmissionGuard.declineOnUnsupportedCpu(
                    plan,
                    ((CraftingCPUCluster) (Object) this).getAvailableStorage()));
        }
    }
}
