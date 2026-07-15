package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.IGridNode;
import appeng.hooks.ticking.TickHandler;
import appeng.me.service.StorageService;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = StorageService.class, remap = false)
public abstract class StorageServiceDeepCoalescingMixin {
    @Unique
    private long aco$lastAggregateRefreshTick = Long.MIN_VALUE;

    @Inject(method = "onServerEndTick", at = @At("HEAD"), cancellable = true)
    private void aco$coalesceAggregateRefresh(CallbackInfo ci) {
        if (!ACOConfig.deepNetworkForceUpdateCoalescing()) {
            return;
        }

        long currentTick = TickHandler.instance().getCurrentTick();
        int interval = ACOConfig.getDeepNetworkUpdateIntervalTicks();
        if (aco$lastAggregateRefreshTick != Long.MIN_VALUE
                && currentTick - aco$lastAggregateRefreshTick < interval) {
            ci.cancel();
            return;
        }
        aco$lastAggregateRefreshTick = currentTick;
    }

    @Inject(method = "addNode", at = @At("HEAD"))
    private void aco$refreshAfterNodeAdd(IGridNode node, CompoundTag savedData, CallbackInfo ci) {
        aco$lastAggregateRefreshTick = Long.MIN_VALUE;
    }

    @Inject(method = "removeNode", at = @At("HEAD"))
    private void aco$refreshAfterNodeRemove(IGridNode node, CallbackInfo ci) {
        aco$lastAggregateRefreshTick = Long.MIN_VALUE;
    }

    @Inject(method = "invalidateCache", at = @At("HEAD"))
    private void aco$refreshAfterExplicitInvalidation(CallbackInfo ci) {
        aco$lastAggregateRefreshTick = Long.MIN_VALUE;
    }
}
