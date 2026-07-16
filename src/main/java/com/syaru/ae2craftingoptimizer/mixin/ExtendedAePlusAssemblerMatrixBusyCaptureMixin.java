package com.syaru.ae2craftingoptimizer.mixin;

import com.syaru.ae2craftingoptimizer.optimization.AssemblerMatrixBusyCountCache;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(
        targets = "com.glodblock.github.extendedae.common.me.matrix.ClusterAssemblerMatrix",
        priority = 900,
        remap = false)
public abstract class ExtendedAePlusAssemblerMatrixBusyCaptureMixin {
    @Dynamic("Targets the busy-count handler merged by ExtendedAE Plus")
    @Inject(method = "onGetBusyCrafterAmount", at = @At("RETURN"), require = 0)
    private void aco$captureExtendedAePlusBusyCount(
            CallbackInfoReturnable<Integer> extendedAePlusResult,
            CallbackInfo ci) {
        Integer value = extendedAePlusResult.getReturnValue();
        if (value != null) {
            AssemblerMatrixBusyCountCache.put(this, value);
        }
    }
}
