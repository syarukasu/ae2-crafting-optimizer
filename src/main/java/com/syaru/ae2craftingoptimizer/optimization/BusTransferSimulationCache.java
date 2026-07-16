package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public final class BusTransferSimulationCache {
    private static final Map<Object, Set<Request>> REJECTED = new IdentityHashMap<>();
    private static long cacheTick = Long.MIN_VALUE;

    private BusTransferSimulationCache() {
    }

    public static synchronized boolean wasRejected(Object storage, AEKey what, long amount) {
        if (!ACOConfig.cacheNegativeBusTransferSimulations() || storage == null || what == null || amount <= 0) {
            return false;
        }

        advanceTick();
        Set<Request> requests = REJECTED.get(storage);
        return requests != null && requests.contains(new Request(what, amount));
    }

    public static synchronized void rememberRejected(Object storage, AEKey what, long amount) {
        if (!ACOConfig.cacheNegativeBusTransferSimulations() || storage == null || what == null || amount <= 0) {
            return;
        }

        advanceTick();
        REJECTED.computeIfAbsent(storage, ignored -> new HashSet<>()).add(new Request(what, amount));
    }

    public static synchronized void clear() {
        REJECTED.clear();
        cacheTick = Long.MIN_VALUE;
    }

    private static void advanceTick() {
        long currentTick = ServerTickClock.currentTick();
        if (cacheTick != currentTick) {
            REJECTED.clear();
            cacheTick = currentTick;
        }
    }

    private record Request(AEKey what, long amount) {
    }
}
