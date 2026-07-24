package com.syaru.ae2craftingoptimizer.engine;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToLongFunction;

/** 通常計画へBigInteger Plannerを重ねる前に、全量クラフト時の安全な上限だけを調べる。 */
final class WideArithmeticPreflight {
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private WideArithmeticPreflight() {
    }

    /**
     * 配列化済みRoot Programを一巡し、個別値・総量・CPU byteのどこかがlongを超えるか調べる。
     * 在庫0を使うため、実在庫で途中停止する計画以上の安全な上限になる。
     */
    static <K> boolean requiresWideArithmetic(
            K root,
            BigInteger requestedAmount,
            CompiledRootProgram<K> program,
            ToLongFunction<K> amountPerByte,
            int maximumBits) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(amountPerByte, "amountPerByte");
        CompiledRootProgram.BigInventorySnapshot<K> emptyInventory =
                program.captureBigInventory(ignored -> BigInteger.ZERO, maximumBits);
        BigCraftingPlan<K> fullPlan = program.planBig(
                requestedAmount,
                emptyInventory,
                PlanningGuard.none(),
                maximumBits);
        // Pattern回数または各AEKey量が個別にlongを超える場合はWide計画が必須になる。
        if (containsValuePastLong(fullPlan.patternExecutions())
                || containsValuePastLong(fullPlan.usedInventory())
                || containsValuePastLong(fullPlan.emitted())
                || containsValuePastLong(fullPlan.missing())) {
            return true;
        }
        // 個別値が収まっても、複数キーの合計がlongを超える計画はAE2内部集計を保護する。
        if (sumExceedsLong(fullPlan.usedInventory())
                || sumExceedsLong(fullPlan.emitted())
                || sumExceedsLong(fullPlan.missing())) {
            return true;
        }
        BigInteger bytes = BigExactCraftingByteCounter.calculate(
                root,
                requestedAmount,
                program.patternsByOutput(),
                fullPlan.patternExecutions(),
                amountPerByte,
                maximumBits);
        return bytes.compareTo(LONG_MAX) > 0;
    }

    static <K> boolean requiresWideArithmetic(
            K root,
            BigInteger requestedAmount,
            Map<K, CompiledPattern<K>> patterns,
            ToLongFunction<K> amountPerByte,
            int maximumBits) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(patterns, "patterns");
        Objects.requireNonNull(amountPerByte, "amountPerByte");
        Map<String, BigInteger> fullExecutions = new LinkedHashMap<>();
        BigInteger leafDemand = collectFullDemand(
                root, requestedAmount, patterns, fullExecutions, maximumBits);
        // 個別Keyへ分けても、葉素材の合計がlongを超える計画はWide経路の候補になる。
        if (leafDemand.compareTo(LONG_MAX) > 0) {
            return true;
        }
        BigInteger bytes = BigExactCraftingByteCounter.calculate(
                root,
                requestedAmount,
                patterns,
                fullExecutions,
                amountPerByte,
                maximumBits);
        return bytes.compareTo(LONG_MAX) > 0;
    }

    private static <K> BigInteger collectFullDemand(
            K key,
            BigInteger requestedAmount,
            Map<K, CompiledPattern<K>> patterns,
            Map<String, BigInteger> executions,
            int maximumBits) {
        CompiledPattern<K> pattern = patterns.get(key);
        // Patternを持たない素材とEmitter出力は、Wide判定上の葉需要として数える。
        if (pattern == null) {
            return BigCountMath.requireMaximumBits(
                    requestedAmount, "wide-preflight/leaf", maximumBits);
        }
        BigInteger executionCount = BigCountMath.requireMaximumBits(
                BigCountMath.ceilDiv(
                        requestedAmount,
                        BigInteger.valueOf(pattern.outputAmount(key)),
                        "wide-preflight/executions/" + pattern.id()),
                "wide-preflight/executions/" + pattern.id(),
                maximumBits);
        // 合流するDAGでは同じPatternへ複数経路から到達するため、最後の経路で上書きしない。
        executions.merge(pattern.id(), executionCount, BigInteger::add);

        BigInteger total = BigInteger.ZERO;
        // 各入力を全量クラフトする上限需要を辿り、在庫で途中停止する実計画以上の安全な上限を作る。
        for (CompiledPattern.InputSlot<K> slot : pattern.inputs()) {
            CompiledPattern.Stack<K> input = slot.alternatives().get(0);
            BigInteger inputDemand = BigCountMath.multiply(
                    BigInteger.valueOf(input.amount()),
                    executionCount,
                    "wide-preflight/input/" + pattern.id(),
                    maximumBits);
            total = BigCountMath.add(
                    total,
                    collectFullDemand(
                            input.key(), inputDemand, patterns, executions, maximumBits),
                    "wide-preflight/leaf-total",
                    maximumBits);
        }
        return total;
    }

    private static boolean containsValuePastLong(Map<?, BigInteger> counts) {
        // 個別カウンタをAE2のlong APIへ無損失変換できるかだけを確認する。
        for (BigInteger amount : counts.values()) {
            // 一つでも上限を超えればBigInteger経路へ切り替える。
            if (amount.compareTo(LONG_MAX) > 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean sumExceedsLong(Map<?, BigInteger> counts) {
        BigInteger total = BigInteger.ZERO;
        // 加算自体もBigIntegerで行い、preflight中にlong overflowを起こさない。
        for (BigInteger amount : counts.values()) {
            total = total.add(amount);
            // 上限を超えた時点で残りを走査せずWide判定を確定する。
            if (total.compareTo(LONG_MAX) > 0) {
                return true;
            }
        }
        return false;
    }
}
