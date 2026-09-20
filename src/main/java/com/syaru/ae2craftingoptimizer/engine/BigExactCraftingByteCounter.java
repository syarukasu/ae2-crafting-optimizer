package com.syaru.ae2craftingoptimizer.engine;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToLongFunction;

/**
 * AE2 15.4.10のCPU bytes式をBigIntegerの有理数として計算する。
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
    private BigInteger wholeBytes = BigInteger.ZERO;
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
        return counter.roundedBytes();
    }

    static <K> BigInteger calculate(CraftingPlanTrace<K> trace, ToLongFunction<K> amountPerByte, int maximumBits) {
        var counter = new BigExactCraftingByteCounter<K>(Map.of(), Map.of(), amountPerByte, maximumBits);
        for (var charge : trace.charges()) {
            if (charge.key() == null) {
                counter.addInteger(charge.amount());
            } else {
                counter.addStackAmount(charge.amount(), amountPerByte.applyAsLong(charge.key()));
            }
        }
        return counter.roundedBytes();
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
        addStackAmount(requestedAmount, divisor);
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
        wholeBytes = BigCountMath.add(wholeBytes, amount, "bytes/integer", maximumBits);
    }

    private BigInteger roundedBytes() {
        return numerator.signum() == 0 ? BigCountMath.requireMaximumBits(wholeBytes, "bytes/result", maximumBits)
                : BigCountMath.add(wholeBytes, BigInteger.ONE, "bytes/finalCeil", maximumBits);
    }

    private void addStackAmount(BigInteger amount, long divisor) {
        BigCountMath.requireMaximumBits(amount, "bytes/stackAmount", maximumBits);
        if (divisor <= 0) {
            throw new IllegalArgumentException("amountPerByte must be positive");
        }
        BigInteger unit = BigInteger.valueOf(divisor);
        BigInteger[] parts = amount.divideAndRemainder(unit);
        // Issue #190: only the converted byte cost, not amount * 8, consumes CPU capacity.
        addInteger(BigCountMath.multiply(parts[0], BigInteger.valueOf(8), "bytes/wholeStack", maximumBits));
        // The remainder is below a positive long unit; scaling it needs at most 66 bits.
        addFraction(parts[1].multiply(BigInteger.valueOf(8)), unit);
    }

    private void addFraction(BigInteger addNumerator, BigInteger addDenominator) {
        BigInteger[] parts = addNumerator.divideAndRemainder(addDenominator);
        addInteger(parts[0]);
        if (parts[1].signum() == 0) return;
        BigInteger gcd = denominator.gcd(addDenominator);
        BigInteger leftMultiplier = addDenominator.divide(gcd);
        BigInteger rightMultiplier = denominator.divide(gcd);
        // Both operands are fractions below one. Temporaries need at most maximumBits + 64.
        BigInteger nextNumerator = numerator.multiply(leftMultiplier)
                .add(parts[1].multiply(rightMultiplier));
        BigInteger nextDenominator = denominator.multiply(leftMultiplier);
        BigInteger reduction = nextNumerator.gcd(nextDenominator);
        BigInteger reducedDenominator = BigCountMath.requireMaximumBits(
                nextDenominator.divide(reduction), "bytes/denominator", maximumBits);
        BigInteger[] normalized = nextNumerator.divide(reduction).divideAndRemainder(reducedDenominator);
        addInteger(normalized[0]);
        numerator = normalized[1];
        denominator = reducedDenominator;
    }
}
