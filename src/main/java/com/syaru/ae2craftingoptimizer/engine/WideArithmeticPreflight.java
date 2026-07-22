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
        executions.put(pattern.id(), executionCount);

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
}
