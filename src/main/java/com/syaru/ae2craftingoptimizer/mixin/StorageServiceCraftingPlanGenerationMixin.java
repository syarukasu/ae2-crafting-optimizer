package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.stacks.AEKey;
import appeng.me.service.StorageService;
import appeng.me.storage.NetworkStorage;
import com.syaru.ae2craftingoptimizer.access.StorageRevisionAccess;
import com.syaru.ae2craftingoptimizer.optimization.StorageRevisionTracker;
import com.syaru.ae2craftingoptimizer.optimization.StorageRevisionState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** AE2が外部mountの変更を検出した時点で、旧計画を新要求から分離する。 */
@Mixin(value = StorageService.class, remap = false)
public abstract class StorageServiceCraftingPlanGenerationMixin
        implements StorageRevisionAccess {
    @Unique
    private final StorageRevisionState aco$storageRevision = new StorageRevisionState();

    @Unique
    private boolean aco$storageContentsChangedWhileRefreshing;

    @Shadow
    @Final
    private NetworkStorage storage;

    @Inject(method = "<init>", at = @At("RETURN"), require = 1)
    private void aco$registerNetworkStorageOwner(CallbackInfo ci) {
        StorageRevisionTracker.register(
                storage,
                (StorageService) (Object) this);
    }

    @Override
    public long aco$captureStorageRevision() {
        return aco$storageRevision.capture();
    }

    @Override
    public long aco$currentStorageRevision() {
        return aco$storageRevision.current();
    }

    @Override
    public void aco$advanceStorageRevision() {
        aco$storageRevision.advance();
    }

    @Inject(method = "updateCachedStacks", at = @At("HEAD"), require = 1)
    private void aco$beginStorageRefresh(CallbackInfo ci) {
        aco$storageContentsChangedWhileRefreshing = false;
    }

    @Inject(method = "postWatcherUpdate", at = @At("HEAD"), require = 1)
    private void aco$rememberObservedStorageChange(AEKey what, long newAmount, CallbackInfo ci) {
        // 同じrefresh内の二件目以降は、一つ目で進めたrevisionを共有する。
        if (aco$storageContentsChangedWhileRefreshing) {
            return;
        }
        aco$storageContentsChangedWhileRefreshing = true;
        // watcherが例外を投げても変更済みcacheを旧revisionで公開しないよう、差分確定時に進める。
        aco$storageRevision.advance();
    }

    @Inject(method = "updateCachedStacks", at = @At("RETURN"), require = 1)
    private void aco$finishStorageRefresh(CallbackInfo ci) {
        // 次のrefreshが同じbooleanを再利用しないよう、正常終了時にも明示的に解除する。
        aco$storageContentsChangedWhileRefreshing = false;
    }

}
