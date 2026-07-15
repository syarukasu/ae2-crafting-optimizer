package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.hooks.ticking.TickHandler;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import it.unimi.dsi.fastutil.objects.AbstractObject2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BusFuzzySearchCache {
    private static final Map<CacheKey, CacheEntry> CACHE = new LinkedHashMap<>(128, 0.75f, true);

    private BusFuzzySearchCache() {
    }

    public static synchronized Collection<Object2LongMap.Entry<AEKey>> find(
            KeyCounter counter,
            AEKey key,
            FuzzyMode mode) {
        if (!ACOConfig.deepBusSearchRewrite()) {
            return counter.findFuzzy(key, mode);
        }

        long currentTick = TickHandler.instance().getCurrentTick();
        var cacheKey = new CacheKey(counter, key, mode);
        var cached = CACHE.get(cacheKey);
        if (cached != null && currentTick - cached.createdTick() < ACOConfig.getDeepBusFuzzyCacheTicks()) {
            return cached.entries();
        }

        var entries = copy(counter.findFuzzy(key, mode));
        CACHE.put(cacheKey, new CacheEntry(currentTick, entries));
        trim();
        return entries;
    }

    public static synchronized void clear() {
        CACHE.clear();
    }

    private static List<Object2LongMap.Entry<AEKey>> copy(
            Collection<Object2LongMap.Entry<AEKey>> source) {
        var result = new ArrayList<Object2LongMap.Entry<AEKey>>(source.size());
        for (var entry : source) {
            result.add(new AbstractObject2LongMap.BasicEntry<>(entry.getKey(), entry.getLongValue()));
        }
        return List.copyOf(result);
    }

    private static void trim() {
        int maximumSize = ACOConfig.getDeepBusFuzzyCacheSize();
        var iterator = CACHE.entrySet().iterator();
        while (CACHE.size() > maximumSize && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private record CacheKey(KeyCounter counter, AEKey key, FuzzyMode mode) {
    }

    private record CacheEntry(long createdTick, List<Object2LongMap.Entry<AEKey>> entries) {
    }
}
