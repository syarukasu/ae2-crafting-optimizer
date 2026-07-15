package com.syaru.ae2craftingoptimizer.mixin;

import appeng.parts.automation.IOBusPart;
import com.syaru.ae2craftingoptimizer.optimization.GridTickBudget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = IOBusPart.class, priority = 500, remap = false)
public abstract class IOBusOperationsCapMixin {
    @Inject(
            method = "getOperationsPerTick",
            at = @At("RETURN"),
            cancellable = true,
            require = 0)
    private void aco$capIoBusOperations(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(GridTickBudget.limitIoBusOperations(this, cir.getReturnValue()));
    }
}
