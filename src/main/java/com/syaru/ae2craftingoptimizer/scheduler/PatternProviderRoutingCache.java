package com.syaru.ae2craftingoptimizer.scheduler;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.optimization.ProviderPatternGenerationTracker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Reuses structural provider routes while still checking each provider's live busy/backpressure state. */
public final class PatternProviderRoutingCache {
    private static final Map<CraftingService, ServiceRoutes> ROUTES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private PatternProviderRoutingCache() {
    }

    public static List<ICraftingProvider> candidates(
            CraftingService service,
            IPatternDetails pattern) {
        long generation = ProviderPatternGenerationTracker.generation();
        synchronized (ROUTES) {
            ServiceRoutes routes = ROUTES.computeIfAbsent(service, ignored -> new ServiceRoutes());
            if (routes.generation != generation) {
                routes.generation = generation;
                routes.byPattern.clear();
            }
            return routes.byPattern.computeIfAbsent(
                    pattern,
                    ignored -> copyProviders(service.getProviders(pattern)));
        }
    }

    private static List<ICraftingProvider> copyProviders(Iterable<ICraftingProvider> providers) {
        List<ICraftingProvider> result = new ArrayList<>();
        providers.forEach(result::add);
        return List.copyOf(result);
    }

    public static void clear() {
        synchronized (ROUTES) {
            ROUTES.clear();
        }
    }

    private static final class ServiceRoutes {
        private long generation = Long.MIN_VALUE;
        private final IdentityHashMap<IPatternDetails, List<ICraftingProvider>> byPattern =
                new IdentityHashMap<>();
    }
}
