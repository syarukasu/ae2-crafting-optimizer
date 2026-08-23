package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.behaviors.StackTransferContext;
import appeng.api.stacks.GenericStack;
import appeng.me.storage.ExternalStorageFacade;
import appeng.parts.automation.StorageImportStrategy;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.optimization.PreferredSlotScanOrder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Import Busの前回成功スロットを先に調べる。
 *
 * <p>AE2のtransfer本体はキャンセルせず、抽出、ME挿入、余剰返却、操作数会計を
 * すべてAE2へ残す。ACOは候補順序と成功位置だけを保持する。
 */
@Mixin(value = StorageImportStrategy.class, remap = false)
public abstract class StorageImportScanOrderMixin {
    @Unique
    private int aco$preferredSlot = -1;

    @Unique
    private int aco$currentSlot = -1;

    @Unique
    private boolean aco$scanOrderEnabled;

    @Inject(method = "transfer", at = @At("HEAD"))
    private void aco$beginTransfer(StackTransferContext context, CallbackInfoReturnable<Boolean> cir) {
        aco$scanOrderEnabled = ACOConfig.cacheImportBusLastSuccessfulSlot();
        aco$currentSlot = -1;
    }

    @Redirect(
            method = "transfer",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/me/storage/ExternalStorageFacade;getStackInSlot(I)Lappeng/api/stacks/GenericStack;"),
            require = 1)
    private GenericStack aco$readPreferredSlot(ExternalStorageFacade storage, int scanIndex) {
        // 機能OFF時はスロット番号を一切変更しない。
        if (!aco$scanOrderEnabled) {
            aco$currentSlot = scanIndex;
            return storage.getStackInSlot(scanIndex);
        }
        aco$currentSlot = PreferredSlotScanOrder.map(
                scanIndex,
                aco$preferredSlot,
                storage.getSlots());
        return storage.getStackInSlot(aco$currentSlot);
    }

    @Redirect(
            method = "transfer",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/behaviors/StackTransferContext;reduceOperationsRemaining(J)V"),
            require = 1)
    private void aco$rememberSuccessfulSlot(StackTransferContext context, long usedOperations) {
        // AE2が正の操作数を会計した時だけ、次回の優先候補として記録する。
        if (aco$scanOrderEnabled && usedOperations > 0L && aco$currentSlot >= 0) {
            aco$preferredSlot = aco$currentSlot;
        }
        context.reduceOperationsRemaining(usedOperations);
    }

    @Inject(method = "transfer", at = @At("RETURN"))
    private void aco$endTransfer(StackTransferContext context, CallbackInfoReturnable<Boolean> cir) {
        aco$currentSlot = -1;
        aco$scanOrderEnabled = false;
    }
}
