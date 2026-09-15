package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.stacks.AEItemKey;
import appeng.crafting.pattern.AECraftingPattern;
import com.syaru.ae2craftingoptimizer.optimization.CraftingCalculationMemo;
import net.minecraft.world.item.crafting.CraftingRecipe;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Issue #179: leave AE2's untagged cache and initial recipe evaluation unchanged. */
@Mixin(value = AECraftingPattern.class, remap = false)
public class CraftingPatternTaggedValidationMixin {
    @Shadow @Final private CraftingRecipe recipe;

    @Inject(method = "getTestResult", at = @At("RETURN"), cancellable = true, require = 1)
    private void aco$reuseTaggedValidation(int slot, AEItemKey key, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() == null) {
            Boolean result = CraftingCalculationMemo.taggedCraftingResult(this, recipe, slot, key);
            if (result != null) {
                cir.setReturnValue(result);
            }
        }
    }

    @Inject(method = "setTestResult", at = @At("RETURN"), require = 1)
    private void aco$rememberTaggedValidation(int slot, AEItemKey key, boolean result, CallbackInfo ci) {
        CraftingCalculationMemo.rememberTaggedCraftingResult(this, recipe, slot, key, result);
    }
}
