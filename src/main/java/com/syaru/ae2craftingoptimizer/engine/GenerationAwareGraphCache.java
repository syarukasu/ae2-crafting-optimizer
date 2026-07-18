package com.syaru.ae2craftingoptimizer.engine;

import java.util.Objects;
import java.util.function.LongFunction;

public final class GenerationAwareGraphCache<K> {
    private volatile CompiledCraftingGraph<K> cached;

    public CompiledCraftingGraph<K> getOrCompile(
            long generation, LongFunction<CompiledCraftingGraph<K>> compiler) {
        CompiledCraftingGraph<K> current = cached;
        if (current != null && current.generation() == generation) {
            return current;
        }
        synchronized (this) {
            current = cached;
            if (current == null || current.generation() != generation) {
                current = Objects.requireNonNull(compiler.apply(generation), "compiler result");
                if (current.generation() != generation) {
                    throw new IllegalStateException("compiled graph generation mismatch");
                }
                cached = current;
            }
            return current;
        }
    }

    public synchronized void invalidate() {
        cached = null;
    }
}
