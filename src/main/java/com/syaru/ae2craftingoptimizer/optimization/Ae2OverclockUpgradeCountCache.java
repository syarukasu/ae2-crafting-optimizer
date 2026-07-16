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
        Integer value = kind == Kind.OVERCLOCK ? entry.overclock : entry.parallel;
        if (value != null) {
            OptimizationMetrics.recordAe2OverclockUpgradeCount(true);
        }
        return value;
    }

    public static synchronized void put(Object host, Kind kind, int value) {
        long tick = ServerTickClock.currentTick();
        if (!ACOConfig.cacheAe2OverclockUpgradeCounts() || host == null || tick == 0L) {
            return;
        }
        Entry entry = CACHE.computeIfAbsent(host, ignored -> new Entry());
        if (entry.tick != tick) {
            entry.tick = tick;
            entry.overclock = null;
            entry.parallel = null;
        }
        if (kind == Kind.OVERCLOCK) {
            if (entry.overclock != null) {
                return;
            }
            entry.overclock = value;
        } else {
            if (entry.parallel != null) {
                return;
            }
            entry.parallel = value;
        }
        OptimizationMetrics.recordAe2OverclockUpgradeCount(false);
    }

    public static synchronized void clear() {
        CACHE.clear();
    }

    private static final class Entry {
        private long tick;
        private Integer overclock;
        private Integer parallel;
    }
}
