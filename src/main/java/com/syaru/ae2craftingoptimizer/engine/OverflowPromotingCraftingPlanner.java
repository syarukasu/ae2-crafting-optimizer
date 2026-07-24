package com.syaru.ae2craftingoptimizer.engine;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * まず低コストなlong経路を試し、overflowした注文だけBigInteger経路で最初から再計算する。
 * 途中まで作ったlong結果は再利用しないため、昇格による二重計上は発生しない。
 */
public final class OverflowPromotingCraftingPlanner<K> {
    private final int maximumBits;

    public OverflowPromotingCraftingPlanner() {
        this(BigCountMath.HARD_MAXIMUM_BITS);
    }

    public OverflowPromotingCraftingPlanner(int maximumBits) {
        BigCountMath.requireMaximumBits(BigInteger.ZERO, "planner maximum", maximumBits);
        this.maximumBits = maximumBits;
    }

    public Result<K> plan(
            CompiledCraftingGraph<K> graph,
            K requestedKey,
            BigInteger requestedAmount,
            Map<K, BigInteger> inventory,
            Set<K> emittable) {
        return plan(graph, requestedKey, requestedAmount, inventory, emittable, PlanningGuard.none());
    }

    public Result<K> plan(
            CompiledCraftingGraph<K> graph,
            K requestedKey,
            BigInteger requestedAmount,
            Map<K, BigInteger> inventory,
            Set<K> emittable,
            PlanningGuard guard) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(requestedKey, "requestedKey");
        Objects.requireNonNull(inventory, "inventory");
        BigCountMath.requireMaximumBits(requestedAmount, "request", maximumBits);
        var compiled = CompiledRootProgram.tryCompile(graph, requestedKey, emittable::contains);
        // 決定的な経路は配列プログラムへ変換し、計画量に依存しない一巡計算を使う。
        if (compiled.isPresent()) {
            CompiledRootProgram<K> program = compiled.get();
            // 在庫も注文数もlong内なら、BigInteger配列を作らず高速経路から開始する。
            if (requestedAmount.bitLength() <= SIGNED_LONG_MAGNITUDE_BITS
                    && referencedInventoryFitsLong(program, inventory, maximumBits)) {
                CompiledRootProgram.InventorySnapshot<K> longInventory = program.captureLongInventory(
                        key -> checkedInventoryAmount(inventory, key, maximumBits).longValueExact());
                return plan(program, requestedAmount, longInventory, guard);
            }
            CompiledRootProgram.BigInventorySnapshot<K> bigInventory = program.captureBigInventory(
                    key -> checkedInventoryAmount(inventory, key, maximumBits),
                    maximumBits);
            return new BigResult<>(
                    program.planBig(requestedAmount, bigInventory, guard, maximumBits),
                    true);
        }

