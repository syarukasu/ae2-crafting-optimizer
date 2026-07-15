package com.syaru.ae2craftingoptimizer.mixin;

import appeng.me.service.P2PService;
import com.syaru.ae2craftingoptimizer.optimization.P2PNotificationDeduplicator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = P2PService.class, remap = false)
public abstract class P2PServiceTopologyDeduplicationMixin {
    @Inject(method = "wakeInputTunnels", at = @At("HEAD"), cancellable = true)
    private void aco$suppressDuplicateWakeSweep(CallbackInfo ci) {
        if (!P2PNotificationDeduplicator.shouldWakeInputs((P2PService) (Object) this)) {
            ci.cancel();
        }
    }
}
