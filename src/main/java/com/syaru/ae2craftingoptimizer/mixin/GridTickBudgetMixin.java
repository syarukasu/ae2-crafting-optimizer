package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.ticking.TickRateModulation;
import appeng.me.service.TickManagerService;
import appeng.me.service.helpers.TickTracker;
import com.syaru.ae2craftingoptimizer.optimization.GridTickBudget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TickManagerService.class, remap = false)
public abstract class GridTickBudgetMixin {
    @Shadow
    private long currentTick;

    @Inject(
            method = "unsafeTickingRequest",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private void aco$deferHeavyGridTickable(
            TickTracker tracker,
            int ticksSinceLastCall,
            CallbackInfoReturnable<TickRateModulation> cir) {
        TickRateModulation deferred = GridTickBudget.beforeTick(
                tracker.getGridTickable(),
                tracker.getNode(),
                ticksSinceLastCall,
                this.currentTick);
        if (deferred != null) {
            cir.setReturnValue(deferred);
        }
    }

    @Inject(
            method = "unsafeTickingRequest",
            at = @At("RETURN"),
            require = 0)
    private void aco$recordHeavyGridTickable(
            TickTracker tracker,
            int ticksSinceLastCall,
            CallbackInfoReturnable<TickRateModulation> cir) {
        GridTickBudget.afterTick(
                tracker.getGridTickable(),
                tracker.getNode(),
                this.currentTick,
                cir.getReturnValue());
    }
}
