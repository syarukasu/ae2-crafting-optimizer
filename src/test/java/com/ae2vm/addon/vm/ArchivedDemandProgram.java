// Archived pre-dev.20 arithmetic fixture; not an authenticated dev.20 binary.
// Source archive SHA-256: E30EA71F785B511EB8B4554902B9B0504EAC750C3CF81F07151B419EE4FDE8CD
package com.ae2vm.addon.vm;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import com.ae2vm.addon.compiler.PatternCompiler;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.CancellationException;

/** #218: shared recipe demands, not a multiplied snapshot of one inventory trial. */
final class ArchivedDemandProgram {
    private static final BigInteger ZERO = BigInteger.ZERO, ONE = BigInteger.ONE;
    final Map<IPatternDetails, Ratio> crafts = new LinkedHashMap<>();
    final Map<AEKey, Ratio> needs = new LinkedHashMap<>(), outputs = new LinkedHashMap<>();
    final Map<AEKey, Leaf> leaves = new LinkedHashMap<>();
    final Set<AEKey> intermediates = new LinkedHashSet<>(), dependencies = new HashSet<>();
    final IPatternDetails pattern;
    final int generation;
    final List<ArchivedDemandProgram> children = new ArrayList<>();
    private final Set<AEKey> inputs = new HashSet<>(), secondary = new HashSet<>();
    BigInteger nodes = ZERO, period = ONE;
    VmByteCost bytes = VmByteCost.ZERO;

