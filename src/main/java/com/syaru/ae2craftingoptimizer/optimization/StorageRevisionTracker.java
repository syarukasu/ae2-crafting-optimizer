package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.networking.IGrid;
import appeng.me.service.StorageService;
import appeng.me.storage.NetworkStorage;
import com.syaru.ae2craftingoptimizer.access.NetworkStorageRevisionAccess;
import com.syaru.ae2craftingoptimizer.access.StorageRevisionAccess;
import java.util.Objects;

/**
 * 一つのAE2 gridが参照するstorage内容とmount構成の単調revisionを所有する。
 * workerへは{@link RevisionToken}だけを渡し、mutableなGridを読み直させない。
 */
public final class StorageRevisionTracker {
    private static final long INITIAL_REVISION = 1L;

    private StorageRevisionTracker() {
    }

    /**
     * server threadでAE2の遅延在庫cacheを確定してから、そのnetworkのrevisionを固定する。
     */
    public static RevisionToken refreshAndCapture(IGrid grid) {
        StorageService storageService = storageService(grid);
        // AE2自身の差分検出を先に完了させ、ACOで全在庫を二重走査しない。
        storageService.getCachedInventory();
        StorageRevisionAccess owner = revisionOwner(storageService);
        return new RevisionToken(
                owner,
                owner.aco$captureStorageRevision());
    }

    /** 現在値を更新せず固定する。既にserver-thread capture済みの互換入口だけで使う。 */
    public static RevisionToken capture(IGrid grid) {
        StorageRevisionAccess owner = revisionOwner(storageService(grid));
        return new RevisionToken(owner, owner.aco$currentStorageRevision());
    }

    /** NetworkStorageを所有gridへ接続し、別networkのrevisionを混在させない。 */
    public static void register(NetworkStorage networkStorage, StorageService storageService) {
        Objects.requireNonNull(networkStorage, "networkStorage");
        Objects.requireNonNull(storageService, "storageService");
        networkRevisionAccess(networkStorage)
                .aco$setStorageRevisionOwner(revisionOwner(storageService));
    }

    /**
     * 所有gridに属するNetworkStorageのmutationまたはmount変更だけを失効させる。
     * standaloneなNetworkStorageはACO計画cacheの所有外なので何もしない。
     */
    public static void markNetworkStorageChanged(NetworkStorage networkStorage) {
        Objects.requireNonNull(networkStorage, "networkStorage");
        networkRevisionAccess(networkStorage).aco$advanceStorageRevisionOwner();
    }

    /** workerがmutableなGridを読まず、capture時と同じstorage revisionかだけを検証する。 */
    public static boolean isCurrent(RevisionToken token) {
        Objects.requireNonNull(token, "token");
        return token.owner().aco$currentStorageRevision() == token.revision();
    }

    private static StorageService storageService(IGrid grid) {
        Objects.requireNonNull(grid, "grid");
        var storageService = grid.getStorageService();
        // ACOのMixin対象とrevision所有者が異なる実装では、誤ったcache共有を行わない。
        if (!(storageService instanceof StorageService concrete)) {
            throw new IllegalStateException(
                    "Unsupported AE2 storage service implementation: " + storageService.getClass().getName());
        }
        return concrete;
    }

    private static StorageRevisionAccess revisionOwner(StorageService storageService) {
        // Issue #167: revision Mixinが欠落した環境で不正なcache共有を継続しない。
        if (!(storageService instanceof StorageRevisionAccess access)) {
            throw new IllegalStateException(
                    "ACO storage revision access is unavailable on "
                            + storageService.getClass().getName());
        }
        return access;
    }

    private static NetworkStorageRevisionAccess networkRevisionAccess(NetworkStorage networkStorage) {
        // Issue #167: standaloneを含む全NetworkStorageへ同じMixinが必要である。
        if (!(networkStorage instanceof NetworkStorageRevisionAccess access)) {
            throw new IllegalStateException(
                    "ACO network storage revision access is unavailable on "
                            + networkStorage.getClass().getName());
        }
        return access;
    }

    /** 参照同一性を持つstorage ownerと、その時点の単調revision。 */
    public record RevisionToken(StorageRevisionAccess owner, long revision) {
        public RevisionToken {
            Objects.requireNonNull(owner, "owner");
            // 0以下は未登録または不正なtokenなので、plannerへ公開しない。
            if (revision < INITIAL_REVISION) {
                throw new IllegalArgumentException("storage revision must be positive");
            }
        }
    }
}
