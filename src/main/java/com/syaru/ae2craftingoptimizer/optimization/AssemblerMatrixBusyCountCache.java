package com.syaru.ae2craftingoptimizer.optimization;

import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.util.Map;
import java.util.WeakHashMap;

public final class AssemblerMatrixBusyCountCache {
    private static final Map<Object, Entry> CACHE = new WeakHashMap<>();

    private AssemblerMatrixBusyCountCache() {
    }

    public static synchronized Integer get(Object cluster) {
        long tick = ServerTickClock.currentTick();
        if (!ACOConfig.cacheAssemblerMatrixBusyCount() || cluster == null || tick == 0L) {
            return null;
        }
        Entry entry = CACHE.get(cluster);
        if (entry == null || entry.tick != tick) {
            return null;
        }
        OptimizationMetrics.recordAssemblerMatrixBusyCountHit();
        return entry.value;
    }

    public static synchronized void put(Object cluster, int value) {
        long tick = ServerTickClock.currentTick();
        if (!ACOConfig.cacheAssemblerMatrixBusyCount() || cluster == null || tick == 0L) {
            return;
        }
        CACHE.put(cluster, new Entry(tick, value));
    }

    public static synchronized void invalidate(Object cluster) {
        CACHE.remove(cluster);
    }

    public static synchronized void clear() {
        CACHE.clear();
    }

    private record Entry(long tick, int value) {
    }
}
