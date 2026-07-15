package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.stacks.AEKey;
import appeng.api.storage.AEKeyFilter;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class CraftableSetCache {
    private static final Map<CraftingService, Map<FilterKey, Set<AEKey>>> CACHE = new WeakHashMap<>();

    private CraftableSetCache() {
    }

    public static Set<AEKey> get(CraftingService craftingService, AEKeyFilter filter) {
        if (!ACOConfig.cacheCraftableSets()) {
            return null;
        }

        FilterKey key = FilterKey.of(filter);
        synchronized (CACHE) {
            Map<FilterKey, Set<AEKey>> serviceCache = CACHE.get(craftingService);
            Set<AEKey> cached = serviceCache != null ? serviceCache.get(key) : null;
            if (cached != null && ACOConfig.logPatternLookupCache()) {
                AE2CraftingOptimizer.LOGGER.debug("AE2 craftable-set cache hit for {}", key.description());
            }
            return cached;
        }
    }

    public static void put(CraftingService craftingService, AEKeyFilter filter, Set<AEKey> value) {
        if (!ACOConfig.cacheCraftableSets() || value == null) {
            return;
        }

        int maxEntries = ACOConfig.getCraftableSetCacheSize();
        FilterKey key = FilterKey.of(filter);
        synchronized (CACHE) {
            Map<FilterKey, Set<AEKey>> serviceCache = CACHE.computeIfAbsent(
                    craftingService,
                    ignored -> new LinkedHashMap<>(16, 0.75f, true) {
                        @Override
                        protected boolean removeEldestEntry(Map.Entry<FilterKey, Set<AEKey>> eldest) {
                            return size() > maxEntries;
                        }
                    });
            serviceCache.put(key, Set.copyOf(value));
        }
    }

    public static void clear(String reason) {
        synchronized (CACHE) {
            CACHE.clear();
        }
        if (ACOConfig.logPatternLookupCache()) {
            AE2CraftingOptimizer.LOGGER.debug("Cleared AE2 craftable-set cache: {}", reason);
        }
    }

    private record FilterKey(String className, int identity) {
        private static FilterKey of(AEKeyFilter filter) {
            if (filter == null) {
                return new FilterKey("<null>", 0);
            }
            return new FilterKey(filter.getClass().getName(), System.identityHashCode(filter));
        }

        private String description() {
            return className + "@" + Integer.toHexString(identity);
        }
    }
}
