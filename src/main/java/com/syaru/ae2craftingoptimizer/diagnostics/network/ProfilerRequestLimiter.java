package com.syaru.ae2craftingoptimizer.diagnostics.network;

import java.util.Map;
import java.util.WeakHashMap;

/** Server-thread-only limiter; disconnected players are not retained by diagnostics. */
public final class ProfilerRequestLimiter<T> {
    public static final long INTERVAL_NANOS = 1_000_000_000L;
    private final Map<T, Long> lastRequests = new WeakHashMap<>();

    public boolean tryAcquire(T identity, boolean operator, long now) {
        if (!operator || identity == null) {
            return false;
        }
        Long previous = lastRequests.get(identity);
        // nanoTime origins may be negative; subtraction also handles wraparound.
        if (previous != null && now - previous < INTERVAL_NANOS) {
            return false;
        }
        lastRequests.put(identity, now);
        return true;
    }
}
