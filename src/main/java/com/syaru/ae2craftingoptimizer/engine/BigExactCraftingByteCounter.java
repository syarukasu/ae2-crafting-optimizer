package com.syaru.ae2craftingoptimizer.engine;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToLongFunction;

/**
 * AE2 19.2.17のCPU bytes式をBigIntegerの有理数として計算する。
 * doubleへ落とさないため、10^64級でも最後のceilまで桁落ちしない。
 * 入力Patternは単一候補かつ各キーが一度だけ現れる木構造でなければならない。
 * 共有DAGを渡した場合は近似値を返さず、明示的に失敗する。
 */
public final class BigExactCraftingByteCounter<K> {
    private final Map<K, CompiledPattern<K>> patterns;
    private final Map<String, BigInteger> executions;
    private final ToLongFunction<K> amountPerByte;
    private final int maximumBits;
    private final Set<K> visitedKeys = new HashSet<>();
    private BigInteger numerator = BigInteger.ZERO;
    private BigInteger denominator = BigInteger.ONE;

    private BigExactCraftingByteCounter(
            Map<K, CompiledPattern<K>> patterns,
            Map<String, BigInteger> executions,
            ToLongFunction<K> amountPerByte,
            int maximumBits) {
        this.patterns = Map.copyOf(Objects.requireNonNull(patterns, "patterns"));
        this.executions = Map.copyOf(Objects.requireNonNull(executions, "executions"));
        this.amountPerByte = Objects.requireNonNull(amountPerByte, "amountPerByte");
        this.maximumBits = maximumBits;
    }

    public static <K> BigInteger calculate(
            K root,
            BigInteger requestedAmount,
            Map<K, CompiledPattern<K>> patterns,
            Map<String, BigInteger> executions,
            ToLongFunction<K> amountPerByte,
            int maximumBits) {
        Objects.requireNonNull(root, "root");
        BigCountMath.requireMaximumBits(requestedAmount, "bytes/requestedAmount", maximumBits);
        BigExactCraftingByteCounter<K> counter = new BigExactCraftingByteCounter<>(
                patterns, executions, amountPerByte, maximumBits);
        BigInteger nodes = counter.visit(root, requestedAmount);
        counter.addInteger(BigCountMath.multiply(
                nodes, BigInteger.valueOf(8L), "bytes/nodeOverhead", maximumBits));
        BigInteger[] divided = counter.numerator.divideAndRemainder(counter.denominator);
        BigInteger result = divided[1].signum() == 0
                ? divided[0]
                : BigCountMath.add(
                        divided[0], BigInteger.ONE, "bytes/finalCeil", maximumBits);
        return BigCountMath.requireMaximumBits(result, "bytes/result", maximumBits);
    }

    private BigInteger visit(K key, BigInteger requestedAmount) {
        // 共有中間素材または循環を枝ごとに再展開してCPU bytesを二重計上しない。
        if (!visitedKeys.add(key)) {
            throw new IllegalArgumentException(
                    "exact byte counting requires a tree-shaped single-occurrence input graph");
        }
        long divisor = amountPerByte.applyAsLong(key);
        if (divisor <= 0L) {
            throw new IllegalArgumentException("amountPerByte must be positive");
        }
        addFraction(
                BigCountMath.multiply(
                        requestedAmount,
                        BigInteger.valueOf(8L),
                        "bytes/stack/" + key,
                        maximumBits),
                BigInteger.valueOf(divisor));
        BigInteger nodes = BigInteger.ONE;
        CompiledPattern<K> pattern = patterns.get(key);
        if (pattern == null) {
            return nodes;
        }
        BigInteger patternExecutions = executions.getOrDefault(pattern.id(), BigInteger.ZERO);
        if (patternExecutions.signum() <= 0) {
            return nodes;
        }
        for (int slot = 0; slot < pattern.inputs().size(); slot++) {
            CompiledPattern.Stack<K> input = pattern.inputs().get(slot).alternatives().get(0);
            BigInteger amount = BigCountMath.multiply(
                    BigInteger.valueOf(input.amount()),
                    patternExecutions,
                    "bytes/input/" + pattern.id(),
                    maximumBits);
            nodes = BigCountMath.add(
                    nodes,
                    visit(input.key(), amount),
                    "bytes/nodeCount",
                    maximumBits);
        }
        addInteger(patternExecutions);
        return nodes;
    }

    private void addInteger(BigInteger amount) {
        addFraction(amount, BigInteger.ONE);
    }

    private void addFraction(BigInteger addNumerator, BigInteger addDenominator) {
        BigCountMath.requireMaximumBits(addNumerator, "bytes/numerator", maximumBits);
        if (addDenominator.signum() <= 0) {
            throw new IllegalArgumentException("byte denominator must be positive");
        }
        BigInteger gcd = denominator.gcd(addDenominator);
        BigInteger leftMultiplier = addDenominator.divide(gcd);
        BigInteger rightMultiplier = denominator.divide(gcd);
        BigInteger nextNumerator = BigCountMath.add(
                BigCountMath.multiply(
                        numerator, leftMultiplier, "bytes/fraction-left", maximumBits),
                BigCountMath.multiply(
                        addNumerator, rightMultiplier, "bytes/fraction-right", maximumBits),
                "bytes/fraction-add",
                maximumBits);
        BigInteger nextDenominator = BigCountMath.multiply(
                denominator, leftMultiplier, "bytes/denominator", maximumBits);
        BigInteger reduction = nextNumerator.gcd(nextDenominator);
        numerator = nextNumerator.divide(reduction);
        denominator = nextDenominator.divide(reduction);
    }
}
