package com.syaru.ae2craftingoptimizer.engine;

import java.util.Map;
import java.util.Objects;
import java.util.function.ToLongFunction;

/** AE2 15.4.10の線形CraftingTreeと同じ順番でCPU bytesを再計算する。 */
public final class ExactCraftingByteCounter<K> {
    private final Map<K, CompiledPattern<K>> patterns;
    private final Map<String, Long> executions;
    private final ToLongFunction<K> amountPerByte;
    private double bytes;

    private ExactCraftingByteCounter(
            Map<K, CompiledPattern<K>> patterns,
            Map<String, Long> executions,
            ToLongFunction<K> amountPerByte) {
        this.patterns = Map.copyOf(Objects.requireNonNull(patterns, "patterns"));
        this.executions = Map.copyOf(Objects.requireNonNull(executions, "executions"));
        this.amountPerByte = Objects.requireNonNull(amountPerByte, "amountPerByte");
    }

    public static <K> long calculate(
            K root,
            long requestedAmount,
            Map<K, CompiledPattern<K>> patterns,
            Map<String, Long> executions,
            ToLongFunction<K> amountPerByte) {
        Objects.requireNonNull(root, "root");
        CheckedLongMath.requireNonNegative(requestedAmount, "bytes/requestedAmount");
        ExactCraftingByteCounter<K> counter =
                new ExactCraftingByteCounter<>(patterns, executions, amountPerByte);
        long nodes = counter.visit(root, requestedAmount);
        counter.add((double) CheckedLongMath.multiply(nodes, 8L, "bytes/nodeOverhead"));
        return (long) Math.ceil(counter.bytes);
    }

    private long visit(K key, long requestedAmount) {
        long divisor = amountPerByte.applyAsLong(key);
        if (divisor <= 0L) {
            throw new IllegalArgumentException("amountPerByte must be positive");
        }
        add((double) requestedAmount / (double) divisor * 8.0D);
        long nodes = 1L;
        CompiledPattern<K> pattern = patterns.get(key);
        if (pattern == null) {
            return nodes;
        }
        long patternExecutions = executions.getOrDefault(pattern.id(), 0L);
        if (patternExecutions <= 0L) {
            return nodes;
        }
        for (int slot = 0; slot < pattern.inputs().size(); slot++) {
            CompiledPattern.Stack<K> input = pattern.inputs().get(slot).alternatives().get(0);
            long amount = CheckedLongMath.multiply(
                    input.amount(), patternExecutions, "bytes/input/" + pattern.id());
            nodes = CheckedLongMath.add(nodes, visit(input.key(), amount), "bytes/nodeCount");
        }
        // CraftingTreeProcessは全入力を処理した後にPattern実行回数をbytesへ加える。
        add(patternExecutions);
        return nodes;
    }

    private void add(double amount) {
        if (!Double.isFinite(amount) || amount < 0.0D) {
            throw new ArithmeticException("crafting bytes are not finite");
        }
        bytes += amount;
        if (!Double.isFinite(bytes) || bytes >= Long.MAX_VALUE) {
            throw new ArithmeticException("crafting bytes exceed long range");
        }
    }
}
