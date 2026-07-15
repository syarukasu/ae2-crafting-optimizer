package com.syaru.ae2craftingoptimizer.mixin;

import com.syaru.ae2craftingoptimizer.optimization.GridTickBudget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(
        targets = {
                "com.glodblock.github.extendedae.common.parts.PartExImportBus",
                "com.glodblock.github.extendedae.common.parts.PartExExportBus",
                "com.glodblock.github.extendedae.common.parts.PartPreciseExportBus"
        },
        priority = 500,
        remap = false)
public abstract class ExtendedIOBusOperationsCapMixin {
    @Inject(
            method = "getOperationsPerTick",
            at = @At("RETURN"),
            cancellable = true,
            require = 0)
    private void aco$capExtendedIoBusOperations(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(GridTickBudget.limitIoBusOperations(this, cir.getReturnValue()));
    }
}
