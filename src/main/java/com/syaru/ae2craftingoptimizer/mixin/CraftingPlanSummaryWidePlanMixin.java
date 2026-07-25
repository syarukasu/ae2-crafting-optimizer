package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.menu.me.crafting.CraftingPlanSummary;
import com.syaru.ae2craftingoptimizer.engine.Ae2CraftingPlanSidecars;
import com.syaru.ae2craftingoptimizer.engine.BigCraftingPlanSummary;
import com.syaru.ae2craftingoptimizer.integration.Ae2CraftingTreeCompatibility;
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
        var metadata = Ae2CraftingPlanSidecars.metadata(plan);
        // 通常AE2計画は元のfromJobと周辺MODのTAIL Injectへそのまま渡す。
        if (metadata.isEmpty()) {
            return;
        }

        CraftingPlanSummary summary =
                BigCraftingPlanSummary.from(metadata.orElseThrow()).toVanillaFacade();
        // HEADで早期returnするWide計画だけ、実行されないAE2CTの初期化を明示的に補う。
        Ae2CraftingTreeCompatibility.populateWideSummary(summary, plan);
        cir.setReturnValue(summary);
    }
}
