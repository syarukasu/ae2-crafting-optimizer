package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.IGridNode;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.optimization.CraftingExecutionBudget;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CraftingService.class, remap = false)
public abstract class CraftingServiceInvalidationMixin {
    private static Boolean aco$vmLoaded;

    @Inject(method = "refreshNodeCraftingProvider", at = @At("TAIL"))
    private void ae2CraftingOptimizer$clearAdaptiveBudgetOnProviderRefresh(IGridNode node, CallbackInfo ci) {
        ae2CraftingOptimizer$nativeIndexChanged(node);
    }

    @Inject(method = "addNode", at = @At("TAIL"))
    private void ae2CraftingOptimizer$clearAdaptiveBudgetOnNodeAdd(IGridNode node, CompoundTag savedData, CallbackInfo ci) {
        if (node != null && node.getOwner() instanceof appeng.blockentity.crafting.CraftingBlockEntity) {
            ae2CraftingOptimizer$clearAdaptiveBudget("crafting node added");
        }
    }

    @Inject(method = "removeNode", at = @At("TAIL"))
    private void ae2CraftingOptimizer$clearAdaptiveBudgetOnNodeRemove(IGridNode node, CallbackInfo ci) {
        ae2CraftingOptimizer$nativeIndexChanged(node);
        if (node != null && node.getOwner() instanceof appeng.blockentity.crafting.CraftingBlockEntity) {
            ae2CraftingOptimizer$clearAdaptiveBudget("crafting node removed");
        }
    }

    private static void ae2CraftingOptimizer$clearAdaptiveBudget(String reason) {
        /*
         * 計算共有キーにはPattern世代が含まれるため、Provider更新時に全Gridのdedupを
         * 消去する必要はない。旧世代Entryは一致せず、bounded cacheから自然に退避される。
         */
        CraftingExecutionBudget.clearAdaptiveState(reason);
    }

    private static void ae2CraftingOptimizer$nativeIndexChanged(IGridNode node) {
        if (node != null
                && node.getService(appeng.api.networking.crafting.ICraftingProvider.class) != null
                && ae2CraftingOptimizer$isVmLoaded()) {
            com.syaru.ae2craftingoptimizer.optimization.ProviderPatternGenerationTracker.nativeIndexChanged();
        }
    }

    private static boolean ae2CraftingOptimizer$isVmLoaded() {
        Boolean cached = aco$vmLoaded;
        if (cached == null) {
            cached = net.neoforged.fml.ModList.get().isLoaded("ae2vm_aco");
            aco$vmLoaded = cached;
        }
        return cached;
    }
}
