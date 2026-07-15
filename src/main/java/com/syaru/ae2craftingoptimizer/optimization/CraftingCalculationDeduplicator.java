package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEKey;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Future;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class CraftingCalculationDeduplicator {
    private static final Map<CraftingService, Map<RequestKey, Entry>> ACTIVE_CALCULATIONS = new WeakHashMap<>();
    private static final Map<CraftingService, Map<RequestKey, CompletedEntry>> COMPLETED_PLANS = new WeakHashMap<>();
    private static final long NANOS_PER_TICK = 50_000_000L;

    private CraftingCalculationDeduplicator() {
    }

    public static Future<ICraftingPlan> findActive(
            CraftingService craftingService,
            Level level,
            ICraftingSimulationRequester requester,
            AEKey output,
            long amount,
            CalculationStrategy strategy) {
        if (!ACOConfig.deduplicateActiveCraftingCalculations()) {
            return null;
        }

        RequestKey requestKey = RequestKey.of(level, requester, output, amount, strategy);
        long now = System.nanoTime();

        synchronized (ACTIVE_CALCULATIONS) {
            Map<RequestKey, Entry> serviceEntries = ACTIVE_CALCULATIONS.get(craftingService);
            if (serviceEntries == null) {
                return findCompletedLocked(craftingService, requestKey, now);
            }

            cleanupActive(craftingService, serviceEntries, now);
            Entry entry = serviceEntries.get(requestKey);
            if (entry == null || !entry.isReusable(now)) {
                return findCompletedLocked(craftingService, requestKey, now);
            }

            if (ACOConfig.logCraftingCalculationDeduplication()) {
                AE2CraftingOptimizer.LOGGER.debug(
                        "Reused active AE2 crafting calculation for {} x{} ({})",
                        output.getId(),
                        amount,
                        strategy);
            }
            return entry.future;
        }
    }

    public static void remember(
            CraftingService craftingService,
            Level level,
            ICraftingSimulationRequester requester,
            AEKey output,
            long amount,
            CalculationStrategy strategy,
            Future<ICraftingPlan> future) {
        if (!ACOConfig.deduplicateActiveCraftingCalculations() || future == null || future.isDone() || future.isCancelled()) {
            return;
        }

        RequestKey requestKey = RequestKey.of(level, requester, output, amount, strategy);
        long now = System.nanoTime();

        synchronized (ACTIVE_CALCULATIONS) {
            Map<RequestKey, Entry> serviceEntries = ACTIVE_CALCULATIONS.computeIfAbsent(craftingService, ignored -> new HashMap<>());
            cleanupActive(craftingService, serviceEntries, now);
            serviceEntries.put(requestKey, new Entry(future, now));
        }
    }

    public static void clear(String reason) {
        synchronized (ACTIVE_CALCULATIONS) {
            ACTIVE_CALCULATIONS.clear();
            COMPLETED_PLANS.clear();
        }
        if (ACOConfig.logCraftingCalculationDeduplication()) {
            AE2CraftingOptimizer.LOGGER.debug("Cleared active AE2 crafting calculation table: {}", reason);
        }
    }

    public static void clearCompleted(String reason) {
        synchronized (ACTIVE_CALCULATIONS) {
            COMPLETED_PLANS.clear();
        }
        if (ACOConfig.logCraftingCalculationDeduplication()) {
            AE2CraftingOptimizer.LOGGER.debug("Cleared completed AE2 crafting plan cache: {}", reason);
        }
    }

    private static Future<ICraftingPlan> findCompletedLocked(CraftingService craftingService, RequestKey requestKey, long now) {
        if (!ACOConfig.cacheCompletedCraftingPlans()) {
            return null;
        }

        Map<RequestKey, CompletedEntry> serviceEntries = COMPLETED_PLANS.get(craftingService);
        if (serviceEntries == null) {
            return null;
        }

        cleanupCompleted(serviceEntries, now);
        CompletedEntry entry = serviceEntries.get(requestKey);
        if (entry == null || !entry.isReusable(now)) {
            return null;
        }

        if (ACOConfig.logCraftingCalculationDeduplication()) {
            AE2CraftingOptimizer.LOGGER.debug(
                    "Reused completed AE2 crafting plan for {} x{} ({})",
                    requestKey.output.getId(),
                    requestKey.amount,
                    requestKey.strategy);
        }
        return java.util.concurrent.CompletableFuture.completedFuture(entry.plan);
    }

    private static void cleanupActive(CraftingService craftingService, Map<RequestKey, Entry> serviceEntries, long now) {
        Iterator<Map.Entry<RequestKey, Entry>> iterator = serviceEntries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<RequestKey, Entry> mapEntry = iterator.next();
            Entry entry = mapEntry.getValue();
            if (entry.future.isDone() && !entry.future.isCancelled()) {
                rememberCompleted(craftingService, mapEntry.getKey(), entry, now);
            }
            if (!entry.isReusable(now)) {
                iterator.remove();
            }
        }
    }

    private static void rememberCompleted(CraftingService craftingService, RequestKey requestKey, Entry entry, long now) {
        if (!ACOConfig.cacheCompletedCraftingPlans()) {
            return;
        }

        ICraftingPlan plan;
        try {
            plan = entry.future.get();
        } catch (Exception ignored) {
            return;
        }

        if (!isCompletedPlanCacheable(plan)) {
            return;
        }

        Map<RequestKey, CompletedEntry> completedEntries = COMPLETED_PLANS.computeIfAbsent(craftingService, ignored -> new HashMap<>());
        cleanupCompleted(completedEntries, now);
        if (completedEntries.size() >= ACOConfig.getCompletedCraftingPlanCacheSize()) {
            Iterator<RequestKey> iterator = completedEntries.keySet().iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
        completedEntries.put(requestKey, new CompletedEntry(plan, now));
    }

    private static boolean isCompletedPlanCacheable(ICraftingPlan plan) {
        if (plan == null) {
            return false;
        }

        if (plan.simulation() || !plan.missingItems().isEmpty()) {
            return true;
        }

        return ACOConfig.cacheSuccessfulCompletedCraftingPlans();
    }

    private static void cleanupCompleted(Map<RequestKey, CompletedEntry> serviceEntries, long now) {
        Iterator<CompletedEntry> iterator = serviceEntries.values().iterator();
        while (iterator.hasNext()) {
            CompletedEntry entry = iterator.next();
            if (!entry.isReusable(now)) {
                iterator.remove();
            }
        }
    }

    private static long maximumAgeNanos() {
        return ACOConfig.getActiveCalculationDeduplicationWindowTicks() * NANOS_PER_TICK;
    }

    private static long completedMaximumAgeNanos() {
        return ACOConfig.getCompletedCraftingPlanCacheTtlTicks() * NANOS_PER_TICK;
    }

    private record RequestKey(
            ResourceLocation dimension,
            String requesterClass,
            int requesterIdentity,
            AEKey output,
            long amount,
            CalculationStrategy strategy) {
        private static RequestKey of(
                Level level,
                ICraftingSimulationRequester requester,
                AEKey output,
                long amount,
                CalculationStrategy strategy) {
            ResourceLocation dimension = level.dimension().location();
            return new RequestKey(
                    dimension,
                    requester.getClass().getName(),
                    System.identityHashCode(requester),
                    output,
                    amount,
                    strategy);
        }
    }

    private record Entry(Future<ICraftingPlan> future, long createdAtNanos) {
        private boolean isReusable(long now) {
            return !future.isDone()
                    && !future.isCancelled()
                    && now - createdAtNanos <= maximumAgeNanos();
        }
    }

    private record CompletedEntry(ICraftingPlan plan, long createdAtNanos) {
        private boolean isReusable(long now) {
            return now - createdAtNanos <= completedMaximumAgeNanos();
        }
    }
}