        // コンパイル不能な経路は従来Plannerへ戻し、結果をAuthoritativeとしては採用しない。
        Map<K, BigInteger> snapshot = immutableInventory(inventory, maximumBits);
        if (requestedAmount.bitLength() <= SIGNED_LONG_MAGNITUDE_BITS && allFitLong(snapshot)) {
            try {
                Map<K, Long> longs = new LinkedHashMap<>();
                snapshot.forEach((key, value) -> longs.put(key, value.longValueExact()));
                return new LongResult<>(new LongCraftingPlanner<K>().plan(
                        graph,
                        requestedKey,
                        requestedAmount.longValueExact(),
                        longs,
                        emittable,
                        guard), false);
            } catch (ArithmeticException overflow) {
                // 不変SnapshotからBigIntegerで再計算し、途中までのlong計算結果は絶対に混ぜない。
            }
        }
        return new BigResult<>(new BigCraftingPlanner<K>(maximumBits).plan(
                graph, requestedKey, requestedAmount, snapshot, emittable, guard), false);
    }

    /** AE2の参照キーだけを固定したSnapshotで、long優先・overflow時BigInteger再計算を行う。 */
    public Result<K> plan(
            CompiledRootProgram<K> program,
            BigInteger requestedAmount,
            CompiledRootProgram.InventorySnapshot<K> inventory,
            PlanningGuard guard) {
        Objects.requireNonNull(program, "program");
        BigCountMath.requireMaximumBits(requestedAmount, "request", maximumBits);
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(guard, "guard");
        // signed longへ収まる注文は、一時BigIntegerを作らない配列経路から開始する。
        if (requestedAmount.bitLength() <= SIGNED_LONG_MAGNITUDE_BITS) {
            try {
                return new LongResult<>(
                        program.planLong(requestedAmount.longValueExact(), inventory, guard),
                        true);
            } catch (CountOverflowException overflow) {
                // 同じ不変在庫Snapshotから最初から再計算し、途中のlong結果は一切引き継がない。
            }
        }
        return new BigResult<>(
                program.planBig(requestedAmount, inventory, guard, maximumBits),
                true);
    }

    /** 正確なBigInteger在庫Snapshotを使い、収まる注文だけlong高速経路へ落とす。 */
    public Result<K> plan(
            CompiledRootProgram<K> program,
            BigInteger requestedAmount,
            CompiledRootProgram.BigInventorySnapshot<K> inventory,
            PlanningGuard guard) {
        Objects.requireNonNull(program, "program");
        BigCountMath.requireMaximumBits(requestedAmount, "request", maximumBits);
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(guard, "guard");
        // 注文と全参照在庫がsigned long内なら、従来の低割当配列経路を優先する。
        if (requestedAmount.bitLength() <= SIGNED_LONG_MAGNITUDE_BITS
                && program.inventoryFitsSignedLong(inventory)) {
            try {
                return new LongResult<>(
                        program.planLong(
                                requestedAmount.longValueExact(),
                                program.narrowInventory(inventory),
                                guard),
                        true);
            } catch (CountOverflowException overflow) {
                // 需要展開だけがoverflowした場合は、不変BigInteger Snapshotから最初から再計算する。
            }
        }
        return new BigResult<>(
                program.planBig(requestedAmount, inventory, guard, maximumBits),
                true);
    }

    public Result<K> plan(
            CompiledCraftingGraph<K> graph,
            K requestedKey,
            BigInteger requestedAmount,
            Map<K, BigInteger> inventory) {
        return plan(graph, requestedKey, requestedAmount, inventory, Set.of());
    }

    private static <K> Map<K, BigInteger> immutableInventory(
            Map<K, BigInteger> inventory,
            int maximumBits) {
        Map<K, BigInteger> copy = new LinkedHashMap<>();
        Objects.requireNonNull(inventory, "inventory").forEach((key, value) -> {
            Objects.requireNonNull(key, "inventory key");
            copy.put(key, BigCountMath.requireMaximumBits(value, "inventory", maximumBits));
        });
        return Map.copyOf(copy);
    }

    private static boolean allFitLong(Map<?, BigInteger> values) {
        return values.values().stream()
                .allMatch(value -> value.bitLength() <= SIGNED_LONG_MAGNITUDE_BITS);
    }

    private static <K> boolean referencedInventoryFitsLong(
            CompiledRootProgram<K> program,
            Map<K, BigInteger> inventory,
            int maximumBits) {
        // Root Programが実際に読むキーだけを検査し、無関係な巨大在庫Mapは走査しない。
        for (int node = 0; node < program.nodeCount(); node++) {
            BigInteger amount = checkedInventoryAmount(
                    inventory,
                    program.keyAt(node),
                    maximumBits);
            // 一つでもsigned longを超える在庫値があればBigInteger Snapshotを選ぶ。
            if (amount.bitLength() > SIGNED_LONG_MAGNITUDE_BITS) {
                return false;
            }
        }
        return true;
    }

    private static <K> BigInteger checkedInventoryAmount(
            Map<K, BigInteger> inventory,
            K key,
            int maximumBits) {
        BigInteger amount = inventory.get(key);
        // Mapに存在しない参照キーは在庫0として扱う。
        if (amount == null) {
            return BigInteger.ZERO;
        }
        return BigCountMath.requireMaximumBits(amount, "inventory", maximumBits);
    }

    /** 正のsigned longへ無損失変換できる最大bit長。 */
    private static final int SIGNED_LONG_MAGNITUDE_BITS = Long.SIZE - 1;

    public sealed interface Result<K> permits LongResult, BigResult {
        boolean usesBigInteger();

        boolean craftable();

        /** True only for the graph subset whose arithmetic result is proven deterministic. */
        boolean provenEquivalent();
    }

    public record LongResult<K>(LongCraftingPlan<K> plan, boolean provenEquivalent) implements Result<K> {
        public LongResult {
            Objects.requireNonNull(plan, "plan");
        }

        @Override
        public boolean usesBigInteger() {
            return false;
        }

        @Override
        public boolean craftable() {
            return plan.craftable();
        }
    }

    public record BigResult<K>(BigCraftingPlan<K> plan, boolean provenEquivalent) implements Result<K> {
        public BigResult {
            Objects.requireNonNull(plan, "plan");
        }

        @Override
        public boolean usesBigInteger() {
            return true;
        }

        @Override
        public boolean craftable() {
            return plan.craftable();
        }
    }
}
