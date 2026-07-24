package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import com.syaru.ae2craftingoptimizer.integration.BigIntegerStorageSnapshotBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** mounted storageごとの在庫寄与を分離し、long overflowなしで集計する。 */
@Mixin(value = NetworkStorage.class, priority = 900, remap = false)
public abstract class NetworkStorageBigIntegerSnapshotMixin {
    @Redirect(
            method = "getAvailableStacks",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/storage/MEStorage;getAvailableStacks(Lappeng/api/stacks/KeyCounter;)V"),
            require = 1)
    private void aco$collectExactAvailableStacks(
            MEStorage mountedStorage,
            KeyCounter networkCounter) {
        BigIntegerStorageSnapshotBridge.collect(mountedStorage, networkCounter);
    }
}
