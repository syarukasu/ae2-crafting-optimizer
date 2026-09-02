package com.syaru.ae2craftingoptimizer.engine.parallel;

import com.syaru.ae2craftingoptimizer.engine.CompiledPattern;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Server Threadで固定したPattern候補順だけをworkerへ公開するimmutable index。 */
public final class ParallelPatternIndex<K> {
    private final long generation;
    private final Map<K, List<CompiledPattern<K>>> candidatesByOutput;
    private final Set<K> emittableKeys;
    private final Set<K> incompleteOutputs;
    private final Comparator<? super K> keyOrder;

    public ParallelPatternIndex(
            long generation,
            Map<K, ? extends List<CompiledPattern<K>>> candidatesByOutput,
            Set<K> emittableKeys,
            Set<K> incompleteOutputs,
            Comparator<? super K> keyOrder) {
        if (generation < 0L) {
            throw new IllegalArgumentException("pattern generation must not be negative");
        }
        this.generation = generation;
        this.keyOrder = Objects.requireNonNull(keyOrder, "keyOrder");
        this.candidatesByOutput = immutableCandidates(candidatesByOutput);
        this.emittableKeys = immutableKeys(emittableKeys, "emittableKeys");
        this.incompleteOutputs = immutableKeys(incompleteOutputs, "incompleteOutputs");
    }

    public static <K> ParallelPatternIndex<K> fromPatterns(
            long generation,
            Collection<CompiledPattern<K>> patterns,
            Set<K> emittableKeys,
            Comparator<? super K> keyOrder) {
        Objects.requireNonNull(patterns, "patterns");
        Map<K, List<CompiledPattern<K>>> candidates = new LinkedHashMap<>();
        for (CompiledPattern<K> pattern : patterns) {
            Objects.requireNonNull(pattern, "pattern");
            for (K output : pattern.outputs().keySet()) {
                candidates.computeIfAbsent(output, ignored -> new ArrayList<>()).add(pattern);
            }
        }
        return new ParallelPatternIndex<>(
                generation,
                candidates,
                emittableKeys,
                Set.of(),
                keyOrder);
    }

    public long generation() {
        return generation;
    }

    public List<CompiledPattern<K>> candidatesFor(K output) {
        return candidatesByOutput.getOrDefault(output, List.of());
    }

    public boolean isEmittable(K key) {
        return emittableKeys.contains(key);
    }

    public boolean isIncomplete(K output) {
        return incompleteOutputs.contains(output);
    }

    public Comparator<? super K> keyOrder() {
        return keyOrder;
    }

    public int indexedOutputCount() {
        return candidatesByOutput.size();
    }

    private static <K> Map<K, List<CompiledPattern<K>>> immutableCandidates(
            Map<K, ? extends List<CompiledPattern<K>>> source) {
        Objects.requireNonNull(source, "candidatesByOutput");
        Map<K, List<CompiledPattern<K>>> copy = new LinkedHashMap<>();
        for (Map.Entry<K, ? extends List<CompiledPattern<K>>> entry : source.entrySet()) {
            K key = Objects.requireNonNull(entry.getKey(), "candidate output");
            List<CompiledPattern<K>> candidates = List.copyOf(
                    Objects.requireNonNull(entry.getValue(), "candidates"));
            if (candidates.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("candidates must not contain null");
            }
            copy.put(key, candidates);
        }
        return Collections.unmodifiableMap(copy);
    }

    private static <K> Set<K> immutableKeys(Set<K> source, String name) {
        Objects.requireNonNull(source, name);
        LinkedHashSet<K> copy = new LinkedHashSet<>();
        for (K key : source) {
            copy.add(Objects.requireNonNull(key, name + " key"));
        }
        return Collections.unmodifiableSet(copy);
    }
}
