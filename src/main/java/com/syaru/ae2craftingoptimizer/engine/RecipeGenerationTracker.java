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
        GENERATION.updateAndGet(value -> {
            // Issue #167: recipe世代をwrapすると古いGraphとのABA一致を作るため明示失敗する。
            if (value == Long.MAX_VALUE) {
                throw new IllegalStateException("recipe generation exhausted");
            }
            return value + 1L;
        });
    }
}
