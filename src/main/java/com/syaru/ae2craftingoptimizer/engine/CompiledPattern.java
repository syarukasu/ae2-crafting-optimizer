package com.syaru.ae2craftingoptimizer.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class CompiledPattern<K> {
    private final String id;
    private final List<InputSlot<K>> inputs;
    private final Map<K, Long> outputs;
    private final boolean externalPush;

    public CompiledPattern(String id, List<InputSlot<K>> inputs, Map<K, Long> outputs, boolean externalPush) {
        this.id = requireId(id);
        this.inputs = List.copyOf(Objects.requireNonNull(inputs, "inputs"));
        if (this.inputs.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("inputs must not contain null");
        }
        Objects.requireNonNull(outputs, "outputs");
        if (outputs.isEmpty()) {
            throw new IllegalArgumentException("pattern must have at least one output");
        }
        Map<K, Long> outputCopy = new LinkedHashMap<>();
        outputs.forEach((key, amount) -> {
            Objects.requireNonNull(key, "output key");
            if (amount == null || amount <= 0L) {
                throw new IllegalArgumentException("output amounts must be positive");
            }
            outputCopy.put(key, amount);
        });
        this.outputs = Collections.unmodifiableMap(outputCopy);
        this.externalPush = externalPush;
    }

    public String id() {
        return id;
    }

    public List<InputSlot<K>> inputs() {
        return inputs;
    }

    public Map<K, Long> outputs() {
        return outputs;
    }

    public boolean externalPush() {
        return externalPush;
    }

    public long outputAmount(K key) {
        return outputs.getOrDefault(key, 0L);
    }

    private static String requireId(String id) {
        String value = Objects.requireNonNull(id, "id").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        return value;
    }

    public static final class InputSlot<K> {
        private final List<Stack<K>> alternatives;

        public InputSlot(List<Stack<K>> alternatives) {
            Objects.requireNonNull(alternatives, "alternatives");
            if (alternatives.isEmpty()) {
                throw new IllegalArgumentException("input slot must have at least one alternative");
            }
            List<Stack<K>> copy = new ArrayList<>(alternatives.size());
            for (Stack<K> alternative : alternatives) {
                copy.add(Objects.requireNonNull(alternative, "alternative"));
            }
            this.alternatives = List.copyOf(copy);
        }

        public List<Stack<K>> alternatives() {
            return alternatives;
        }
    }

    public record Stack<K>(K key, long amount) {
        public Stack {
            Objects.requireNonNull(key, "key");
            if (amount <= 0L) {
                throw new IllegalArgumentException("stack amount must be positive");
            }
        }
    }
}
