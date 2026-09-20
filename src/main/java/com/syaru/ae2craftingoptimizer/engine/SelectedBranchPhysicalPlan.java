package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.stacks.AEKey;
import appeng.api.crafting.IPatternDetails;
import com.syaru.ae2craftingoptimizer.api.batch.ExactPatternFormula;
import com.syaru.ae2craftingoptimizer.api.vector.ExactCraftingInputSlot;
import com.syaru.ae2craftingoptimizer.api.vector.ExactCraftingStep;
import com.syaru.ae2craftingoptimizer.api.vector.ExactStack;
import com.syaru.ae2craftingoptimizer.api.vector.PreparedVectorBatch;
import com.syaru.ae2craftingoptimizer.api.vector.VectorResourceMode;
import com.syaru.ae2craftingoptimizer.engine.vector.VectorPlanFingerprint;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.world.level.Level;

/** Issue #190: preserves selected fixed-input branches in the existing receipt execution contract. */
public final class SelectedBranchPhysicalPlan {
    public static final String PREFIX = "aco-branch-v1:";

    private SelectedBranchPhysicalPlan() {}

    public static PreparedVectorBatch prepare(BigCraftingPlan<AEKey> exact,
            Collection<CompiledPattern<AEKey>> candidates, long patternGeneration,
            long recipeGeneration, int maximumBits) {
        if (!exact.craftable() || !exact.emitted().isEmpty() || exact.requestedAmount().signum() <= 0) {
            throw new IllegalArgumentException("selected physical branch is missing or relies on emitted stock");
        }
        Map<String, CompiledPattern<AEKey>> selected = new TreeMap<>();
        for (var pattern : candidates) {
            if (exact.patternExecutions().getOrDefault(pattern.id(), BigInteger.ZERO).signum() > 0) {
                selected.putIfAbsent(pattern.id(), pattern);
            }
        }
        if (selected.isEmpty() || !selected.keySet().equals(positive(exact.patternExecutions()).keySet())
                || selected.size() > 65_536) {
            throw new IllegalArgumentException("selected physical pattern bindings are incomplete");
        }
        Map<AEKey, Set<String>> producers = new HashMap<>();
        Map<String, List<ExactCraftingInputSlot>> slots = new HashMap<>();
        Map<String, Set<String>> successors = new HashMap<>();
        Map<String, Integer> indegree = new HashMap<>();
        for (var pattern : selected.values()) {
            if (pattern.externalPush() || pattern.inputs().isEmpty() || pattern.inputs().size() > 9) {
                throw new IllegalArgumentException("selected branch requires a receipt-backed crafting-table pattern");
            }
            List<ExactCraftingInputSlot> inputs = new ArrayList<>();
            for (var slot : pattern.inputs()) {
                if (slot.alternatives().size() != 1) {
                    throw new IllegalArgumentException("selected branch has no fixed concrete input binding");
                }
                var stack = slot.alternatives().get(0);
                inputs.add(new ExactCraftingInputSlot(stack.key(), stack.amount()));
            }
            slots.put(pattern.id(), List.copyOf(inputs));
            successors.put(pattern.id(), new TreeSet<>());
            indegree.put(pattern.id(), 0);
            pattern.outputs().keySet().forEach(k ->
                    producers.computeIfAbsent(k, unused -> new TreeSet<>()).add(pattern.id()));
        }
        // Interleaved/cyclic execution needs a different step contract; do not batch it as a DAG.
        for (var pattern : selected.values()) {
            for (var input : slots.get(pattern.id())) {
                for (String producer : producers.getOrDefault(input.key(), Set.of())) {
                    if (successors.get(producer).add(pattern.id())) {
                        indegree.merge(pattern.id(), 1, Integer::sum);
                    }
                }
            }
        }
        Queue<String> ready = new PriorityQueue<>();
        indegree.forEach((id, n) -> { if (n == 0) ready.add(id); });
        List<String> ordered = new ArrayList<>();
        while (!ready.isEmpty()) {
            String id = ready.remove();
            ordered.add(id);
            for (String next : successors.get(id)) {
                if (indegree.merge(next, -1, Integer::sum) == 0) ready.add(next);
            }
        }
        if (ordered.size() != selected.size()) {
            throw new IllegalArgumentException("selected physical branch requires interleaved cyclic execution");
        }
        Map<String, Integer> depth = new HashMap<>();
        for (int i = ordered.size() - 1; i >= 0; i--) {
            String id = ordered.get(i);
            int value = 1;
            for (String next : successors.get(id)) value = Math.max(value, depth.get(next) + 1);
            depth.put(id, value);
        }
        ordered.sort(Comparator.<String>comparingInt(depth::get).reversed().thenComparing(id -> id));
        Map<AEKey, BigInteger> stock = new LinkedHashMap<>(positive(exact.usedInventory()));
        stock.values().forEach(n -> checked(n, maximumBits));
        checked(exact.requestedAmount(), maximumBits);
        List<ExactCraftingStep> steps = new ArrayList<>();
        BigInteger executions = BigInteger.ZERO;
        for (String id : ordered) {
            BigInteger count = checked(exact.patternExecutions().get(id), maximumBits);
            executions = checked(executions.add(count), maximumBits);
            for (var input : slots.get(id)) {
                BigInteger debit = checked(count.multiply(BigInteger.valueOf(input.amountPerExecution())), maximumBits);
                BigInteger left = stock.getOrDefault(input.key(), BigInteger.ZERO).subtract(debit);
                if (left.signum() < 0) throw new IllegalArgumentException("selected branch boundary input is insufficient");
                stock.put(input.key(), left);
            }
            for (var output : selected.get(id).outputs().entrySet()) {
                BigInteger credit = checked(count.multiply(BigInteger.valueOf(output.getValue())), maximumBits);
                stock.put(output.getKey(), checked(stock.getOrDefault(output.getKey(), BigInteger.ZERO)
                        .add(credit), maximumBits));
            }
            steps.add(new ExactCraftingStep(id, depth.get(id), count, slots.get(id)));
        }
        BigInteger remaining = stock.getOrDefault(exact.requestedKey(), BigInteger.ZERO)
                .subtract(exact.requestedAmount());
        if (remaining.signum() < 0) throw new IllegalArgumentException("selected branch cannot satisfy its requested output");
        stock.put(exact.requestedKey(), remaining);
        var inputs = stacks(positive(exact.usedInventory()));
        var outputs = List.of(new ExactStack(exact.requestedKey(), exact.requestedAmount()));
        var surplus = stacks(positive(stock));
        String fingerprint = fingerprint(steps, exact.requestedAmount(), inputs, outputs, surplus);
        var prepared = new PreparedVectorBatch(UUID.randomUUID(), UUID.randomUUID(), VectorResourceMode.NETWORK_STORAGE,
                exact.requestedKey(), exact.requestedAmount(), executions, depth.get(ordered.get(0)), inputs,
                outputs, surplus, ordered, steps, fingerprint, patternGeneration, recipeGeneration);
        validateAccounting(prepared, exact);
        return prepared;
    }

