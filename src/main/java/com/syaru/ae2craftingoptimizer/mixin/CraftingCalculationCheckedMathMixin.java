package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingCalculation;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 不足数のKeyCounterがlong境界で負数へ巻き戻る前に計算をAE2エラーとして止める。 */
@Mixin(value = CraftingCalculation.class, remap = false)
public abstract class CraftingCalculationCheckedMathMixin {
    @Shadow
    private KeyCounter missing;

    @Inject(method = "addMissing", at = @At("HEAD"))
    private void aco$validateMissingAdd(AEKey key, long amount, CallbackInfo ci) {
        if (!ACOConfig.enableCheckedAe2CraftingArithmetic()) {
            return;
        }
        if (amount < 0L) {
            throw new ArithmeticException("negative AE2 missing count");
        }
        Math.addExact(missing.get(key), amount);
    }
}
