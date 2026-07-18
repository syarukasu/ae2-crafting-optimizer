package com.syaru.ae2craftingoptimizer.scheduler;

import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class FairCraftingJobScheduler {
    private static final Map<CraftingService, DeficitRoundRobinScheduler> SCHEDULERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private FairCraftingJobScheduler() {
    }

    public static boolean supports(Object executionOwner) {
        return executionOwner instanceof FairSchedulerStateStore;
    }

    public static int grant(
            CraftingService service,
            Object executionOwner,
            int requestedOperations,
            long gameTick) {
        if (service == null
                || !(executionOwner instanceof FairSchedulerStateStore stateStore)
                || requestedOperations <= 0) {
            return requestedOperations;
        }
        DeficitRoundRobinScheduler scheduler;
        synchronized (SCHEDULERS) {
            scheduler = SCHEDULERS.computeIfAbsent(service, unused -> new DeficitRoundRobinScheduler());
        }
        return scheduler.grant(
                stateStore.aco$getFairSchedulerState(),
                requestedOperations,
                gameTick,
                ACOConfig.getFairSchedulerOperationsPerTick(),
                ACOConfig.getFairSchedulerQuantum(),
                ACOConfig.getFairSchedulerTimeBudgetMillis() * 1_000_000L);
    }

    public static void recordElapsed(CraftingService service, long gameTick, long elapsedNanos) {
        if (service == null || elapsedNanos <= 0L) {
            return;
        }
        DeficitRoundRobinScheduler scheduler;
        synchronized (SCHEDULERS) {
            scheduler = SCHEDULERS.get(service);
        }
        if (scheduler != null) {
            scheduler.recordElapsed(gameTick, elapsedNanos);
        }
    }

    public static void clear() {
        synchronized (SCHEDULERS) {
            SCHEDULERS.clear();
        }
    }
}
