package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

public final class CraftingRequestThrottle {
    private static final Map<ICraftingRequester, Map<RequestKey, Entry>> THROTTLED_REQUESTS = new WeakHashMap<>();
    private static final long NANOS_PER_TICK = 50_000_000L;

    private CraftingRequestThrottle() {
    }

    public static boolean shouldThrottle(ICraftingRequester owner, int slot, AEKey key, long amount) {
        if (!ACOConfig.throttleExportBusCraftRequests()) {
            return false;
        }

        RequestKey requestKey = new RequestKey(slot, key, amount);
        long now = System.nanoTime();
        synchronized (THROTTLED_REQUESTS) {
            Map<RequestKey, Entry> ownerEntries = THROTTLED_REQUESTS.get(owner);
            if (ownerEntries == null) {
                return false;
            }

            cleanup(ownerEntries, now);
            Entry entry = ownerEntries.get(requestKey);
            boolean throttled = entry != null && entry.untilNanos > now;
            if (throttled && ACOConfig.logGridTickBudget()) {
                AE2CraftingOptimizer.LOGGER.debug(
                        "Throttled repeated AE2 crafting request from {} slot {} for {} x{}",
                        ownerName(owner),
                        slot,
                        key.getId(),
                        amount);
            }
            return throttled;
        }
    }

    public static void recordFailure(ICraftingRequester owner, int slot, AEKey key, long amount) {
        if (!ACOConfig.throttleExportBusCraftRequests()) {
            return;
        }

        long now = System.nanoTime();
        long cooldownNanos = ACOConfig.getExportBusCraftFailureCooldownTicks() * NANOS_PER_TICK;
        RequestKey requestKey = new RequestKey(slot, key, amount);
        int maxEntries = ACOConfig.getExportBusCraftThrottleCacheSize();
        synchronized (THROTTLED_REQUESTS) {
            Map<RequestKey, Entry> ownerEntries = THROTTLED_REQUESTS.computeIfAbsent(owner, ignored -> new HashMap<>());
            cleanup(ownerEntries, now);
            if (ownerEntries.size() >= maxEntries) {
                Iterator<RequestKey> iterator = ownerEntries.keySet().iterator();
                if (iterator.hasNext()) {
                    iterator.next();
                    iterator.remove();
                }
            }
            ownerEntries.put(requestKey, new Entry(now + cooldownNanos));
        }
    }

    public static void clear(String reason) {
        synchronized (THROTTLED_REQUESTS) {
            THROTTLED_REQUESTS.clear();
        }
        if (ACOConfig.logGridTickBudget()) {
            AE2CraftingOptimizer.LOGGER.debug("Cleared AE2 crafting request throttle table: {}", reason);
        }
    }

    private static void cleanup(Map<RequestKey, Entry> ownerEntries, long now) {
        Iterator<Entry> iterator = ownerEntries.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (entry.untilNanos <= now) {
                iterator.remove();
            }
        }
    }

    private static String ownerName(ICraftingRequester owner) {
        return owner == null ? "<null>" : owner.getClass().getName();
    }

    private record RequestKey(int slot, AEKey key, long amount) {
    }

    private record Entry(long untilNanos) {
    }
}
