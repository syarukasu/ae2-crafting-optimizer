package com.syaru.ae2craftingoptimizer.mixin;

import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.optimization.OptimizationMetrics;
import com.syaru.ae2craftingoptimizer.optimization.ServerTickClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixCrafter", remap = false)
public abstract class ExtendedAeAssemblerMatrixCrafterCacheMixin {
    @Unique
    private long aco$threadCountTick = Long.MIN_VALUE;
    @Unique
    private int aco$threadCount;
    @Unique
    private boolean aco$threadCountValid;

    @Inject(method = "usedThread", at = @At("HEAD"), cancellable = true, require = 0)
    private void aco$reuseThreadCount(CallbackInfoReturnable<Integer> cir) {
        long tick = ServerTickClock.currentTick();
        if (ACOConfig.cacheAssemblerMatrixThreadCounts()
                && tick != 0L
                && aco$threadCountValid
                && aco$threadCountTick == tick) {
            OptimizationMetrics.recordAssemblerMatrixThreadCountHit();
            cir.setReturnValue(aco$threadCount);
        }
    }

    @Inject(method = "usedThread", at = @At("RETURN"), require = 0)
    private void aco$storeThreadCount(CallbackInfoReturnable<Integer> cir) {
        long tick = ServerTickClock.currentTick();
        if (ACOConfig.cacheAssemblerMatrixThreadCounts() && tick != 0L) {
            aco$threadCount = cir.getReturnValue();
            aco$threadCountTick = tick;
            aco$threadCountValid = true;
        }
    }

    @Inject(
            method = {"changeState", "onChangeInventory", "stop", "loadTag"},
            at = @At("HEAD"),
            require = 0)
    private void aco$invalidateThreadCountForVoidMutation(CallbackInfo ci) {
        aco$threadCountValid = false;
    }

    @Inject(method = "pushJob", at = @At("HEAD"), require = 0)
    private void aco$invalidateThreadCountForJob(CallbackInfoReturnable<Boolean> cir) {
        aco$threadCountValid = false;
    }

    @Inject(method = "tickingRequest", at = @At("HEAD"), require = 0)
    private void aco$invalidateThreadCountBeforeExecution(CallbackInfoReturnable<Object> cir) {
        aco$threadCountValid = false;
    }
}
