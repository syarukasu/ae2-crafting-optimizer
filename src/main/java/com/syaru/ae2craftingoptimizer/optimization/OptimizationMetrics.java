package com.syaru.ae2craftingoptimizer.optimization;

import java.util.List;
import java.util.concurrent.atomic.LongAdder;

public final class OptimizationMetrics {
    private static final LongAdder SHARED_BUDGET_LIMITS = new LongAdder();
    private static final LongAdder SHARED_DEFERRED_OPERATIONS = new LongAdder();
    private static final LongAdder GT_CANDIDATE_CACHE_HITS = new LongAdder();
    private static final LongAdder GT_CANDIDATE_CACHE_MISSES = new LongAdder();
    private static final LongAdder MEKANISM_RECIPE_CACHE_HITS = new LongAdder();
    private static final LongAdder MEKANISM_RECIPE_CACHE_MISSES = new LongAdder();
    private static final LongAdder MEKANISM_RECIPE_VALIDATIONS = new LongAdder();
    private static final LongAdder REFLECTION_LOOKUP_HITS = new LongAdder();
    private static final LongAdder REFLECTION_LOOKUP_MISSES = new LongAdder();
    private static final LongAdder AE2_OVERCLOCK_UPGRADE_COUNT_HITS = new LongAdder();
    private static final LongAdder AE2_OVERCLOCK_UPGRADE_COUNT_MISSES = new LongAdder();
    private static final LongAdder REACTION_CHAMBER_RECIPE_REUSES = new LongAdder();
    private static final LongAdder ASSEMBLER_MATRIX_THREAD_COUNT_HITS = new LongAdder();
    private static final LongAdder ASSEMBLER_MATRIX_BUSY_COUNT_HITS = new LongAdder();
    private static final LongAdder ASSEMBLER_MATRIX_STATUS_UPDATES_COALESCED = new LongAdder();
    private static final LongAdder PATTERN_MICRO_BATCH_PUSHES = new LongAdder();
    private static final LongAdder PATTERN_MICRO_BATCH_EXECUTIONS = new LongAdder();

    private OptimizationMetrics() {
    }

    public static void recordSharedBudgetLimit(int requestedOperations, int grantedOperations) {
        if (grantedOperations >= requestedOperations) {
            return;
        }
        SHARED_BUDGET_LIMITS.increment();
        SHARED_DEFERRED_OPERATIONS.add((long) requestedOperations - grantedOperations);
    }

    public static void recordGtCandidateCache(boolean hit) {
        (hit ? GT_CANDIDATE_CACHE_HITS : GT_CANDIDATE_CACHE_MISSES).increment();
    }

    public static void recordMekanismRecipeCache(boolean hit) {
        (hit ? MEKANISM_RECIPE_CACHE_HITS : MEKANISM_RECIPE_CACHE_MISSES).increment();
    }

    public static void recordMekanismRecipeValidation() {
        MEKANISM_RECIPE_VALIDATIONS.increment();
    }

    public static void recordReflectionLookup(boolean hit) {
        (hit ? REFLECTION_LOOKUP_HITS : REFLECTION_LOOKUP_MISSES).increment();
    }

    public static void recordAe2OverclockUpgradeCount(boolean hit) {
        (hit ? AE2_OVERCLOCK_UPGRADE_COUNT_HITS : AE2_OVERCLOCK_UPGRADE_COUNT_MISSES).increment();
    }

    public static void recordReactionChamberRecipeReuse() {
        REACTION_CHAMBER_RECIPE_REUSES.increment();
    }

    public static void recordAssemblerMatrixThreadCountHit() {
        ASSEMBLER_MATRIX_THREAD_COUNT_HITS.increment();
    }

    public static void recordAssemblerMatrixBusyCountHit() {
        ASSEMBLER_MATRIX_BUSY_COUNT_HITS.increment();
    }

    public static void recordAssemblerMatrixStatusUpdateCoalesced() {
        ASSEMBLER_MATRIX_STATUS_UPDATES_COALESCED.increment();
    }

    public static void recordPatternMicroBatch(long patternExecutions) {
        PATTERN_MICRO_BATCH_PUSHES.increment();
        PATTERN_MICRO_BATCH_EXECUTIONS.add(Math.max(1L, patternExecutions));
    }

