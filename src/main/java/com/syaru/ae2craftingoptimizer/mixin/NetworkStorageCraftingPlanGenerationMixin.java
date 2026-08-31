package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import com.syaru.ae2craftingoptimizer.access.NetworkStorageRevisionAccess;
import com.syaru.ae2craftingoptimizer.access.StorageRevisionAccess;
import com.syaru.ae2craftingoptimizer.optimization.StorageRevisionTracker;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 成立したME在庫変更だけを、計画共有revisionへ通知する。 */
@Mixin(value = NetworkStorage.class, remap = false)
public abstract class NetworkStorageCraftingPlanGenerationMixin
        implements NetworkStorageRevisionAccess {
    @Unique
    @Nullable
    private StorageRevisionAccess aco$storageRevisionOwner;

    @Override
    public void aco$setStorageRevisionOwner(StorageRevisionAccess owner) {
        // Issue #167: 一つのNetworkStorageを別gridへ再接続するとrevision境界が混在する。
        if (aco$storageRevisionOwner != null && aco$storageRevisionOwner != owner) {
            throw new IllegalStateException("NetworkStorage owner changed unexpectedly");
        }
        aco$storageRevisionOwner = owner;
    }

    @Override
    public void aco$advanceStorageRevisionOwner() {
        StorageRevisionAccess owner = aco$storageRevisionOwner;
        // 全NetworkStorageへMixinされるため、standaloneインスタンスは正常な対象外である。
        if (owner == null) {
            return;
        }
        owner.aco$advanceStorageRevision();
    }
    @Inject(method = "mount", at = @At("RETURN"), require = 1)
    private void aco$advanceAfterMount(int priority, MEStorage inventory, CallbackInfo ci) {
        // Issue #167: 同量のstorageへ差し替わってもroutingと抽出可否が変わるため失効させる。
        StorageRevisionTracker.markNetworkStorageChanged((NetworkStorage) (Object) this);
    }

    @Inject(method = "unmount", at = @At("RETURN"), require = 1)
    private void aco$advanceAfterUnmount(MEStorage inventory, CallbackInfo ci) {
        // Issue #167: mount解除はcached amountが同じでも計画の参照先を変更する。
        StorageRevisionTracker.markNetworkStorageChanged((NetworkStorage) (Object) this);
    }

    @Inject(method = "insert", at = @At("RETURN"), require = 1)
    private void aco$advanceAfterInsert(
            AEKey key,
            long amount,
            Actionable mode,
            IActionSource source,
            CallbackInfoReturnable<Long> cir) {
        // Issue #167: simulationまたは0件受理では正本在庫が変化していない。
        if (mode != Actionable.MODULATE || cir.getReturnValue() <= 0L) {
            return;
        }
        StorageRevisionTracker.markNetworkStorageChanged((NetworkStorage) (Object) this);
    }

    @Inject(method = "extract", at = @At("RETURN"), require = 1)
    private void aco$advanceAfterExtract(
            AEKey key,
            long amount,
            Actionable mode,
            IActionSource source,
            CallbackInfoReturnable<Long> cir) {
        // Issue #167: simulationまたは0件抽出では正本在庫が変化していない。
        if (mode != Actionable.MODULATE || cir.getReturnValue() <= 0L) {
            return;
        }
        StorageRevisionTracker.markNetworkStorageChanged((NetworkStorage) (Object) this);
    }
}
