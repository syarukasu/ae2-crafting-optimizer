package com.syaru.ae2craftingoptimizer.engine;

import java.util.concurrent.atomic.AtomicLong;

public final class RecipeGenerationTracker {
    private static final AtomicLong GENERATION = new AtomicLong(1L);

    private RecipeGenerationTracker() {
    }

    public static long generation() {
        return GENERATION.get();
    }

    public static void invalidate() {
        GENERATION.updateAndGet(value -> value == Long.MAX_VALUE ? 1L : value + 1L);
    }
}
