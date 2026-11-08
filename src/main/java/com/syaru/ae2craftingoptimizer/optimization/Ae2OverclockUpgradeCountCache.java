package com.syaru.ae2craftingoptimizer.optimization;

import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.util.Map;
import java.util.WeakHashMap;

public final class Ae2OverclockUpgradeCountCache {
    public enum Kind {
        OVERCLOCK,
        PARALLEL
    }

    private static final Map<Object, Entry> CACHE = new WeakHashMap<>();

    private Ae2OverclockUpgradeCountCache() {
    }

    public static synchronized Integer get(Object host, Kind kind) {
        long tick = ServerTickClock.currentTick();
        if (!ACOConfig.cacheAe2OverclockUpgradeCounts() || host == null || tick == 0L) {
            return null;
        }
        Entry entry = CACHE.get(host);
        if (entry == null || entry.tick != tick) {
            return null;
        }
        if (kind == Kind.OVERCLOCK) {
            if (!entry.hasOverclock) {
                return null;
            }
            OptimizationMetrics.recordAe2OverclockUpgradeCount(true);
            return entry.overclock;
        }
        if (!entry.hasParallel) {
            return null;
        }
        OptimizationMetrics.recordAe2OverclockUpgradeCount(true);
        return entry.parallel;
    }

    public static synchronized void put(Object host, Kind kind, int value) {
        long tick = ServerTickClock.currentTick();
        if (!ACOConfig.cacheAe2OverclockUpgradeCounts() || host == null || tick == 0L) {
            return;
        }
        Entry entry = CACHE.computeIfAbsent(host, ignored -> new Entry());
        if (entry.tick != tick) {
            entry.tick = tick;
            entry.hasOverclock = false;
            entry.hasParallel = false;
        }
        if (kind == Kind.OVERCLOCK) {
            if (entry.hasOverclock) {
                return;
            }
            entry.overclock = value;
            entry.hasOverclock = true;
        } else {
            if (entry.hasParallel) {
                return;
            }
            entry.parallel = value;
            entry.hasParallel = true;
        }
        OptimizationMetrics.recordAe2OverclockUpgradeCount(false);
    }

    public static synchronized void clear() {
        CACHE.clear();
    }

    private static final class Entry {
        private long tick;
        private int overclock;
        private int parallel;
        private boolean hasOverclock;
        private boolean hasParallel;
    }
}
