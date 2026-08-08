package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.stacks.AEKey;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Per-calculation fallback state plus compact aggregation for slow-calculation
 * diagnostics. No AE2 objects are retained by the thread-local state.
 */
public final class CraftingFallbackDiagnostics {
    private static final int MAX_AGGREGATES = 4096;
    private static final ThreadLocal<Observation> CURRENT = new ThreadLocal<>();
    private static final Map<AggregateKey, LongAdder> AGGREGATES = new ConcurrentHashMap<>();

    private CraftingFallbackDiagnostics() {
    }

    public static void reset() {
        CURRENT.remove();
    }

    public static void record(
            AEKey output,
            long patternGeneration,
            long recipeGeneration,
            FallbackReasonCode code) {
        if (CURRENT.get() != null) {
            return;
        }
        String outputId = output == null ? "<unknown>" : output.getId().toString();
        AggregateKey key = new AggregateKey(outputId, patternGeneration, recipeGeneration, code);
        if (AGGREGATES.size() >= MAX_AGGREGATES) {
            AGGREGATES.clear();
        }
        LongAdder aggregate = AGGREGATES.computeIfAbsent(key, ignored -> new LongAdder());
        aggregate.increment();
        long aggregateCount = aggregate.sum();
        CURRENT.set(new Observation(code, patternGeneration, recipeGeneration, aggregateCount));
        OptimizationMetrics.recordCraftingFallback(code);
    }

    public static Observation take() {
        Observation observation = CURRENT.get();
        CURRENT.remove();
        return observation == null ? Observation.none() : observation;
    }

    public record Observation(
            FallbackReasonCode code,
            long patternGeneration,
            long recipeGeneration,
            long aggregateCount) {
        private static Observation none() {
            return new Observation(FallbackReasonCode.UNKNOWN, -1L, -1L, 0L);
        }
    }

    private record AggregateKey(
            String outputId,
            long patternGeneration,
            long recipeGeneration,
            FallbackReasonCode code) {
    }
}