    public static List<String> summaryLines() {
        long gtHits = GT_CANDIDATE_CACHE_HITS.sum();
        long gtMisses = GT_CANDIDATE_CACHE_MISSES.sum();
        long mekHits = MEKANISM_RECIPE_CACHE_HITS.sum();
        long mekMisses = MEKANISM_RECIPE_CACHE_MISSES.sum();
        long reflectionHits = REFLECTION_LOOKUP_HITS.sum();
        long reflectionMisses = REFLECTION_LOOKUP_MISSES.sum();
        long upgradeHits = AE2_OVERCLOCK_UPGRADE_COUNT_HITS.sum();
        long upgradeMisses = AE2_OVERCLOCK_UPGRADE_COUNT_MISSES.sum();
        return List.of(
                "Shared CPU budget: " + SHARED_BUDGET_LIMITS.sum()
                        + " limit(s), " + SHARED_DEFERRED_OPERATIONS.sum() + " operation(s) deferred",
                "GTCEu intent candidate cache: " + gtHits + " hit(s), " + gtMisses
                        + " miss(es), " + percent(gtHits, gtMisses) + "% hit rate",
                "Mekanism resolved recipe cache: " + mekHits + " hit(s), " + mekMisses
                        + " miss(es), " + percent(mekHits, mekMisses) + "% hit rate",
                "Mekanism recipe validations: " + MEKANISM_RECIPE_VALIDATIONS.sum(),
                "Pattern micro-batching: " + PATTERN_MICRO_BATCH_PUSHES.sum()
                        + " aggregate push(es), " + PATTERN_MICRO_BATCH_EXECUTIONS.sum()
                        + " pattern execution(s)",
                "AE2 Overclock reflection cache: " + reflectionHits + " hit(s), " + reflectionMisses
                        + " miss(es), " + percent(reflectionHits, reflectionMisses) + "% hit rate",
                "AE2 Overclock upgrade-count cache: " + upgradeHits + " hit(s), " + upgradeMisses
                        + " miss(es), " + percent(upgradeHits, upgradeMisses) + "% hit rate",
                "AdvancedAE Reaction Chamber recipe reuses: " + REACTION_CHAMBER_RECIPE_REUSES.sum(),
                "ExtendedAE Assembly Matrix: " + ASSEMBLER_MATRIX_THREAD_COUNT_HITS.sum()
                        + " thread-count hit(s), " + ASSEMBLER_MATRIX_BUSY_COUNT_HITS.sum()
                        + " busy-count hit(s), " + ASSEMBLER_MATRIX_STATUS_UPDATES_COALESCED.sum()
                        + " status update(s) coalesced");
    }

    public static void reset() {
        SHARED_BUDGET_LIMITS.reset();
        SHARED_DEFERRED_OPERATIONS.reset();
        GT_CANDIDATE_CACHE_HITS.reset();
        GT_CANDIDATE_CACHE_MISSES.reset();
        MEKANISM_RECIPE_CACHE_HITS.reset();
        MEKANISM_RECIPE_CACHE_MISSES.reset();
        MEKANISM_RECIPE_VALIDATIONS.reset();
        REFLECTION_LOOKUP_HITS.reset();
        REFLECTION_LOOKUP_MISSES.reset();
        AE2_OVERCLOCK_UPGRADE_COUNT_HITS.reset();
        AE2_OVERCLOCK_UPGRADE_COUNT_MISSES.reset();
        REACTION_CHAMBER_RECIPE_REUSES.reset();
        ASSEMBLER_MATRIX_THREAD_COUNT_HITS.reset();
        ASSEMBLER_MATRIX_BUSY_COUNT_HITS.reset();
        ASSEMBLER_MATRIX_STATUS_UPDATES_COALESCED.reset();
        PATTERN_MICRO_BATCH_PUSHES.reset();
        PATTERN_MICRO_BATCH_EXECUTIONS.reset();
    }

    private static long percent(long hits, long misses) {
        long total = hits + misses;
        return total == 0L ? 0L : Math.round(hits * 100.0D / total);
    }
}
