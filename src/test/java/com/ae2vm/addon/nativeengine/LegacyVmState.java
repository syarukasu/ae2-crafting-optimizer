package com.ae2vm.addon.nativeengine;

import appeng.api.networking.IGrid;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** State for historical detached-capture tests only. Never shipped. */
final class LegacyVmState {
    private static final Map<IGrid, Long> EPOCHS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final AtomicLong RECIPES = new AtomicLong();
    static long epoch(IGrid grid) { return EPOCHS.getOrDefault(grid, 0L); }
    static long recipeEpoch() { return RECIPES.get(); }
    static void recipesChanged() { RECIPES.incrementAndGet(); NativeVmPatternCatalog.recipesChanged(); }
    static void patternsChanged(IGrid grid) {
        EPOCHS.merge(grid, 1L, Long::sum);
        NativeVmPatternCatalog.invalidate(grid);
    }
    static final class Changed extends RuntimeException {
        Changed(String detail) { super(detail); }
    }
}
