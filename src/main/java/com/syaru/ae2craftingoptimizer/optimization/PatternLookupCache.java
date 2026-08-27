package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.engine.RecipeGenerationTracker;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class PatternLookupCache {
    /** 小規模cacheでrehashを避ける初期bucket数。保持上限はConfig値を使う。 */
    private static final int INITIAL_CACHE_CAPACITY = 16;
    /** access-order LRU用の標準負荷率。Java LinkedHashMapの既定値と同じ。 */
    private static final float LRU_LOAD_FACTOR = 0.75F;
    private static final Map<CraftingService, ServiceCache> CACHE = new WeakHashMap<>();

    private PatternLookupCache() {
    }

    public static Collection<IPatternDetails> get(CraftingService craftingService, AEKey key) {
        if (!ACOConfig.cachePatternLookups()) {
            return null;
        }

        synchronized (CACHE) {
            ServiceCache serviceCache = CACHE.get(craftingService);
            // Providerまたはrecipe世代が変わったentryは、明示clearを待たずに利用を拒否する。
            if (serviceCache == null || !serviceCache.isCurrent()) {
                return null;
            }
            Collection<IPatternDetails> cached = serviceCache.values.get(key);
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
            ServiceCache serviceCache = CACHE.get(craftingService);
            // 世代不一致なら古いPattern参照を持つMapごと交換する。
            if (serviceCache == null || !serviceCache.isCurrent()) {
                serviceCache = new ServiceCache(maxEntries);
                CACHE.put(craftingService, serviceCache);
            }
            serviceCache.values.put(key, List.copyOf(value));
        }
    }

    public static void clear(String reason) {
        synchronized (CACHE) {
            CACHE.clear();
        }
        Ae2DecisionProgramCache.clear();
        if (ACOConfig.logPatternLookupCache()) {
            AE2CraftingOptimizer.LOGGER.debug("Cleared AE2 pattern lookup cache: {}", reason);
        }
    }

    private static final class ServiceCache {
        private final long patternGeneration = ProviderPatternGenerationTracker.generation();
        private final long recipeGeneration = RecipeGenerationTracker.generation();
        private final Map<AEKey, Collection<IPatternDetails>> values;

        private ServiceCache(int maximumEntries) {
            values = new LinkedHashMap<>(INITIAL_CACHE_CAPACITY, LRU_LOAD_FACTOR, true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<AEKey, Collection<IPatternDetails>> eldest) {
                    return size() > maximumEntries;
                }
            };
        }

        private boolean isCurrent() {
            return patternGeneration == ProviderPatternGenerationTracker.generation()
                    && recipeGeneration == RecipeGenerationTracker.generation();
        }
    }
}
