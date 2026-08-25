package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.KeyCounter;
import appeng.parts.reporting.AbstractMonitorPart;
import com.syaru.ae2craftingoptimizer.integration.TerminalDisplaySnapshotProjection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Issue #153の飽和処理を、ストレージモニターの表示値取得だけへ接続する。 */
@Mixin(value = AbstractMonitorPart.class, priority = 900, remap = false)
public abstract class AbstractMonitorPartDisplaySaturationMixin {
    @Redirect(
            method = "updateReportingValue",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/networking/storage/IStorageService;getCachedInventory()Lappeng/api/stacks/KeyCounter;"),
            require = 1)
    private KeyCounter aco$saturateStorageMonitorDisplaySnapshot(IStorageService storageService) {
        return TerminalDisplaySnapshotProjection.monitorStacks(storageService);
    }
}
