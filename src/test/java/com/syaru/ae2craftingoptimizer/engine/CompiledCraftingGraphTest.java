package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CompiledCraftingGraphTest {
    @Test
    void identifiesStronglyConnectedCycles() {
        var a = pattern("a", "b", "a");
        var b = pattern("b", "a", "b");
        var c = pattern("c", "raw", "c");
        var graph = CompiledCraftingGraph.compile(7L, List.of(a, b, c));

        assertTrue(graph.isCyclic("a"));
        assertTrue(graph.sharesCycle("a", "b"));
        assertFalse(graph.isCyclic("c"));
    }

    @Test
    void reusesOnlyTheSameGeneration() {
        GenerationAwareGraphCache<String> cache = new GenerationAwareGraphCache<>();
        AtomicInteger builds = new AtomicInteger();
        var first = cache.getOrCompile(1L, generation -> {
            builds.incrementAndGet();
            return CompiledCraftingGraph.compile(generation, List.of(pattern("p", "raw", "out")));
        });
        var second = cache.getOrCompile(1L, generation -> {
            builds.incrementAndGet();
            return CompiledCraftingGraph.compile(generation, List.of());
        });

        assertSame(first, second);
        assertTrue(builds.get() == 1);
    }

    @Test
    void compilesDeepDependencyChainsWithoutUsingTheJavaCallStack() {
        int depth = 20_000;
        List<CompiledPattern<String>> patterns = new ArrayList<>(depth);
        for (int index = 1; index <= depth; index++) {
            patterns.add(pattern("p" + index, "k" + (index - 1), "k" + index));
        }

        CompiledCraftingGraph<String> graph = CompiledCraftingGraph.compile(1L, patterns);

        assertFalse(graph.isCyclic("k" + depth));
        assertTrue(graph.stronglyConnectedComponentCount() == depth + 1);
    }

    private static CompiledPattern<String> pattern(String id, String input, String output) {
        return new CompiledPattern<>(
                id,
                List.of(new CompiledPattern.InputSlot<>(List.of(new CompiledPattern.Stack<>(input, 1L)))),
                Map.of(output, 1L),
                false);
    }
}
