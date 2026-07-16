package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.crafting.CraftingCalculation;
import com.syaru.ae2craftingoptimizer.optimization.CraftingCalculationMemo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingCalculation.class, remap = false)
public abstract class CraftingCalculationMemoLifecycleMixin {
    @Inject(method = "run", at = @At("HEAD"))
    private void aco$beginCalculationMemo(CallbackInfoReturnable<ICraftingPlan> cir) {
        CraftingCalculationMemo.begin(this);
    }

    @Inject(method = "run", at = @At("RETURN"))
    private void aco$endCalculationMemo(CallbackInfoReturnable<ICraftingPlan> cir) {
        CraftingCalculationMemo.end(this);
    }
}
