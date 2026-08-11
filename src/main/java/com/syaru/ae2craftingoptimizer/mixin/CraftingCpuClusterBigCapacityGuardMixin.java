package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.syaru.ae2craftingoptimizer.access.BigCapacityPlanBoundaryAccess;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.engine.Ae2CraftingPlanSidecars;
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
        boolean insaneAeExactPlan = ACOConfig.enableInsaneAeBigCraftingProfile()
                && Ae2CraftingPlanSidecars.bigInteger(plan).isPresent();
        // InsaneAE専用のExact受け渡しだけはInsaneAE側が同じPlanを所有するため通す。
        // 容量だけをlongへ飽和したBigCapacity計画は標準CPUへ絶対に渡さない。
        if (Ae2CraftingPlanSidecars.isWide(plan) && !insaneAeExactPlan) {
            cir.setReturnValue(CraftingSubmitResult.CPU_TOO_SMALL);
        }
    }
}
