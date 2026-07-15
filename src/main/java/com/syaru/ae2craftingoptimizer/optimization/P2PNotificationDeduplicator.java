package com.syaru.ae2craftingoptimizer.optimization;

import appeng.hooks.ticking.TickHandler;
import appeng.me.service.P2PService;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.util.Map;
import java.util.WeakHashMap;

public final class P2PNotificationDeduplicator {
    private static final Map<P2PService, Long> LAST_WAKE_TICKS = new WeakHashMap<>();

    private P2PNotificationDeduplicator() {
    }

    public static synchronized boolean shouldWakeInputs(P2PService service) {
        if (!ACOConfig.deepP2PTopologyChangeOnlyRecheck()) {
            return true;
        }

        long currentTick = TickHandler.instance().getCurrentTick();
        Long previousTick = LAST_WAKE_TICKS.get(service);
        if (previousTick != null
                && currentTick - previousTick < ACOConfig.getDeepP2PDuplicateWindowTicks()) {
            return false;
        }
        LAST_WAKE_TICKS.put(service, currentTick);
        return true;
    }

    public static synchronized void clear() {
        LAST_WAKE_TICKS.clear();
    }
}
