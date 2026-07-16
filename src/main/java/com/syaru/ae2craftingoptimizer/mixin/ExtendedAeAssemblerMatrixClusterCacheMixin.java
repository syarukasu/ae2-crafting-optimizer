package com.syaru.ae2craftingoptimizer.mixin;

import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.optimization.AssemblerMatrixBusyCountCache;
import com.syaru.ae2craftingoptimizer.optimization.OptimizationMetrics;
import com.syaru.ae2craftingoptimizer.optimization.ServerTickClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.syaru.ae2craftingoptimizer.optimization.ReflectionLookupCache;
import java.lang.reflect.Method;

@Pseudo
@Mixin(
        targets = "com.glodblock.github.extendedae.common.me.matrix.ClusterAssemblerMatrix",
        priority = 1100,
        remap = false)
public abstract class ExtendedAeAssemblerMatrixClusterCacheMixin {
    @Unique
    private long aco$statusTick = Long.MIN_VALUE;
    @Unique
    private boolean aco$statusValue;
    @Unique
    private boolean aco$statusSeen;
    @Unique
    private Object aco$lastAvailableCrafter;

    @Inject(method = "getAvailableCrafter", at = @At("HEAD"), cancellable = true, require = 0)
    private void aco$reuseAvailableCrafter(CallbackInfoReturnable<Object> cir) {
        if (!ACOConfig.cacheAssemblerMatrixRouting() || aco$lastAvailableCrafter == null) {
            return;
        }
        try {
            Method usedThread = ReflectionLookupCache.getMethod(
                    aco$lastAvailableCrafter.getClass(), "usedThread", new Class<?>[0]);
            if (((Number) usedThread.invoke(aco$lastAvailableCrafter)).intValue() < 8) {
                cir.setReturnValue(aco$lastAvailableCrafter);
            } else {
                aco$lastAvailableCrafter = null;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            aco$lastAvailableCrafter = null;
        }
    }

    @Inject(method = "getAvailableCrafter", at = @At("RETURN"), require = 0)
    private void aco$rememberAvailableCrafter(CallbackInfoReturnable<Object> cir) {
        if (ACOConfig.cacheAssemblerMatrixRouting()) {
            aco$lastAvailableCrafter = cir.getReturnValue();
        }
    }

    @Inject(method = "getBusyCrafterAmount", at = @At("HEAD"), cancellable = true, require = 0)
    private void aco$reuseBusyCount(CallbackInfoReturnable<Integer> cir) {
        Integer cached = AssemblerMatrixBusyCountCache.get(this);
        if (cached != null) {
            cir.setReturnValue(cached);
        }
    }

    @Inject(method = "getBusyCrafterAmount", at = @At("RETURN"), require = 0)
    private void aco$storeBusyCount(CallbackInfoReturnable<Integer> cir) {
        AssemblerMatrixBusyCountCache.put(this, cir.getReturnValue());
    }

    @Inject(method = {"updateCrafter", "addCrafter", "destroy"}, at = @At("HEAD"), require = 0)
    private void aco$invalidateBusyCount(CallbackInfo ci) {
        AssemblerMatrixBusyCountCache.invalidate(this);
        aco$lastAvailableCrafter = null;
    }

    @Inject(method = {"addTileEntity", "done", "destroy"}, at = @At("HEAD"), require = 0)
    private void aco$invalidateStatusCoalescingForStructureChange(CallbackInfo ci) {
        aco$statusSeen = false;
    }

    @Inject(method = "updateStatus", at = @At("HEAD"), cancellable = true, require = 0)
    private void aco$coalesceStatusUpdate(boolean status, CallbackInfo ci) {
        if (!ACOConfig.coalesceAssemblerMatrixStatusUpdates()) {
            return;
        }
        long tick = ServerTickClock.currentTick();
        if (tick == 0L) {
            return;
        }
        if (aco$statusSeen && aco$statusTick == tick && aco$statusValue == status) {
            OptimizationMetrics.recordAssemblerMatrixStatusUpdateCoalesced();
            ci.cancel();
            return;
        }
        aco$statusSeen = true;
        aco$statusTick = tick;
        aco$statusValue = status;
    }
}
