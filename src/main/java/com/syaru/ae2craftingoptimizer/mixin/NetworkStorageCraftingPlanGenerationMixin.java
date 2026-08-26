package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.me.storage.NetworkStorage;
import com.syaru.ae2craftingoptimizer.optimization.CraftingCalculationDeduplicator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 実際に成立したME在庫変更だけを、完成済み計画cacheの世代へ通知する。 */
@Mixin(value = NetworkStorage.class, remap = false)
public abstract class NetworkStorageCraftingPlanGenerationMixin {
    @Inject(method = "insert", at = @At("RETURN"), require = 1)
    private void aco$advanceAfterInsert(
            AEKey key,
            long amount,
            Actionable mode,
            IActionSource source,
            CallbackInfoReturnable<Long> cir) {
        // SIMULATEまたは0件受理では正本在庫が変化していないため、世代を進めない。
        if (mode != Actionable.MODULATE || cir.getReturnValue() <= 0L) {
            return;
        }
        CraftingCalculationDeduplicator.onStorageChange();
    }

    @Inject(method = "extract", at = @At("RETURN"), require = 1)
    private void aco$advanceAfterExtract(
            AEKey key,
            long amount,
            Actionable mode,
            IActionSource source,
            CallbackInfoReturnable<Long> cir) {
        // SIMULATEまたは0件抽出では正本在庫が変化していないため、世代を進めない。
        if (mode != Actionable.MODULATE || cir.getReturnValue() <= 0L) {
            return;
        }
        CraftingCalculationDeduplicator.onStorageChange();
    }
}
