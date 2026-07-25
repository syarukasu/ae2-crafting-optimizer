package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.menu.me.crafting.CraftingPlanSummary;
import com.syaru.ae2craftingoptimizer.engine.Ae2CraftingPlanSidecars;
import com.syaru.ae2craftingoptimizer.engine.BigCraftingPlanSummary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Wide計画をAE2のunchecked long加算へ渡さず、安全な互換Summaryへ変換する。 */
@Mixin(value = CraftingPlanSummary.class, remap = false)
public abstract class CraftingPlanSummaryWidePlanMixin {
    @Inject(method = "fromJob", at = @At("HEAD"), cancellable = true, require = 1)
    private static void aco$buildWideSummaryWithoutLongOverflow(
            IGrid grid,
            IActionSource source,
            ICraftingPlan plan,
            CallbackInfoReturnable<CraftingPlanSummary> cir) {
        Ae2CraftingPlanSidecars.metadata(plan).ifPresent(widePlan ->
                cir.setReturnValue(BigCraftingPlanSummary.from(widePlan).toVanillaFacade()));
    }
}
