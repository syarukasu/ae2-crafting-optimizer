package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.storage.MEStorage;
import appeng.me.storage.DelegatingMEInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** DriveWatcherなどAE2標準の委譲Storageから、実際のセルInventoryを安全に取得する。 */
@Mixin(value = DelegatingMEInventory.class, remap = false)
public interface DelegatingMEInventoryAccessor {
    @Accessor("delegate")
    MEStorage aco$getDelegateStorage();
}
