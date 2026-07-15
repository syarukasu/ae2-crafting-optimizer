package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class PatternLookupCache {
    private static final Map<CraftingService, Map<AEKey, Collection<IPatternDetails>>> CACHE = new WeakHashMap<>();

    private PatternLookupCache() {
    }

    public static Collection<IPatternDetails> get(CraftingService craftingService, AEKey key) {
        if (!ACOConfig.cachePatternLookups()) {
            return null;
        }

        synchronized (CACHE) {
            Map<AEKey, Collection<IPatternDetails>> serviceCache = CACHE.get(craftingService);
            Collection<IPatternDetails> cached = serviceCache != null ? serviceCache.get(key) : null;
            if (cached != null && ACOConfig.logPatternLookupCache()) {
                AE2CraftingOptimizer.LOGGER.debug("AE2 pattern lookup cache hit for {}", key.getId());
            }
            return cached;
        }
    }

    public static void put(CraftingService craftingService, AEKey key, Collection<IPatternDetails> value) {
        if (!ACOConfig.cachePatternLookups() || value == null) {
            return;
        }

        int maxEntries = ACOConfig.getPatternLookupCacheSize();
        synchronized (CACHE) {
            Map<AEKey, Collection<IPatternDetails>> serviceCache = CACHE.computeIfAbsent(
                    craftingService,
                    ignored -> new LinkedHashMap<>(16, 0.75f, true) {
                        @Override
                        protected boolean removeEldestEntry(Map.Entry<AEKey, Collection<IPatternDetails>> eldest) {
                            return size() > maxEntries;
                        }
                    });
            serviceCache.put(key, List.copyOf(value));
        }
    }

    public static void clear(String reason) {
        synchronized (CACHE) {
            CACHE.clear();
        }
        if (ACOConfig.logPatternLookupCache()) {
            AE2CraftingOptimizer.LOGGER.debug("Cleared AE2 pattern lookup cache: {}", reason);
        }
    }
}
