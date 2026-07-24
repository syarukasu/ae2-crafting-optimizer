package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.core.AEConfig;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.math.BigInteger;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

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

    /**
     * NetworkCraftingSimulationStateへ伝播した正確なSidecarから、参照キーだけを固定する。
     * Sidecarが不完全ならnullを返し、呼出側はAE2標準またはlong経路へ戻る。
     */
    @Nullable
    static CompiledRootProgram.BigInventorySnapshot<AEKey> captureExactNetworkSnapshot(
            CompiledRootProgram<AEKey> program,
            KeyCounter networkSnapshot,
            AEKey requestedOutput) {
        BigKeyCounterSidecars.Snapshot exact =
                BigKeyCounterSidecars.snapshot(networkSnapshot).orElse(null);
        // 一部storageの正確値を取得できなかったSnapshotは、在庫を推測せず採用しない。
        if (exact == null || !exact.complete()) {
            return null;
        }
        return program.captureBigInventory(
                key -> key.equals(requestedOutput) ? BigInteger.ZERO : exact.amount(key),
                ACOConfig.getBigIntegerMaximumBits());
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

    /** 計算後も正確なBigInteger在庫Snapshotが一致しているかを検証する。 */
    static boolean matchesLive(
            CompiledRootProgram<AEKey> program,
            CompiledRootProgram.BigInventorySnapshot<AEKey> snapshot,
            IGrid grid,
            IActionSource source,
            AEKey requestedOutput) {
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(source, "source");
        KeyCounter cached = grid.getStorageService().getCachedInventory();
        BigKeyCounterSidecars.Snapshot exact =
                BigKeyCounterSidecars.snapshot(cached).orElse(null);
        // 再検証時にSidecarが失われた場合は、丸めたlong値で一致扱いにしない。
        if (exact == null || !exact.complete()) {
            return false;
        }
        return program.inventoryMatches(
                snapshot,
                key -> key.equals(requestedOutput)
                        ? BigInteger.ZERO
                        : liveExactAmount(grid, source, cached, exact, key),
                ACOConfig.getBigIntegerMaximumBits());
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

    private static BigInteger liveExactAmount(
            IGrid grid,
            IActionSource source,
            KeyCounter cachedInventory,
            BigKeyCounterSidecars.Snapshot exact,
            AEKey key) {
        BigInteger cachedExact = exact.amount(key);
        // 正確在庫が0なら抽出Simulationを行わず、そのまま返す。
        if (cachedExact.signum() == 0) {
            return BigInteger.ZERO;
        }
        // Simulation無効時は、セルから取得した正確なBigInteger値を正本とする。
        if (!AEConfig.instance().isCraftingSimulatedExtraction()) {
            return cachedExact;
        }

        long facadeAmount = cachedInventory.get(key);
        // Facadeが非正ならAE2側では抽出対象にならないため、在庫0として再検証を失敗させる。
        if (facadeAmount <= 0L) {
            return BigInteger.ZERO;
        }
        long extractable = grid.getStorageService().getInventory().extract(
                key,
                facadeAmount,
                Actionable.SIMULATE,
                source);
        // Facade全量を抽出できない場合は、権限・partition・外部制限を優先する。
        if (extractable < facadeAmount) {
            return BigInteger.valueOf(Math.max(0L, extractable));
        }
        return cachedExact;
    }
}
