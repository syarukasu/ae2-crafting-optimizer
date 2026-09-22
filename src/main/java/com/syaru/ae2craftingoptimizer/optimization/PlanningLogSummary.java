package com.syaru.ae2craftingoptimizer.optimization;

import java.util.Locale;

/** Issue #209: bounded observations, never crafting quantities or live job owners. */
public final class PlanningLogSummary {
    private final long intervalNanos;
    private long epoch;
    private long previousReport;
    private long started;
    private long active;
    private long completed;
    private long aborted;
    private long compiled;
    private long snapshot;
    private long standard;
    private long other;
    private long exact;
    private long missing;
    private double elapsedTotal;
    private long elapsedMax;

    public PlanningLogSummary(long intervalNanos) {
        if (intervalNanos <= 0) throw new IllegalArgumentException("interval must be positive");
        this.intervalNanos = intervalNanos;
    }

    public synchronized void reset(long now) {
        epoch++;
        active = 0;
        clearWindow();
        previousReport = now;
    }

    public synchronized long begin() {
        started++;
        active++;
        return epoch;
    }

    public synchronized void finish(long token, String route, boolean wide, boolean simulation, long nanos) {
        if (token != epoch || active == 0) return;
        active--;
        if (route == null) {
            aborted++;
            return;
        }
        completed++;
        switch (route) {
            case "compiled-strict" -> compiled++;
            case "ae2-snapshot" -> snapshot++;
            case "ae2-standard" -> standard++;
            default -> other++;
        }
        if (wide) exact++;
        if (simulation) missing++;
        long elapsed = Math.max(0, nanos);
        elapsedTotal += elapsed;
        elapsedMax = Math.max(elapsedMax, elapsed);
    }

    public synchronized Snapshot poll(long now) {
        return poll(now, false);
    }

    public synchronized boolean isDue(long now) {
        return now - previousReport >= intervalNanos;
    }

    public synchronized Snapshot poll(long now, boolean otherActivity) {
        long window = now - previousReport;
        if (window < intervalNanos) return null;
        previousReport = now;
        if (!otherActivity && started == 0 && completed == 0 && aborted == 0 && active == 0) return null;
        Snapshot result = new Snapshot(window, started, active, completed, aborted,
                compiled, snapshot, standard, other, exact, missing,
                completed == 0 ? 0 : elapsedTotal / completed / 1_000_000.0,
                elapsedMax / 1_000_000.0);
        clearWindow();
        return result;
    }

    private void clearWindow() {
        started = completed = aborted = compiled = snapshot = standard = other = exact = missing = 0;
        elapsedTotal = 0;
        elapsedMax = 0;
    }

    public record Snapshot(long windowNanos, long started, long active, long completed, long aborted,
            long compiled, long snapshot, long standard, long other, long exact, long missing,
            double averageMs, double maxMs) {
        public String line() {
            return String.format(Locale.ROOT,
                    "[ACO Stats] windowSeconds=%.1f started=%d active=%d planComplete=%d aborted=%d "
                    + "compiled=%d ae2Snapshot=%d ae2Standard=%d other=%d exactPlans=%d missingPlans=%d "
                    + "avgMs=%.3f maxMs=%.3f",
                    windowNanos / 1_000_000_000.0, started, active, completed, aborted,
                    compiled, snapshot, standard, other, exact, missing, averageMs, maxMs);
        }
    }
}
