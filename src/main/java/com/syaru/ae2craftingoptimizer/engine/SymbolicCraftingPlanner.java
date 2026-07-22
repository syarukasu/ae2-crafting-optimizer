package com.syaru.ae2craftingoptimizer.engine;

import java.math.BigInteger;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 決定的なPattern DAGを {@link CompiledRootProgram} へ変換し、数式一巡で計画する公開Facade。
 * AE2実環境では世代付きSnapshotがRoot Program自体を保持し、このFacadeの再コンパイルも避ける。
 */
public final class SymbolicCraftingPlanner<K> {
    public Optional<LongCraftingPlan<K>> tryPlanLong(
            CompiledCraftingGraph<K> graph,
            K requestedKey,
            long requestedAmount,
            Map<K, Long> inventory,
            Set<K> emittable,
            PlanningGuard guard) {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(emittable, "emittable");
        Optional<CompiledRootProgram<K>> compiled = CompiledRootProgram.tryCompile(
                graph,
                requestedKey,
                emittable::contains);
        // 曖昧、循環、複数出力などを含む経路は呼出側のAE2 Fallbackへ返す。
        if (compiled.isEmpty()) {
            return Optional.empty();
        }
        CompiledRootProgram<K> program = compiled.get();
        CompiledRootProgram.InventorySnapshot<K> snapshot = program.captureLongInventory(
                key -> checkedLongAmount(inventory, key));
        return Optional.of(program.planLong(requestedAmount, snapshot, guard));
    }

    public Optional<BigCraftingPlan<K>> tryPlanBig(
            CompiledCraftingGraph<K> graph,
            K requestedKey,
            BigInteger requestedAmount,
            Map<K, BigInteger> inventory,
            Set<K> emittable,
            PlanningGuard guard) {
        return tryPlanBig(
                graph,
                requestedKey,
                requestedAmount,
                inventory,
                emittable,
                guard,
                BigCountMath.HARD_MAXIMUM_BITS);
    }

    public Optional<BigCraftingPlan<K>> tryPlanBig(
            CompiledCraftingGraph<K> graph,
            K requestedKey,
            BigInteger requestedAmount,
            Map<K, BigInteger> inventory,
            Set<K> emittable,
            PlanningGuard guard,
            int maximumBits) {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(emittable, "emittable");
        Optional<CompiledRootProgram<K>> compiled = CompiledRootProgram.tryCompile(
                graph,
                requestedKey,
                emittable::contains);
        // 数式として一意に証明できない経路はBigIntegerでも近似せずFallbackする。
        if (compiled.isEmpty()) {
            return Optional.empty();
        }
        CompiledRootProgram<K> program = compiled.get();
        CompiledRootProgram.BigInventorySnapshot<K> snapshot = program.captureBigInventory(
                key -> checkedBigAmount(inventory, key),
                maximumBits);
        return Optional.of(program.planBig(requestedAmount, snapshot, guard, maximumBits));
    }

    /** 旧Topology cacheの呼出互換。Root Programの本番cacheはAE2世代Snapshotが所有する。 */
    public static void clearTopologyCache() {
        // 静的な計画結果を保持しないため、明示的に破棄する対象はない。
    }

    private static <K> long checkedLongAmount(Map<K, Long> inventory, K key) {
        Long amount = inventory.get(key);
        // Mapに存在しないキーは在庫0として扱う。
        if (amount == null) {
            return 0L;
        }
        return CheckedLongMath.requireNonNegative(amount, "symbolic/inventory");
    }

    private static <K> BigInteger checkedBigAmount(Map<K, BigInteger> inventory, K key) {
        BigInteger amount = inventory.get(key);
        // Mapに存在しないキーは在庫0として扱う。
        if (amount == null) {
            return BigInteger.ZERO;
        }
        return amount;
    }
}
