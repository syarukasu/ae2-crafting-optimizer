package com.syaru.ae2craftingoptimizer.integration;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.api.big.BigCraftingEngineApi;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.engine.BigIntegerCraftingPlan;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Issue #125: exact Jobの排他所有権を取る前に、境界ME操作が成立するかを証明する。
 *
 * <p>物理実行の入出力は監査済みexactセルだけを通る ({@link ExactNetworkStorageBridge})。
 * そのrouteが無いGridで所有権を取ると、Jobは理由も出さずに永久待機する。
 * この事前検証は {@code exactVectorCrafting.enabled} のコメントが約束する
 * 「Unsupported graphs fall back before input ownership is transferred」を
 * ストレージ境界にも適用するもの。</p>
 */
public final class ExactBoundaryRoutePreflight {
    private ExactBoundaryRoutePreflight() {
    }

    /** 検証結果と、成立しない場合に提出側が取るべき道。 */
    public enum Outcome {
        /** 全境界操作が現在のGridで成立する。exact所有権を取ってよい。 */
        VIABLE,
        /** 成立しないが互換の外部コンシューマが居る。exact昇格せず素通しする。 */
        DELEGATE,
        /** 成立せず、他に実行できる者も居ない。明確な理由付きで拒否する。 */
        DECLINE
    }

    public record Result(Outcome outcome, String detail) {
        public boolean viable() {
            return outcome == Outcome.VIABLE;
        }

        public static Result ok() {
            return new Result(Outcome.VIABLE, "");
        }

        public static Result blocked(String detail) {
            /*
             * Issue #109の境界: 外部コンシューマの登録それ自体はAE2標準CPUの
             * 提出経路を変えない。ここで参照するのは「ACO自身が実行できない計画を
             * 誰に返すか」の分岐だけで、成立するrouteを持つ計画の提出には影響しない。
             */
            return new Result(
                    BigCraftingEngineApi.hasExternalBigIntegerPlanConsumer()
                            ? Outcome.DELEGATE
                            : Outcome.DECLINE,
                    detail);
        }
    }

    /**
     * 排他所有権を取る前の実行可能性判定。verify設定がoffなら従来どおり素通しする。
     */
    public static Result checkBeforeOwnership(
            IGrid grid,
            BigIntegerCraftingPlan exact,
            IActionSource source) {
        if (!ACOConfig.verifyExactStorageRouteBeforeOwnership()) {
            return Result.ok();
        }
        // Managerが新規開始しない設定でJobを所有すると、必ず進行ゼロの待機になる。
        if (!ACOConfig.enableExactBigIntegerPhysicalExecution()) {
            return Result.blocked("exact physical execution is disabled by config");
        }
        return check(grid, exact, source);
    }

    /**
     * 境界入力の一括確保と最終出力の納品が、監査済みexactセルで成立するかを検証する。
     *
     * <p>途中で解放される容量は考慮しない保守的判定だが、対象のExtendedAE Plus
     * Infinity BigIntegerセルは数量上限を持たないため、現実の偽陰性は
     * filter・priority・未搭載だけに絞られる。</p>
     */
    public static Result check(IGrid grid, BigIntegerCraftingPlan exact, IActionSource source) {
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(exact, "exact");
        Objects.requireNonNull(source, "source");
        Map<AEKey, BigInteger> inputs = positiveCounts(exact.exactPlan().usedInventory());
        AEKey outputKey = exact.finalOutput().what();
        BigInteger requestedAmount = exact.exactPlan().requestedAmount();

        Set<AEKey> boundaryKeys = new LinkedHashSet<>(inputs.keySet());
        boundaryKeys.add(outputKey);
        Optional<Map<AEKey, BigInteger>> stored =
                ExactNetworkStorageBridge.exactStoredAmounts(grid, boundaryKeys);
        if (stored.isEmpty()) {
            return Result.blocked(
                    "no audited exact BigInteger storage cell is mounted on this grid");
        }
        for (Map.Entry<AEKey, BigInteger> input : inputs.entrySet()) {
            BigInteger available = stored.orElseThrow()
                    .getOrDefault(input.getKey(), BigInteger.ZERO);
            if (available.compareTo(input.getValue()) < 0) {
                return Result.blocked(
                        "audited exact storage does not hold the boundary input "
                                + input.getKey()
                                + " (needs " + input.getValue()
                                + ", holds " + available + ")");
            }
        }
        if (!inputs.isEmpty()
                && !ExactNetworkStorageBridge.canExtractAll(grid, inputs, source)) {
            return Result.blocked(
                    "audited exact storage cannot release every boundary input");
        }
        if (requestedAmount.signum() > 0
                && !ExactNetworkStorageBridge.canInsertAll(
                        grid,
                        Map.of(outputKey, requestedAmount),
                        source)) {
            return Result.blocked(
                    "no audited exact storage cell accepts the final output " + outputKey);
        }
        return Result.ok();
    }

    private static Map<AEKey, BigInteger> positiveCounts(Map<AEKey, BigInteger> source) {
        Map<AEKey, BigInteger> positive = new LinkedHashMap<>();
        // 0量の計画エントリは境界操作にならないので、route判定から除く。
        for (Map.Entry<AEKey, BigInteger> entry : source.entrySet()) {
            if (entry.getValue().signum() > 0) {
                positive.put(entry.getKey(), entry.getValue());
            }
        }
        return positive;
    }
}
