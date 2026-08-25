package com.syaru.ae2craftingoptimizer.integration;

import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.syaru.ae2craftingoptimizer.access.NetworkStorageMountsAccess;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;

/**
 * ME端末へ送る在庫全量Snapshotだけを、mount単位の飽和加算で構築する。
 *
 * <p>Issue #148: AE2の{@link KeyCounter}は同じキーの合計が{@link Long#MAX_VALUE}を
 * 超えると負数へwrapし、端末Packetがそのキーを空在庫として削除する。ここでは表示用の
 * 一時Counterだけを最大値へ飽和させる。在庫正本、搬入出、watcher、クラフト計算は変更しない。</p>
 */
public final class TerminalDisplaySnapshotProjection {
    private TerminalDisplaySnapshotProjection() {
    }

    /** 通常Grid端末だけを安全な表示Snapshotへ投影し、その他のStorageはAE2へ委譲する。 */
    public static KeyCounter availableStacks(MEStorage menuStorage) {
        return availableStacks(
                menuStorage,
                ACOConfig.enableExactBigIntegerInventorySnapshots());
    }

    /** ストレージモニターへ送る表示値だけを安全なSnapshotへ投影する。 */
    public static KeyCounter monitorStacks(IStorageService storageService) {
        Objects.requireNonNull(storageService, "storageService");
        KeyCounter cachedInventory = storageService.getCachedInventory();
        // 機能OFF時はNetworkStorageへ触れず、AE2が保持する同じCounterを返す。
        if (!ACOConfig.enableExactBigIntegerInventorySnapshots()) {
            return cachedInventory;
        }
        return monitorStacks(storageService.getInventory(), cachedInventory, true);
    }

    /** Forge Configを起動しない単体試験から、モニター表示境界を検証する内部入口。 */
    static KeyCounter monitorStacks(
            MEStorage networkStorage,
            KeyCounter cachedInventory,
            boolean enabled) {
        Objects.requireNonNull(networkStorage, "networkStorage");
        Objects.requireNonNull(cachedInventory, "cachedInventory");
        // exact mountを列挙できないStorageは推測で補正せず、AE2のcached値へ戻す。
        if (!enabled || !(networkStorage instanceof NetworkStorageMountsAccess)) {
            return cachedInventory;
        }
        return availableStacks(networkStorage, true);
    }

    /** NeoForge Configを起動しない単体試験から、有効状態だけを明示する内部入口。 */
    static KeyCounter availableStacks(MEStorage menuStorage, boolean enabled) {
        Objects.requireNonNull(menuStorage, "menuStorage");
        // 機能OFF時とGrid本体以外の端末は、AE2本来のSnapshotをそのまま使用する。
        if (!enabled || !(menuStorage instanceof NetworkStorageMountsAccess mountsAccess)) {
            return menuStorage.getAvailableStacks();
        }

        KeyCounter displaySnapshot = new KeyCounter();
        NavigableMap<Integer, List<MEStorage>> priorityInventory =
                mountsAccess.aco$getPriorityInventory();
        // NetworkStorage本来の優先順に全mountを一度だけ列挙し、キーごとに飽和加算する。
        for (List<MEStorage> priorityGroup : priorityInventory.values()) {
            // 同一優先度にmountされた全Storageを、表示Snapshotへ独立に統合する。
            for (MEStorage mountedStorage : priorityGroup) {
                BigIntegerStorageSnapshotBridge.collect(
                        mountedStorage,
                        displaySnapshot,
                        true);
            }
        }
        return displaySnapshot;
    }
}