    ArchivedDemandProgram(IPatternDetails pattern, int generation) { this.pattern = pattern; this.generation = generation; }
    record Leaf(BigInteger unit, boolean emit, boolean stockOnly) { }
    record Ratio(BigInteger numerator, BigInteger denominator) {
        Ratio {
            if (denominator.signum() <= 0) throw new IllegalArgumentException("nonpositive demand denominator");
            var gcd = numerator.gcd(denominator);
            numerator = numerator.divide(gcd); denominator = denominator.divide(gcd);
        }
        static Ratio of(BigInteger value) { return new Ratio(value, ONE); }
        Ratio add(Ratio other) {
            return new Ratio(numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)),
                    denominator.multiply(other.denominator));
        }
        Ratio multiply(Ratio other) { return new Ratio(numerator.multiply(other.numerator), denominator.multiply(other.denominator)); }
        BigInteger scale(BigInteger times) {
            var parts = numerator.multiply(times).divideAndRemainder(denominator);
            if (parts[1].signum() != 0) throw new IllegalStateException("non-integral aggregated demand");
            return parts[0];
        }
    }

    /** Only already-demanded immutable definitions are visible to this compiler. */
    static final class Compiler {
        private final Map<AEKey, List<IPatternDetails>> known;
        private final Map<IPatternDetails, ArchivedDemandProgram> programs = new IdentityHashMap<>();
        private final Map<IPatternDetails, Integer> unavailable = new IdentityHashMap<>();
        private final Set<IPatternDetails> visiting = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Runnable control;
        Compiler(Map<AEKey, List<IPatternDetails>> known) { this(known, () -> { }); }
        Compiler(Map<AEKey, List<IPatternDetails>> known, Runnable control) {
            this.known = known; this.control = control;
        }
        private void checkpoint() {
            ArchivedDemandProgram.checkpoint();
            control.run();
        }

        ArchivedDemandProgram get(IPatternDetails pattern) {
            checkpoint();
            var cached = programs.get(pattern);
            if (cached != null && (cached.generation == known.size()
                    || cached.leaves.values().stream().noneMatch(Leaf::stockOnly))) return cached;
            if (Objects.equals(unavailable.get(pattern), known.size()) || !visiting.add(pattern)) return null;
            ArchivedDemandProgram result;
            try { result = compile(pattern); }
            finally { visiting.remove(pattern); }
            if (result == null) unavailable.put(pattern, known.size());
            else programs.put(pattern, result);
            return result;
        }

        private ArchivedDemandProgram compile(IPatternDetails pattern) {
            var result = new ArchivedDemandProgram(pattern, known.size());
            result.crafts.put(pattern, Ratio.of(ONE));
            result.bytes = new VmByteCost(ONE, ONE);
            var primary = pattern.getPrimaryOutput().what();
            for (var output : pattern.getOutputs()) {
                merge(result.outputs, output.what(), Ratio.of(BigInteger.valueOf(output.amount())));
                result.dependencies.add(output.what());
                if (!output.what().equals(primary)) result.secondary.add(output.what());
            }
            for (var slot : pattern.getInputs()) {
                checkpoint();
                if (!(slot instanceof PatternCompiler.DetachedInput exact)) return null;
                var templates = slot.getPossibleInputs();
                if (templates.length != 1 || !exact.isCandidatePrepared(templates[0].what())) return null;
                var key = exact.exactKey();
                if (key == null || !key.equals(templates[0].what()) || !key.equals(exact.craftedKey())) return null;
                var unit = BigInteger.valueOf(templates[0].amount());
                var amount = unit.multiply(BigInteger.valueOf(slot.getMultiplier()));
                result.nodes = result.nodes.add(ONE);
                result.inputs.add(key); result.dependencies.add(key);
                result.bytes = result.bytes.add(new VmByteCost(amount.multiply(BigInteger.valueOf(8)),
                        BigInteger.valueOf(key.getType().getAmountPerByte())));
                var choices = known.get(key);
                if (exact.emittable() || choices == null || choices.isEmpty()) {
                    var leaf = new Leaf(unit, exact.emittable(), !exact.emittable() && choices == null);
                    var previous = result.leaves.putIfAbsent(key, leaf);
                    if (previous != null && !previous.equals(leaf)) return null;
                    merge(result.needs, key, Ratio.of(amount));
                    result.children.add(null);
                    continue;
                }
                if (choices.size() != 1) return null;
                var producer = choices.get(0);
                if (!producer.getPrimaryOutput().what().equals(key)) return null;
                var child = get(producer);
                if (child == null) return null;
                result.children.add(child);
                BigInteger output = ZERO;
                for (var stack : producer.getOutputs()) if (stack.what().equals(key)) output = output.add(BigInteger.valueOf(stack.amount()));
                if (output.signum() <= 0) return null;
                var ratio = new Ratio(amount, output);
                result.nodes = result.nodes.add(child.nodes);
                result.intermediates.add(key); result.intermediates.addAll(child.intermediates);
                result.dependencies.addAll(child.dependencies);
                result.inputs.addAll(child.inputs); result.secondary.addAll(child.secondary);
                for (var entry : child.leaves.entrySet()) {
                    var previous = result.leaves.putIfAbsent(entry.getKey(), entry.getValue());
                    if (previous != null && !previous.equals(entry.getValue())) return null;
                }
                combine(result.crafts, child.crafts, ratio);
                combine(result.needs, child.needs, ratio);
                combine(result.outputs, child.outputs, ratio);
                merge(result.outputs, key, Ratio.of(amount.negate()));
                result.bytes = result.bytes.add(new VmByteCost(child.bytes.numerator().multiply(ratio.numerator()),
                        child.bytes.denominator().multiply(ratio.denominator())));
                // Every conceptual sub-call must have whole batches, not just the sum of two fractional calls.
                result.period = lcm(result.period, ratio.denominator().multiply(child.period)
                        .divide(ratio.numerator().gcd(ratio.denominator().multiply(child.period))));
            }
            if (!Collections.disjoint(result.inputs, result.secondary) || result.inputs.contains(primary)) return null;
            result.outputs.entrySet().removeIf(entry -> entry.getValue().numerator().signum() == 0);
            if (result.outputs.values().stream().anyMatch(value -> value.numerator().signum() < 0)) return null;
            return result;
        }
    }

    private static <K> void combine(Map<K, Ratio> target, Map<K, Ratio> source, Ratio factor) {
        source.forEach((key, value) -> merge(target, key, value.multiply(factor)));
    }
    private static <K> void merge(Map<K, Ratio> target, K key, Ratio value) { target.merge(key, value, Ratio::add); }
    private static BigInteger lcm(BigInteger a, BigInteger b) { return a.divide(a.gcd(b)).multiply(b); }
    private static void checkpoint() {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("VM demand compilation cancelled");
    }
}
