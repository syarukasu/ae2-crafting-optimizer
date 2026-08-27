package com.syaru.ae2craftingoptimizer.optimization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GenerationScopedDecisionCacheTest {
    @Test
    void reusesOnlyTheSamePatternAndRecipeGeneration() {
        var cache = new GenerationScopedDecisionCache<String, String>(4);
        AtomicInteger compiles = new AtomicInteger();

        var first = cache.getOrCompute("root", 1L, 1L, () -> true, () -> compiled("v1", compiles));
        var hit = cache.getOrCompute("root", 1L, 1L, () -> true, () -> compiled("unexpected", compiles));
        var recipeChanged = cache.getOrCompute("root", 1L, 2L, () -> true, () -> compiled("v2", compiles));
        var patternChanged = cache.getOrCompute("root", 2L, 2L, () -> true, () -> compiled("v3", compiles));

        assertFalse(first.hit());
        assertTrue(hit.hit());
        assertEquals("v1", hit.value());
        assertFalse(recipeChanged.hit());
        assertEquals("v2", recipeChanged.value());
        assertFalse(patternChanged.hit());
        assertEquals("v3", patternChanged.value());
        assertEquals(3, compiles.get());
    }

    @Test
    void neverPublishesDynamicDecisionPrograms() {
        var cache = new GenerationScopedDecisionCache<String, String>(4);
        AtomicInteger compiles = new AtomicInteger();

        var first = cache.getOrCompute("root", 1L, 1L, () -> true, () -> dynamic("first", compiles));
        var second = cache.getOrCompute("root", 1L, 1L, () -> true, () -> dynamic("second", compiles));

        assertFalse(first.hit());
        assertFalse(second.hit());
        assertEquals("first", first.value());
        assertEquals("second", second.value());
        assertEquals(2, compiles.get());
    }

    @Test
    void evictsTheLeastRecentlyUsedProgramAtTheFixedBound() {
        var cache = new GenerationScopedDecisionCache<String, String>(2);
        AtomicInteger compiles = new AtomicInteger();

        cache.getOrCompute("a", 1L, 1L, () -> true, () -> compiled("a1", compiles));
        cache.getOrCompute("b", 1L, 1L, () -> true, () -> compiled("b1", compiles));
        cache.getOrCompute("a", 1L, 1L, () -> true, () -> compiled("unexpected", compiles));
        cache.getOrCompute("c", 1L, 1L, () -> true, () -> compiled("c1", compiles));
        var bRecompiled = cache.getOrCompute("b", 1L, 1L, () -> true, () -> compiled("b2", compiles));

        assertFalse(bRecompiled.hit());
        assertEquals("b2", bRecompiled.value());
        assertEquals(4, compiles.get());
    }

    @Test
    void neverPublishesAProgramWhenGenerationChangesDuringCompilation() {
        var cache = new GenerationScopedDecisionCache<String, String>(4);
        AtomicBoolean generationCurrent = new AtomicBoolean(true);
        AtomicInteger compiles = new AtomicInteger();

        var stale = cache.getOrCompute("root", 1L, 1L, generationCurrent::get, () -> {
            generationCurrent.set(false);
            return compiled("stale", compiles);
        });
        generationCurrent.set(true);
        var fresh = cache.getOrCompute("root", 1L, 1L, generationCurrent::get, () -> compiled("fresh", compiles));

        assertFalse(stale.hit());
        assertEquals("stale", stale.value());
        assertFalse(fresh.hit());
        assertEquals("fresh", fresh.value());
        assertEquals(2, compiles.get());
    }

    private static GenerationScopedDecisionCache.CompiledValue<String> compiled(
            String value,
            AtomicInteger compiles) {
        compiles.incrementAndGet();
        return new GenerationScopedDecisionCache.CompiledValue<>(value, true);
    }

    private static GenerationScopedDecisionCache.CompiledValue<String> dynamic(
            String value,
            AtomicInteger compiles) {
        compiles.incrementAndGet();
        return new GenerationScopedDecisionCache.CompiledValue<>(value, false);
    }
}
