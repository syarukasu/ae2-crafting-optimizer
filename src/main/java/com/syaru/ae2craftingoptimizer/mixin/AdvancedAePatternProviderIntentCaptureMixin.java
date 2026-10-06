package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import com.syaru.ae2craftingoptimizer.intent.PatternIntentCapture;
import com.syaru.ae2craftingoptimizer.intent.PatternIntentExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic", remap = false)
public abstract class AdvancedAePatternProviderIntentCaptureMixin {
    @Unique
    private final PatternIntentExtractor aco$intentDescriptions = new PatternIntentExtractor();

    @Inject(method = "pushPattern", at = @At("RETURN"), require = 0)
    private void aco$captureSuccessfulAdvancedPatternPush(
            IPatternDetails pattern,
            KeyCounter[] inputHolder,
            CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            PatternIntentCapture.captureSuccessfulPushFromLogic(this, pattern, inputHolder, aco$intentDescriptions);
        }
    }
}
