package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.core.AEConfig;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.integration.PlanningExactInventorySnapshot;
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
     * Sidecar全体が不完全でも、計画が参照するキーを個別に証明できれば採用する。
     * 参照キーのどれかが不明な場合だけnullを返し、呼出側は安全な経路へ戻る。
     */
    @Nullable
    static CompiledRootProgram.BigInventorySnapshot<AEKey> captureExactNetworkSnapshot(
            CompiledRootProgram<AEKey> program,
            KeyCounter networkSnapshot,
            AEKey requestedOutput) {
        BigKeyCounterSidecars.Snapshot exact =
                BigKeyCounterSidecars.snapshot(networkSnapshot).orElse(null);
        // 無関係なキーのアダプター失敗だけで、対象クラフト全体を捨てない。
        if (exact == null || !hasExactReferencedKeys(program, exact, requestedOutput)) {
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
        return matchesLive(
                program,
                snapshot,
                grid,
                source,
                requestedOutput,
                PlanningExactInventorySnapshot.capture(grid));
    }

    /** 呼出側が安全なスレッドで取得したexact在庫を使い、計画後の一致だけを検証する。 */
    static boolean matchesLive(
            CompiledRootProgram<AEKey> program,
            CompiledRootProgram.BigInventorySnapshot<AEKey> snapshot,
            IGrid grid,
            IActionSource source,
            AEKey requestedOutput,
            KeyCounter currentExactInventory) {
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(currentExactInventory, "currentExactInventory");
        BigKeyCounterSidecars.Snapshot exact = BigKeyCounterSidecars
                .snapshot(currentExactInventory)
                .orElse(null);
        // 再検証時も、計画が参照するキーだけはBigInteger正本で一致を確認する。
        if (exact == null || !hasExactReferencedKeys(program, exact, requestedOutput)) {
            return false;
        }
        return program.inventoryMatches(
                snapshot,
                key -> key.equals(requestedOutput)
                        ? BigInteger.ZERO
                        : liveExactAmount(grid, source, currentExactInventory, exact, key),
                ACOConfig.getBigIntegerMaximumBits());
    }

    private static boolean hasExactReferencedKeys(
            CompiledRootProgram<AEKey> program,
            BigKeyCounterSidecars.Snapshot exact,
            AEKey requestedOutput) {
        for (int node = 0; node < program.nodeCount(); node++) {
            AEKey key = program.keyAt(node);
            // AE2は注文の完成品を在庫計算から除外するため、出力自身は検証対象にしない。
            if (!key.equals(requestedOutput) && !exact.isExact(key)) {
                return false;
            }
        }
        return true;
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
