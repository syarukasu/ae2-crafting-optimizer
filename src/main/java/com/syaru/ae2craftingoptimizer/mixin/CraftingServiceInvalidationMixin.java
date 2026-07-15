package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.IGridNode;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.optimization.CraftableSetCache;
import com.syaru.ae2craftingoptimizer.optimization.CraftingCalculationDeduplicator;
import com.syaru.ae2craftingoptimizer.optimization.CraftingExecutionBudget;
import com.syaru.ae2craftingoptimizer.optimization.CraftingRequestThrottle;
import com.syaru.ae2craftingoptimizer.optimization.PatternLookupCache;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CraftingService.class, remap = false)
public abstract class CraftingServiceInvalidationMixin {
    @Inject(method = "refreshNodeCraftingProvider", at = @At("TAIL"))
    private void ae2CraftingOptimizer$clearAdaptiveBudgetOnProviderRefresh(IGridNode node, CallbackInfo ci) {
        ae2CraftingOptimizer$clearAdaptiveBudget("crafting provider refresh");
    }

    @Inject(method = "addNode", at = @At("TAIL"))
    private void ae2CraftingOptimizer$clearAdaptiveBudgetOnNodeAdd(IGridNode node, CompoundTag savedData, CallbackInfo ci) {
        ae2CraftingOptimizer$clearAdaptiveBudget("crafting node added");
    }

    @Inject(method = "removeNode", at = @At("TAIL"))
    private void ae2CraftingOptimizer$clearAdaptiveBudgetOnNodeRemove(IGridNode node, CallbackInfo ci) {
        ae2CraftingOptimizer$clearAdaptiveBudget("crafting node removed");
    }

    private static void ae2CraftingOptimizer$clearAdaptiveBudget(String reason) {
        CraftingCalculationDeduplicator.clear(reason);
        CraftingExecutionBudget.clearAdaptiveState(reason);
        CraftingRequestThrottle.clear(reason);
        CraftableSetCache.clear(reason);
        PatternLookupCache.clear(reason);
    }
}
