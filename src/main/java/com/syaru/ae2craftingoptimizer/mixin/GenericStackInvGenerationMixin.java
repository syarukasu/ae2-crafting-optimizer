package com.syaru.ae2craftingoptimizer.mixin;

import appeng.helpers.externalstorage.GenericStackInv;
import com.syaru.ae2craftingoptimizer.optimization.ConfigInventoryGenerationAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GenericStackInv.class, remap = false)
public abstract class GenericStackInvGenerationMixin implements ConfigInventoryGenerationAccess {
    @Unique
    private long aco$generation = 1L;

    @Inject(method = "onChange", at = @At("HEAD"))
    private void aco$incrementGeneration(CallbackInfo ci) {
        aco$generation = aco$generation == Long.MAX_VALUE ? 1L : aco$generation + 1L;
    }

    @Override
    public long aco$getGeneration() {
        return aco$generation;
    }
}
