package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.menu.me.common.MEStorageMenu;
import com.syaru.ae2craftingoptimizer.integration.GridStorageSnapshotBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 通常Grid端末の全在庫列挙を、AE2 StorageServiceが同じ世代で作ったSnapshotへ統合する。
 *
 * <p>旧実装の数tick固定cacheは使用しない。Portable CellやAddon固有Storageも対象外にし、
 * AE2 Grid本体と同一Inventoryである場合だけ公式cacheを複製する。</p>
 */
@Mixin(value = MEStorageMenu.class, priority = 900, remap = false)
public abstract class MEStorageMenuGridSnapshotReuseMixin {
    @Redirect(
            method = "broadcastChanges",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/storage/MEStorage;getAvailableStacks()Lappeng/api/stacks/KeyCounter;"),
            require = 1)
    private KeyCounter aco$reuseAuthoritativeGridSnapshot(
            MEStorage menuStorage) {
        MEStorageMenu menu = (MEStorageMenu) (Object) this;
        return GridStorageSnapshotBridge.availableStacks(
                menuStorage,
                menu.getGridNode());
    }
}
