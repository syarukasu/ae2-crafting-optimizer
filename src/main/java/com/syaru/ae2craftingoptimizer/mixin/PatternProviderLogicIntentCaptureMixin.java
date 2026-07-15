package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import com.syaru.ae2craftingoptimizer.intent.PatternIntentCapture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PatternProviderLogic.class, remap = false)
public abstract class PatternProviderLogicIntentCaptureMixin {
    @Shadow
    @Final
    private PatternProviderLogicHost host;

    @Inject(method = "pushPattern", at = @At("RETURN"))
    private void ae2CraftingOptimizer$captureSuccessfulPatternPush(
            IPatternDetails pattern,
            KeyCounter[] inputHolder,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!cir.getReturnValueZ()) {
            return;
        }
        PatternIntentCapture.captureSuccessfulPush(this.host, pattern, inputHolder);
    }
}
