package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.stacks.AEKey;
import appeng.me.service.StorageService;
import com.syaru.ae2craftingoptimizer.optimization.CraftingCalculationDeduplicator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 外部mountが直接変化した場合も、AE2が検出した時点で計画cacheを旧世代へ分離する。 */
@Mixin(value = StorageService.class, remap = false)
public abstract class StorageServiceCraftingPlanGenerationMixin {
    @Inject(method = "postWatcherUpdate", at = @At("HEAD"), require = 1)
    private void aco$advanceStorageGeneration(AEKey key, long amount, CallbackInfo ci) {
        CraftingCalculationDeduplicator.onStorageChange();
    }
}
