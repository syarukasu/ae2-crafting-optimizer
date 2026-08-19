package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.syaru.ae2craftingoptimizer.access.BigCapacityPlanBoundaryAccess;
import com.syaru.ae2craftingoptimizer.api.big.BigCraftingEngineApi;
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
        // 通常long計画はIssue #98の保護対象ではなく、AE2本来の提出処理へそのまま渡す。
        if (!Ae2CraftingPlanSidecars.isWide(plan)) {
            return;
        }

        boolean externalConsumer = BigCraftingEngineApi.hasExternalBigIntegerPlanConsumer();
        boolean executableExactPlan = BigCraftingEngineApi.inspectBigIntegerPlan(plan)
                .filter(view -> !view.simulation())
                .isPresent();
        // Issue #98: 個別数量がlong内でも合計bytesだけ超過するBigCapacity計画を許可する。
        if (externalConsumer && executableExactPlan) {
            return;
        }

        // 対応CPUが無いwide計画を、容量だけLong.MAXへ飽和した標準CPUへ誤投入させない。
        cir.setReturnValue(CraftingSubmitResult.CPU_TOO_SMALL);
    }
}
