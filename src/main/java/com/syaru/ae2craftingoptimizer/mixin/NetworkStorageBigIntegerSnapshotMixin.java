package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import com.syaru.ae2craftingoptimizer.integration.BigIntegerStorageSnapshotBridge;
import com.syaru.ae2craftingoptimizer.integration.ExactNetworkStorageSnapshotCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** mounted storageごとの正確値を集計し、同一世代の重複Network走査を抑える。 */
@Mixin(value = NetworkStorage.class, priority = 900, remap = false)
public abstract class NetworkStorageBigIntegerSnapshotMixin {
    @Inject(
            method = "getAvailableStacks",
            at = @At("HEAD"),
            cancellable = true)
    private void aco$reuseExactNetworkSnapshot(
            KeyCounter networkCounter,
            CallbackInfo ci) {
        /*
         * hit時だけ元の全mount走査を止める。miss時はcaptureを開始し、
         * RETURNで同じtick・同じ世代の完成結果だけを保存する。
         */
        if (ExactNetworkStorageSnapshotCache.reuseOrBegin(
                (NetworkStorage) (Object) this,
                networkCounter)) {
            ci.cancel();
        }
    }

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

    @Inject(
            method = "getAvailableStacks",
            at = @At("RETURN"))
    private void aco$rememberExactNetworkSnapshot(
            KeyCounter networkCounter,
            CallbackInfo ci) {
        ExactNetworkStorageSnapshotCache.finish(
                (NetworkStorage) (Object) this,
                networkCounter);
    }

    @Inject(
            method = {"mount", "unmount"},
            at = @At("HEAD"))
    private void aco$invalidateExactSnapshotAfterMountChange(
            CallbackInfo ci) {
        // mount優先順または構成が変わる前に、依存する全Network Snapshotを失効させる。
        ExactNetworkStorageSnapshotCache.invalidateAll();
    }

    @Inject(
            method = "insert",
            at = @At("RETURN"))
    private void aco$invalidateExactSnapshotAfterInsert(
            AEKey key,
            long amount,
            Actionable mode,
            IActionSource source,
            CallbackInfoReturnable<Long> cir) {
        // Simulationや受入0件では在庫が変化しないため、再利用可能なSnapshotを維持する。
        if (mode == Actionable.MODULATE && cir.getReturnValue() > 0L) {
            ExactNetworkStorageSnapshotCache.invalidateAll();
        }
    }

    @Inject(
            method = "extract",
            at = @At("RETURN"))
    private void aco$invalidateExactSnapshotAfterExtract(
            AEKey key,
            long amount,
            Actionable mode,
            IActionSource source,
            CallbackInfoReturnable<Long> cir) {
        // 実抽出が成功した場合だけ、同じtickに残るSnapshotを無効化する。
        if (mode == Actionable.MODULATE && cir.getReturnValue() > 0L) {
            ExactNetworkStorageSnapshotCache.invalidateAll();
        }
    }
}
