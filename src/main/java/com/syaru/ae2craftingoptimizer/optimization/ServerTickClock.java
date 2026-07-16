package com.syaru.ae2craftingoptimizer.optimization;

public final class ServerTickClock {
    private static volatile long tick;

    private ServerTickClock() {
    }

    public static long currentTick() {
        return tick;
    }

    public static void advance() {
        tick = tick == Long.MAX_VALUE ? 1L : tick + 1L;
    }

    public static void reset() {
        tick = 0L;
    }
}
