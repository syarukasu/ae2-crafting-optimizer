package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
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
            Ae2PlanningInventorySnapshot networkSnapshot,
            AEKey requestedOutput) {
        Objects.requireNonNull(networkSnapshot, "networkSnapshot");
        return program.captureLongInventory(key ->
                key.equals(requestedOutput) ? 0L : networkSnapshot.amount(key));
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

}
