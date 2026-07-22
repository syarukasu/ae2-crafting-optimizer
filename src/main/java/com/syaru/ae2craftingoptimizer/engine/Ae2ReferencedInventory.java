package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.core.AEConfig;
import java.util.Objects;

/** AE2の全在庫をMap化せず、Compiled Root Programが参照するキーだけを取得する。 */
final class Ae2ReferencedInventory {
    private Ae2ReferencedInventory() {
    }

    static CompiledRootProgram.InventorySnapshot<AEKey> captureNetworkSnapshot(
            CompiledRootProgram<AEKey> program,
            KeyCounter networkSnapshot,
            AEKey requestedOutput) {
        Objects.requireNonNull(networkSnapshot, "networkSnapshot");
        return program.captureLongInventory(key ->
                key.equals(requestedOutput) ? 0L : networkSnapshot.get(key));
    }

    static CompiledRootProgram.InventorySnapshot<AEKey> captureLive(
            CompiledRootProgram<AEKey> program,
            IGrid grid,
            IActionSource source,
            AEKey requestedOutput) {
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(source, "source");
        return program.captureLongInventory(key ->
                key.equals(requestedOutput) ? 0L : liveAmount(grid, source, key));
    }

    static boolean matchesLive(
            CompiledRootProgram<AEKey> program,
            CompiledRootProgram.InventorySnapshot<AEKey> snapshot,
            IGrid grid,
            IActionSource source,
            AEKey requestedOutput) {
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(source, "source");
        return program.inventoryMatches(snapshot, key ->
                key.equals(requestedOutput) ? 0L : liveAmount(grid, source, key));
    }

    private static long liveAmount(IGrid grid, IActionSource source, AEKey key) {
        var storage = grid.getStorageService();
        long cached = storage.getCachedInventory().get(key);
        // 在庫表示が0のキーへextract simulationを投げず、そのまま0を返す。
        if (cached <= 0L) {
            return 0L;
        }
        // AE2設定がsimulation extractionを要求する場合だけ、実際に取り出せる量へ絞る。
        if (AEConfig.instance().isCraftingSimulatedExtraction()) {
            return storage.getInventory().extract(
                    key,
                    cached,
                    Actionable.SIMULATE,
                    source);
        }
        return cached;
    }
}