    public static PreparedVectorBatch forJob(PreparedVectorBatch plan, UUID jobId) {
        validateFingerprint(plan);
        return new PreparedVectorBatch(UUID.randomUUID(), jobId, plan.resourceMode(), plan.requestedOutput(),
                plan.requestedAmount(), plan.logicalExecutions(), plan.logicalStageCount(), plan.totalInputs(),
                plan.finalOutputs(), plan.remainingOutputs(), plan.requiredPatternIds(), plan.craftingSteps(),
                plan.programFingerprint(), plan.patternGeneration(), plan.recipeGeneration());
    }

    public static void validateAccounting(PreparedVectorBatch prepared, BigCraftingPlan<AEKey> exact) {
        Map<String, BigInteger> counts = new HashMap<>();
        prepared.craftingSteps().forEach(step -> counts.put(step.patternId(), step.executions()));
        if (!exact.craftable() || !exact.emitted().isEmpty()
                || !prepared.requestedOutput().equals(exact.requestedKey())
                || !prepared.requestedAmount().equals(exact.requestedAmount())
                || !counts.equals(positive(exact.patternExecutions()))
                || !inputCounts(prepared).equals(positive(exact.usedInventory()))) {
            throw new IllegalArgumentException("selected branch does not match exact plan accounting");
        }
        validateFingerprint(prepared);
    }

