package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.IGridNode;
import appeng.hooks.ticking.TickHandler;
import appeng.me.service.StorageService;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = StorageService.class, remap = false)
public abstract class StorageServiceDeepCoalescingMixin {
    @Shadow
    private void updateCachedStacks() {
        throw new AssertionError();
    }

    @Unique
    private long aco$lastAggregateRefreshTick = Long.MIN_VALUE;

    @Redirect(
            method = "onServerEndTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/me/service/StorageService;updateCachedStacks()V"),
            require = 0)
    private void aco$coalesceAggregateRefresh(StorageService service) {
        long currentTick = TickHandler.instance().getCurrentTick();
        if (ACOConfig.deepNetworkForceUpdateCoalescing()
                && aco$lastAggregateRefreshTick != Long.MIN_VALUE
                && currentTick - aco$lastAggregateRefreshTick < ACOConfig.getDeepNetworkUpdateIntervalTicks()) {
            return;
        }

        updateCachedStacks();
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
