package com.syaru.ae2craftingoptimizer.optimization;

import java.util.List;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

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
    private static final LongAdder TRANSACTIONAL_PATTERN_BATCH_COMMITS = new LongAdder();
    private static final LongAdder TRANSACTIONAL_PATTERN_BATCH_EXECUTIONS = new LongAdder();
    private static final LongAdder CRAFTING_ENGINE_SHADOW_MATCHES = new LongAdder();
    private static final LongAdder CRAFTING_ENGINE_SHADOW_MISMATCHES = new LongAdder();
    private static final LongAdder CRAFTING_ENGINE_SHADOW_SKIPS = new LongAdder();
    private static final LongAdder CRAFTING_ENGINE_SHADOW_OVERFLOWS = new LongAdder();
    private static final LongAdder NATIVE_BATCH_TRANSACTIONS = new LongAdder();
    private static final Map<String, LongAdder> NATIVE_BATCH_EXECUTIONS = new ConcurrentHashMap<>();
    private static final LongAdder INSTANT_DISPATCH_CALLS = new LongAdder();
    private static final LongAdder INSTANT_DISPATCH_MULTI_TRANSACTION_CALLS = new LongAdder();
    private static final LongAdder INSTANT_DISPATCH_TRANSACTIONS = new LongAdder();
    private static final LongAdder INSTANT_DISPATCH_EXECUTIONS = new LongAdder();
    private static final LongAdder SEQUENTIAL_INSTANT_WAVES = new LongAdder();
    private static final LongAdder SEQUENTIAL_INSTANT_REQUESTED = new LongAdder();
    private static final LongAdder SEQUENTIAL_INSTANT_COMPLETED = new LongAdder();
    private static final LongAdder SEQUENTIAL_INSTANT_BUDGET_STOPS = new LongAdder();
    private static final LongAccumulator SEQUENTIAL_INSTANT_MAX_WAVE_NANOS =
            new LongAccumulator(Long::max, 0L);

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

    public static void recordTransactionalPatternBatch(long patternExecutions) {
        TRANSACTIONAL_PATTERN_BATCH_COMMITS.increment();
        TRANSACTIONAL_PATTERN_BATCH_EXECUTIONS.add(Math.max(1L, patternExecutions));
    }

    public static void recordCraftingEngineShadowComparison(boolean matched) {
        (matched ? CRAFTING_ENGINE_SHADOW_MATCHES : CRAFTING_ENGINE_SHADOW_MISMATCHES).increment();
    }

    public static void recordCraftingEngineShadowSkipped() {
        CRAFTING_ENGINE_SHADOW_SKIPS.increment();
    }

    public static void recordCraftingEngineShadowOverflow() {
        CRAFTING_ENGINE_SHADOW_OVERFLOWS.increment();
    }

    public static void recordNativePatternBatch(String adapterId, long executions) {
        NATIVE_BATCH_TRANSACTIONS.increment();
        NATIVE_BATCH_EXECUTIONS.computeIfAbsent(adapterId, ignored -> new LongAdder()).add(executions);
    }

    /** Instantが一回のCPU呼び出しで実際に何取引を配送したかを記録する。 */
    public static void recordInstantPatternDispatch(int transactions, int executions) {
        if (transactions <= 0 || executions <= 0) {
            return;
        }
        INSTANT_DISPATCH_CALLS.increment();
        if (transactions > 1) {
            INSTANT_DISPATCH_MULTI_TRANSACTION_CALLS.increment();
        }
        INSTANT_DISPATCH_TRANSACTIONS.add(transactions);
        INSTANT_DISPATCH_EXECUTIONS.add(executions);
    }

    /** AE2標準会計を使う単発Instantの実測値を記録する。 */
    public static void recordSequentialInstantWave(int requested, int completed, long elapsedNanos) {
        SEQUENTIAL_INSTANT_WAVES.increment();
        SEQUENTIAL_INSTANT_REQUESTED.add(Math.max(0, requested));
        SEQUENTIAL_INSTANT_COMPLETED.add(Math.max(0, completed));
        SEQUENTIAL_INSTANT_MAX_WAVE_NANOS.accumulate(Math.max(0L, elapsedNanos));
    }

    public static void recordSequentialInstantBudgetStop() {
        SEQUENTIAL_INSTANT_BUDGET_STOPS.increment();
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
                "Transactional pattern batching: " + TRANSACTIONAL_PATTERN_BATCH_COMMITS.sum()
                        + " adapter commit(s), " + TRANSACTIONAL_PATTERN_BATCH_EXECUTIONS.sum()
                        + " exactly accepted execution(s)",
                "Experimental planner Shadow Mode: " + CRAFTING_ENGINE_SHADOW_MATCHES.sum()
                        + " match(es), " + CRAFTING_ENGINE_SHADOW_MISMATCHES.sum()
                        + " mismatch(es), " + CRAFTING_ENGINE_SHADOW_SKIPS.sum()
                        + " skip(s), " + CRAFTING_ENGINE_SHADOW_OVERFLOWS.sum() + " overflow(s)",
                "Experimental native batch: " + NATIVE_BATCH_TRANSACTIONS.sum()
                        + " transaction(s), executions by adapter " + NATIVE_BATCH_EXECUTIONS,
                "Sequential Instant: " + SEQUENTIAL_INSTANT_WAVES.sum()
                        + " wave(s), " + SEQUENTIAL_INSTANT_COMPLETED.sum()
                        + "/" + SEQUENTIAL_INSTANT_REQUESTED.sum() + " operation(s), "
                        + SEQUENTIAL_INSTANT_BUDGET_STOPS.sum() + " budget stop(s), max wave "
                        + (SEQUENTIAL_INSTANT_MAX_WAVE_NANOS.get() / 1_000L) + " us",
                "Experimental V2 Instant: " + INSTANT_DISPATCH_CALLS.sum()
                        + " successful call(s), " + INSTANT_DISPATCH_MULTI_TRANSACTION_CALLS.sum()
                        + " multi-transaction call(s), " + INSTANT_DISPATCH_TRANSACTIONS.sum()
                        + " transaction(s), " + INSTANT_DISPATCH_EXECUTIONS.sum()
                        + " execution(s)",
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
        TRANSACTIONAL_PATTERN_BATCH_COMMITS.reset();
        TRANSACTIONAL_PATTERN_BATCH_EXECUTIONS.reset();
        CRAFTING_ENGINE_SHADOW_MATCHES.reset();
        CRAFTING_ENGINE_SHADOW_MISMATCHES.reset();
        CRAFTING_ENGINE_SHADOW_SKIPS.reset();
        CRAFTING_ENGINE_SHADOW_OVERFLOWS.reset();
        NATIVE_BATCH_TRANSACTIONS.reset();
        NATIVE_BATCH_EXECUTIONS.clear();
        INSTANT_DISPATCH_CALLS.reset();
        INSTANT_DISPATCH_MULTI_TRANSACTION_CALLS.reset();
        INSTANT_DISPATCH_TRANSACTIONS.reset();
        INSTANT_DISPATCH_EXECUTIONS.reset();
        SEQUENTIAL_INSTANT_WAVES.reset();
        SEQUENTIAL_INSTANT_REQUESTED.reset();
        SEQUENTIAL_INSTANT_COMPLETED.reset();
        SEQUENTIAL_INSTANT_BUDGET_STOPS.reset();
        SEQUENTIAL_INSTANT_MAX_WAVE_NANOS.reset();
    }

    private static long percent(long hits, long misses) {
        long total = hits + misses;
        return total == 0L ? 0L : Math.round(hits * 100.0D / total);
    }
}
