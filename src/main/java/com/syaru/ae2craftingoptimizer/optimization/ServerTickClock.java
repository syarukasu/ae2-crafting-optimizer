package com.syaru.ae2craftingoptimizer.optimization;

public final class ServerTickClock {
    private static volatile long tick;
    private static volatile long startedAtNanos = System.nanoTime();

    private ServerTickClock() {
    }

    public static long currentTick() {
        return tick;
    }

    /** ACOのServerTick START listenerが観測した、現在tickの単調時刻を返す。 */
    public static long startedAtNanos() {
        return startedAtNanos;
    }

    public static void advance() {
        startedAtNanos = System.nanoTime();
        tick = tick == Long.MAX_VALUE ? 1L : tick + 1L;
    }

    public static void reset() {
        tick = 0L;
        startedAtNanos = System.nanoTime();
    }
}
