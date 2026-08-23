package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.menu.me.common.MEStorageMenu;
import com.syaru.ae2craftingoptimizer.integration.TerminalDisplaySnapshotProjection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Issue #148の飽和処理を、ME端末の全量表示Snapshot取得だけへ接続する。 */
@Mixin(value = MEStorageMenu.class, priority = 900, remap = false)
public abstract class MEStorageMenuDisplaySaturationMixin {
    @Redirect(
            method = "broadcastChanges",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/storage/MEStorage;getAvailableStacks()Lappeng/api/stacks/KeyCounter;"),
            require = 1)
    private KeyCounter aco$saturateTerminalDisplaySnapshot(MEStorage menuStorage) {
        return TerminalDisplaySnapshotProjection.availableStacks(menuStorage);
    }
}
