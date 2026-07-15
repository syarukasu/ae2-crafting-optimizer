package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.IGridNode;
import appeng.api.networking.storage.IStorageWatcherNode;
import appeng.api.stacks.AEKey;
import appeng.me.service.StorageService;
import com.syaru.ae2craftingoptimizer.optimization.CraftingCalculationDeduplicator;
import com.syaru.ae2craftingoptimizer.optimization.StorageWatcherUpdateBuffer;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = StorageService.class, remap = false)
public abstract class StorageServiceWatcherThrottleMixin {
    @Redirect(
            method = "postWatcherUpdate",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/networking/storage/IStorageWatcherNode;onStackChange(Lappeng/api/stacks/AEKey;J)V"),
            require = 0)
    private void aco$bufferStorageWatcherUpdate(IStorageWatcherNode watcher, AEKey key, long amount) {
        StorageWatcherUpdateBuffer.onStackChange(watcher, key, amount);
    }

    @Inject(method = "onServerEndTick", at = @At("TAIL"))
    private void aco$flushStorageWatcherUpdatesOnTick(CallbackInfo ci) {
        StorageWatcherUpdateBuffer.tick();
    }

    @Inject(method = "addNode", at = @At("HEAD"))
    private void aco$flushBeforeStorageNodeAdd(IGridNode node, CompoundTag savedData, CallbackInfo ci) {
        StorageWatcherUpdateBuffer.flush();
        CraftingCalculationDeduplicator.clearCompleted("storage node added");
    }

    @Inject(method = "removeNode", at = @At("HEAD"))
    private void aco$flushBeforeStorageNodeRemove(IGridNode node, CallbackInfo ci) {
        StorageWatcherUpdateBuffer.flush();
        CraftingCalculationDeduplicator.clearCompleted("storage node removed");
    }

    @Inject(method = "invalidateCache", at = @At("HEAD"))
    private void aco$flushBeforeStorageCacheInvalidation(CallbackInfo ci) {
        StorageWatcherUpdateBuffer.flush();
        CraftingCalculationDeduplicator.clearCompleted("storage cache invalidated");
    }
}
