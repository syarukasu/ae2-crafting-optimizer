package com.syaru.ae2craftingoptimizer.engine;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bulk, input-ordered simulation for proven acyclic, single-candidate shared-input/co-product programs. */
final class OrderedByproductPlanner {
    private static final int MAX_REQUESTS = 1_048_576;

    private OrderedByproductPlanner() {
    }

    static <K> BigCraftingPlan<K> plan(CompiledRootProgram<K> program, BigInteger requested,
            BigInteger[] initial, PlanningGuard guard, int bits) {
        BigCountMath.requireMaximumBits(requested, "byproduct/request", bits);
        BigInteger[] available = initial.clone();
        BigInteger[] reserved = new BigInteger[initial.length];
        Arrays.fill(reserved, BigInteger.ZERO);
        Map<String, BigInteger> executions = new LinkedHashMap<>();
        Map<K, BigInteger> emitted = new LinkedHashMap<>();
        Map<K, BigInteger> missing = new LinkedHashMap<>();
        var charges = new ArrayList<CraftingPlanTrace.Charge<K>>();
        var pending = new ArrayDeque<Frame>();
        pending.push(new Frame(program.indexOf(program.root()), requested, 1));
        int expanded = 0;
        while (!pending.isEmpty()) {
            guard.checkpoint(expanded);
            Frame frame = pending.peek();
            K key = program.keyAt(frame.node);
            CompiledPattern<K> pattern = program.patternAt(frame.node);
            if (frame.deficit == null) {
                if (++expanded > MAX_REQUESTS) {
                    throw new IllegalStateException("byproduct plan exceeds bounded request expansion");
                }
                charges.add(new CraftingPlanTrace.Charge<>(key, frame.amount, frame.quantum));
                BigInteger fromStock = available[frame.node].min(frame.amount)
                        .divide(BigInteger.valueOf(frame.quantum)).multiply(BigInteger.valueOf(frame.quantum));
                take(frame.node, fromStock, initial, available, reserved);
                frame.deficit = frame.amount.subtract(fromStock);
                if (frame.deficit.signum() == 0) {
                    pending.pop();
                    continue;
                }
                if (program.isEmittableAt(frame.node)) {
                    BigCountMath.merge(emitted, key, frame.deficit, "byproduct/emitted", bits);
                    pending.pop();
                    continue;
                }
                if (pattern == null) {
                    BigCountMath.merge(missing, key, frame.deficit, "byproduct/missing", bits);
                    pending.pop();
                    continue;
                }
                frame.times = BigCountMath.ceilDiv(frame.deficit,
                        BigInteger.valueOf(pattern.outputAmount(key)), "byproduct/times");
            }
            if (frame.input < pattern.inputs().size()) {
                var slot = pattern.inputs().get(frame.input++);
                var input = slot.alternatives().get(0);
                BigInteger amount = BigCountMath.multiply(BigInteger.valueOf(input.amount()), frame.times,
                        "byproduct/input", bits);
                pending.push(new Frame(program.indexOf(input.key()), amount, slot.templateAmount()));
                continue;
            }
            // Inputs are visited first. Missing inputs are hypothetical only in this missing-material simulation.
            BigCountMath.merge(executions, pattern.id(), frame.times, "byproduct/executions", bits);
            for (var output : pattern.outputs().entrySet()) {
                guard.checkpoint(expanded);
                BigInteger produced = BigCountMath.multiply(BigInteger.valueOf(output.getValue()), frame.times,
                        "byproduct/output", bits);
                int index = program.indexOf(output.getKey());
                if (index >= 0) {
                    available[index] = BigCountMath.add(available[index], produced, "byproduct/balance", bits);
                }
            }
            charges.add(new CraftingPlanTrace.Charge<>(null, frame.times, 1));
            if (available[frame.node].compareTo(frame.deficit) < 0) {
                throw new IllegalStateException("byproduct producer did not supply its requested output");
            }
            take(frame.node, frame.deficit, initial, available, reserved);
            pending.pop();
        }
        charges.add(new CraftingPlanTrace.Charge<>(null, BigInteger.valueOf(expanded).multiply(BigInteger.valueOf(8)), 1));
        program.requiresWideOutputCounts(executions, bits, guard);
        Map<K, BigInteger> used = new LinkedHashMap<>();
        for (int node = 0; node < reserved.length; node++) {
            guard.checkpoint(expanded);
            if (reserved[node].signum() > 0) {
                used.put(program.keyAt(node), reserved[node]);
            }
        }
        return new BigCraftingPlan<>(program.root(), requested, executions, used, emitted, missing,
                expanded, new CraftingPlanTrace<>(charges));
    }

    private static void take(int node, BigInteger amount, BigInteger[] initial,
            BigInteger[] available, BigInteger[] reserved) {
        available[node] = available[node].subtract(amount);
        // AE2 reserves the peak initial-minus-current deficit, not every simulated extraction.
        reserved[node] = reserved[node].max(initial[node].subtract(available[node]));
    }

    static <K> LongCraftingPlan<K> narrow(BigCraftingPlan<K> plan, CompiledRootProgram<K> program,
            PlanningGuard guard) {
        if (program.requiresWideOutputCounts(plan.patternExecutions(), BigCountMath.HARD_MAXIMUM_BITS, guard)) {
            throw new CountOverflowException("byproduct outputs", 0, 0, "ordered plan");
        }
        try {
            for (var charge : plan.trace().charges()) {
                charge.amount().longValueExact();
            }
            return new LongCraftingPlan<>(plan.requestedKey(), plan.requestedAmount().longValueExact(),
                    narrow(plan.patternExecutions()), narrow(plan.usedInventory()), narrow(plan.emitted()),
                    narrow(plan.missing()), plan.trace());
        } catch (ArithmeticException overflow) {
            throw new CountOverflowException("byproduct narrowing", 0, 0, "ordered plan");
        }
    }

    private static <K> Map<K, Long> narrow(Map<K, BigInteger> source) {
        Map<K, Long> result = new LinkedHashMap<>();
        source.forEach((key, amount) -> result.put(key, amount.longValueExact()));
        return result;
    }

    private static final class Frame {
        private final int node;
        private final BigInteger amount;
        private final long quantum;
        private BigInteger deficit;
        private BigInteger times;
        private int input;

        private Frame(int node, BigInteger amount, long quantum) {
            this.node = node;
            this.amount = amount;
            this.quantum = quantum;
        }
    }
}
