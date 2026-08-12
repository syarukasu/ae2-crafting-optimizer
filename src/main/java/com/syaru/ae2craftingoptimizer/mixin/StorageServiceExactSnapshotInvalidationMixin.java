package com.syaru.ae2craftingoptimizer.mixin;

import appeng.me.service.StorageService;
import com.syaru.ae2craftingoptimizer.access.ExactBigIntegerInventoryHookAccess;
import com.syaru.ae2craftingoptimizer.integration.ExactNetworkStorageSnapshotCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** AE2が集約在庫をdirtyにした時、ACOの同一tick Snapshotも同時に失効させる。 */
@Mixin(value = StorageService.class, remap = false)
public abstract class StorageServiceExactSnapshotInvalidationMixin
        implements ExactBigIntegerInventoryHookAccess {
    @Inject(
            method = "invalidateCache",
            at = @At("HEAD"),
            require = 1)
    private void aco$invalidateExactNetworkSnapshots(
            CallbackInfo ci) {
        ExactNetworkStorageSnapshotCache.invalidateAll();
    }
}
