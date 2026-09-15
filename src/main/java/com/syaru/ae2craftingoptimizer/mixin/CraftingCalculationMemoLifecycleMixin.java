package com.syaru.ae2craftingoptimizer.mixin;

import appeng.crafting.CraftingCalculation;
import com.syaru.ae2craftingoptimizer.optimization.CraftingCalculationMemo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingCalculation.class, remap = false)
public abstract class CraftingCalculationMemoLifecycleMixin {
    @Inject(method = "run", at = @At("HEAD"))
    private void aco$beginCalculationMemo(CallbackInfoReturnable<?> cir) {
        CraftingCalculationMemo.begin(this);
    }

    @Inject(method = "finish", at = @At("HEAD"), require = 1)
    private void aco$endCalculationMemo(CallbackInfo ci) {
        // Issue #167: AE2はfinishをfinallyから呼ぶため、例外終了でもThreadLocalを必ず破棄する。
        CraftingCalculationMemo.end(this);
    }
}
