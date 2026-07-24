package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.stacks.KeyCounter;
import com.syaru.ae2craftingoptimizer.engine.BigKeyCounterSidecars;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** AE2がKeyCounterを再利用する時、前回集計のBigInteger Sidecarも同時に破棄する。 */
@Mixin(value = KeyCounter.class, remap = false)
public abstract class KeyCounterBigIntegerSidecarLifecycleMixin {
    @Inject(method = {"clear", "reset"}, at = @At("HEAD"))
    private void aco$clearExactInventorySidecar(CallbackInfo ci) {
        BigKeyCounterSidecars.clear((KeyCounter) (Object) this);
    }
}