    public static Map<AEKey, BigInteger> inputCounts(PreparedVectorBatch plan) {
        Map<AEKey, BigInteger> inputs = new LinkedHashMap<>();
        plan.totalInputs().forEach(s -> inputs.put(s.key(), s.amount()));
        return Map.copyOf(inputs);
    }

    /** Server-thread preflight of live recipe quantities, including container returns. No stock mutation. */
    public static void validateBindings(PreparedVectorBatch plan,
            Function<String, IPatternDetails> patterns, Level level, int maximumBits) {
        validateFingerprint(plan);
        Map<AEKey, BigInteger> stock = new LinkedHashMap<>(inputCounts(plan));
        for (var step : plan.craftingSteps()) {
            var pattern = patterns.apply(step.patternId());
            if (pattern == null) throw new IllegalArgumentException("selected pattern is no longer available: " + step.patternId());
            var formula = ExactPatternFormula.tryCreate(pattern, level, step.selectedInputs()).orElseThrow(
                    () -> new IllegalArgumentException("selected pattern has no exact physical formula: " + step.patternId()));
            for (var input : formula.exactInputTotals(step.executions()).entrySet()) {
                stock.put(input.getKey(), checked(stock.getOrDefault(input.getKey(), BigInteger.ZERO)
                        .subtract(input.getValue()), maximumBits));
            }
            for (var output : formula.exactExpectedOutputTotals(step.executions()).entrySet()) {
                stock.put(output.getKey(), checked(stock.getOrDefault(output.getKey(), BigInteger.ZERO)
                        .add(output.getValue()), maximumBits));
            }
        }
        Map<AEKey, BigInteger> expected = new HashMap<>();
        plan.finalOutputs().forEach(s -> expected.merge(s.key(), s.amount(), BigInteger::add));
        plan.remainingOutputs().forEach(s -> expected.merge(s.key(), s.amount(), BigInteger::add));
        if (!positive(stock).equals(expected)) {
            throw new IllegalArgumentException("selected physical branch recipe accounting has changed");
        }
    }

    public static void validateFingerprint(PreparedVectorBatch plan) {
        BigInteger executions = plan.craftingSteps().stream().map(ExactCraftingStep::executions)
                .reduce(BigInteger.ZERO, BigInteger::add);
        if (plan.resourceMode() != VectorResourceMode.NETWORK_STORAGE || plan.craftingSteps().isEmpty()
                || !executions.equals(plan.logicalExecutions())) {
            throw new IllegalArgumentException("selected branch execution totals are inconsistent");
        }
        if (!plan.programFingerprint().equals(fingerprint(plan.craftingSteps(), plan.requestedAmount(),
                plan.totalInputs(), plan.finalOutputs(), plan.remainingOutputs()))) {
            throw new IllegalArgumentException("selected branch fingerprint changed");
        }
    }

    private static String fingerprint(List<ExactCraftingStep> steps, BigInteger requested,
            List<ExactStack> inputs, List<ExactStack> outputs, List<ExactStack> surplus) {
        StringBuilder identity = new StringBuilder(PREFIX);
        for (var step : steps) {
            var selected = step.selectedInputs().stream().map(s ->
                    new ExactStack(s.key(), BigInteger.valueOf(s.amountPerExecution()))).toList();
            identity.append(VectorPlanFingerprint.create(step.patternId() + ":" + step.depth(),
                    step.executions(), selected, List.of()));
        }
        List<ExactStack> allOutputs = new ArrayList<>(outputs);
        allOutputs.addAll(surplus);
        return PREFIX + VectorPlanFingerprint.create(identity.toString(), requested, inputs, allOutputs);
    }

    private static BigInteger checked(BigInteger amount, int bits) {
        if (amount.signum() < 0 || amount.bitLength() > bits) {
            throw new IllegalArgumentException("selected branch count exceeds exact arithmetic bounds");
        }
        return amount;
    }

    private static <K> Map<K, BigInteger> positive(Map<K, BigInteger> source) {
        Map<K, BigInteger> result = new LinkedHashMap<>();
        source.forEach((k, n) -> { if (n.signum() > 0) result.put(k, n); });
        return result;
    }

    private static List<ExactStack> stacks(Map<AEKey, BigInteger> source) {
        return source.entrySet().stream().map(e -> new ExactStack(e.getKey(), e.getValue())).toList();
    }
}
