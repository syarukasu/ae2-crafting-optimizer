package com.syaru.ae2craftingoptimizer.integration;

import appeng.api.networking.IGrid;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import com.syaru.ae2craftingoptimizer.access.NetworkStorageMountsAccess;
import com.syaru.ae2craftingoptimizer.engine.BigKeyCounterSidecars;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

/**
 * BigInteger Planner専用の在庫Snapshotを作る。
 *
 * <p>Issue #109の回帰では、正確量の取得をNetworkStorage#getAvailableStacksへ
 * 常時注入したため、端末・バス・通常クラフトまでACOの集計処理を通っていた。
 * このクラスは計算開始時だけmountを列挙し、通常AE2の在庫一覧には介入しない。</p>
 */
public final class PlanningExactInventorySnapshot {
    private PlanningExactInventorySnapshot() {
    }

    /** AE2の現在のmount構成から、long互換値とBigInteger正本を一度だけ取得する。 */
    public static KeyCounter capture(IGrid grid) {
        Objects.requireNonNull(grid, "grid");
        MEStorage networkInventory = grid.getStorageService().getInventory();
        // 標準NetworkStorageなら各mountを個別に読み、BigIntegerセルの正本を失わない。
        if (networkInventory instanceof NetworkStorage
                && networkInventory instanceof NetworkStorageMountsAccess mounts) {
            return captureMountedStorages(mounts.aco$getPriorityInventory().values());
        }

        KeyCounter result = new KeyCounter();
        // UELM等の独自Network実装では、公開MEStorageを一回だけ安全に取得する。
        BigIntegerStorageSnapshotBridge.collect(networkInventory, result, true);
        return result;
    }

    /** mount単位の取得規則をMinecraft起動なしで検証するためのpackage-private入口。 */
    static KeyCounter captureMountedStorages(
            Iterable<? extends Iterable<MEStorage>> priorities) {
        Objects.requireNonNull(priorities, "priorities");
        KeyCounter facade = new KeyCounter();
        BigKeyCounterSidecars.Accumulator exact = new BigKeyCounterSidecars.Accumulator();
        Set<MEStorage> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        // 優先度は合計量へ影響しないが、AE2と同じ順序で決定的に列挙する。
        for (Iterable<MEStorage> priority : priorities) {
            // 同一Storageが複数回mountされても在庫量を二重計上しない。
            for (MEStorage mountedStorage : priority) {
                if (!visited.add(mountedStorage)) {
                    continue;
                }
                BigIntegerStorageSnapshotBridge.collect(mountedStorage, facade, exact);
            }
        }
        // mountがない場合は従来どおりSidecarを新設しない。
        if (visited.isEmpty()) {
            return facade;
        }
        // Issue #156: 空Counterへ正本を一度だけ付けてから表示値を移す。二重加算しない。
        KeyCounter result = new KeyCounter();
        BigKeyCounterSidecars.merge(result, exact.snapshot());
        result.addAll(facade);
        return result;
    }
}
