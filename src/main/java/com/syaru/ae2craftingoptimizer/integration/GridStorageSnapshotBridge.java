package com.syaru.ae2craftingoptimizer.integration;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.engine.BigKeyCounterSidecars;
import com.syaru.ae2craftingoptimizer.optimization.OptimizationMetrics;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 通常MEネットワーク端末へ、AE2 StorageServiceが管理する同じ在庫Snapshotを複製する。
 *
 * <p>ACO独自の時間固定cacheは使わない。StorageService#getCachedInventoryが必要に応じて
 * AE2自身のdirty flagから再構築するため、端末操作後の0在庫や新規在庫もAE2の失効規則で
 * 即座に更新される。</p>
 */
public final class GridStorageSnapshotBridge {
    private GridStorageSnapshotBridge() {
    }

    /** 対象がGrid本体のInventoryである場合だけ、StorageServiceのSnapshotを再利用する。 */
    public static KeyCounter availableStacks(
            MEStorage menuStorage,
            IGridNode networkNode) {
        Objects.requireNonNull(menuStorage, "menuStorage");
        // BigInteger Snapshot無効時とGridを持たない端末は、元のStorageを直接列挙する。
        if (!ACOConfig.enableExactBigIntegerInventorySnapshots()
                || networkNode == null) {
            return menuStorage.getAvailableStacks();
        }

        IGrid grid = networkNode.getGrid();
        // 切断直後のNodeではGridがnullになり得るため、端末本来の経路へ戻す。
        if (grid == null) {
            return menuStorage.getAvailableStacks();
        }

        IStorageService storageService = grid.getStorageService();
        return availableStacks(
                menuStorage,
                storageService.getInventory(),
                storageService::getCachedInventory,
                true,
                true);
    }

    static KeyCounter availableStacksForTests(
            MEStorage menuStorage,
            MEStorage gridStorage,
            Supplier<KeyCounter> cachedSnapshot,
            boolean enabled) {
        return availableStacks(
                menuStorage,
                gridStorage,
                cachedSnapshot,
                enabled,
                false);
    }

    private static KeyCounter availableStacks(
            MEStorage menuStorage,
            MEStorage gridStorage,
            Supplier<KeyCounter> cachedSnapshot,
            boolean enabled,
            boolean recordMetrics) {
        Objects.requireNonNull(menuStorage, "menuStorage");
        Objects.requireNonNull(gridStorage, "gridStorage");
        Objects.requireNonNull(cachedSnapshot, "cachedSnapshot");

        /*
         * Portable Cell、ME Chest、partition付きAddon Storage等へGrid全体を見せると
         * 表示対象が変わるため、同一Inventoryインスタンスの場合だけ再利用する。
         */
        if (!enabled || menuStorage != gridStorage) {
            return menuStorage.getAvailableStacks();
        }

        KeyCounter snapshot = Objects.requireNonNull(
                cachedSnapshot.get(),
                "cached storage snapshot");
        // 単体試験ではグローバル診断値を変更しない。
        if (recordMetrics) {
            OptimizationMetrics.recordExactStorageTerminalReuse();
        }
        // Menu側の差分計算が共有Counterを変更できないよう、Sidecarを含めて独立複製する。
        return BigKeyCounterSidecars.copyOf(snapshot);
    }
}
