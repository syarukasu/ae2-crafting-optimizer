package com.syaru.ae2craftingoptimizer.optimization;

import com.syaru.ae2craftingoptimizer.api.vector.VectorResourceMode;
import java.math.BigInteger;
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
    private static final LongAdder APPLIED_E_PATTERN_FALLBACKS = new LongAdder();
    private static final LongAdder APPLIED_E_DYNAMIC_PROVIDER_REFRESHES = new LongAdder();
    private static final LongAdder APPLIED_E_COMPLETED_PLAN_CACHE_BYPASSES = new LongAdder();
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
    private static final LongAdder CRAFTING_ISLAND_CACHE_HITS = new LongAdder();
    private static final LongAdder CRAFTING_ISLAND_CACHE_MISSES = new LongAdder();
    private static final LongAdder CRAFTING_ISLAND_ATTEMPTS = new LongAdder();
    private static final LongAdder CRAFTING_ISLAND_COMPILE_REJECTS = new LongAdder();
    private static final LongAdder CRAFTING_ISLAND_BACKEND_REJECTS = new LongAdder();
    private static final LongAdder CRAFTING_ISLAND_CAPACITY_WAITS = new LongAdder();
    private static final LongAdder CRAFTING_ISLAND_STALE_TASKS = new LongAdder();
    private static final LongAdder CRAFTING_ISLAND_INPUT_WAITS = new LongAdder();
    private static final LongAdder CRAFTING_ISLAND_PROVIDER_REJECTS = new LongAdder();
    private static final LongAdder CRAFTING_ISLAND_OUTPUT_WAITS = new LongAdder();
    private static final LongAdder CRAFTING_ISLAND_ENERGY_WAITS = new LongAdder();
    private static final LongAdder CRAFTING_ISLAND_WAVES = new LongAdder();
    private static final LongAdder CRAFTING_ISLAND_PATTERNS = new LongAdder();
    private static final LongAccumulator CRAFTING_ISLAND_LOGICAL_EXECUTIONS =
            new LongAccumulator(OptimizationMetrics::saturatedAdd, 0L);
    private static final LongAccumulator CRAFTING_ISLAND_MAX_WAVE_NANOS =
            new LongAccumulator(Long::max, 0L);
    private static final LongAdder EXACT_VECTOR_HOST_ESCROWED_STARTS =
            new LongAdder();
    private static final LongAdder EXACT_VECTOR_NETWORK_STORAGE_STARTS =
            new LongAdder();
    private static final LongAdder EXACT_VECTOR_ACTIVE_TICKS =
            new LongAdder();
    private static final LongAdder EXACT_VECTOR_COMPLETIONS =
            new LongAdder();
    private static final LongAdder EXACT_VECTOR_CANCELLATIONS =
            new LongAdder();
    private static final LongAdder EXACT_VECTOR_QUARANTINES =
            new LongAdder();
    private static final LongAccumulator EXACT_VECTOR_MAX_ACTIVE_TICK_NANOS =
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

    public static void recordAppliedEPatternFallback() {
        APPLIED_E_PATTERN_FALLBACKS.increment();
    }

    public static void recordAppliedEDynamicProviderRefresh() {
        APPLIED_E_DYNAMIC_PROVIDER_REFRESHES.increment();
    }

    public static void recordAppliedECompletedPlanCacheBypass() {
        APPLIED_E_COMPLETED_PLAN_CACHE_BYPASSES.increment();
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

    public static void recordCraftingIslandCache(boolean hit) {
        // 一回の参照をhit/missどちらか一方へだけ加算する。
        (hit ? CRAFTING_ISLAND_CACHE_HITS : CRAFTING_ISLAND_CACHE_MISSES).increment();
    }

    public static void recordCraftingIslandAttempt() {
        CRAFTING_ISLAND_ATTEMPTS.increment();
    }

    public static void recordCraftingIslandDecision(
            CraftingIslandDecision decision) {
        // enumごとに一つのカウンタだけを増やし、/aco statsで停止段階を区別する。
        switch (decision) {
            case COMPILE_REJECTED -> CRAFTING_ISLAND_COMPILE_REJECTS.increment();
            case BACKEND_UNAVAILABLE -> CRAFTING_ISLAND_BACKEND_REJECTS.increment();
            case CAPACITY_WAIT -> CRAFTING_ISLAND_CAPACITY_WAITS.increment();
            case STALE_TASK -> CRAFTING_ISLAND_STALE_TASKS.increment();
            case INPUT_WAIT -> CRAFTING_ISLAND_INPUT_WAITS.increment();
            case PROVIDER_REJECTED -> CRAFTING_ISLAND_PROVIDER_REJECTS.increment();
            case OUTPUT_WAIT -> CRAFTING_ISLAND_OUTPUT_WAITS.increment();
            case ENERGY_WAIT -> CRAFTING_ISLAND_ENERGY_WAITS.increment();
        }
    }

    public static void recordCraftingIslandWave(
            int patterns,
            BigInteger logicalExecutions,
            long elapsedNanos) {
        CRAFTING_ISLAND_WAVES.increment();
        CRAFTING_ISLAND_PATTERNS.add(Math.max(0, patterns));
        // LongAdderへ入らない巨大論理回数はwrapせずLong.MAX_VALUEへ診断表示だけを飽和させる。
        long boundedExecutions = logicalExecutions.signum() < 0
                ? 0L
                : logicalExecutions.bitLength() > Long.SIZE - 1
                        ? Long.MAX_VALUE
                        : logicalExecutions.longValueExact();
        CRAFTING_ISLAND_LOGICAL_EXECUTIONS.accumulate(boundedExecutions);
        CRAFTING_ISLAND_MAX_WAVE_NANOS.accumulate(Math.max(0L, elapsedNanos));
    }

    /** 所有方式ごとのTransaction開始を一回だけ数える。 */
    public static void recordExactVectorStart(VectorResourceMode mode) {
        // CPU EscrowとME Storage直接所有を分け、どちらの経路が使われたか表示する。
        if (mode == VectorResourceMode.HOST_ESCROWED) {
            EXACT_VECTOR_HOST_ESCROWED_STARTS.increment();
        } else {
            EXACT_VECTOR_NETWORK_STORAGE_STARTS.increment();
        }
    }

    /** 設備のactive tick件数と最長実時間だけを記録する。 */
    public static void recordExactVectorActiveTick(long elapsedNanos) {
        EXACT_VECTOR_ACTIVE_TICKS.increment();
        EXACT_VECTOR_MAX_ACTIVE_TICK_NANOS.accumulate(
                Math.max(0L, elapsedNanos));
    }

    public static void recordExactVectorCompletion() {
        EXACT_VECTOR_COMPLETIONS.increment();
    }

    public static void recordExactVectorCancellation() {
        EXACT_VECTOR_CANCELLATIONS.increment();
    }

    public static void recordExactVectorQuarantine() {
        EXACT_VECTOR_QUARANTINES.increment();
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
                "AppliedE compatibility: " + APPLIED_E_PATTERN_FALLBACKS.sum()
                        + " dynamic pattern fallback(s), " + APPLIED_E_DYNAMIC_PROVIDER_REFRESHES.sum()
                        + " provider refresh(es) preserved, " + APPLIED_E_COMPLETED_PLAN_CACHE_BYPASSES.sum()
                        + " completed plan cache bypass(es)",
                "Experimental native batch: " + NATIVE_BATCH_TRANSACTIONS.sum()
                        + " transaction(s), executions by adapter " + NATIVE_BATCH_EXECUTIONS,
                "Sequential Instant: " + SEQUENTIAL_INSTANT_WAVES.sum()
                        + " wave(s), " + SEQUENTIAL_INSTANT_COMPLETED.sum()
                        + "/" + SEQUENTIAL_INSTANT_REQUESTED.sum() + " operation(s), "
                        + SEQUENTIAL_INSTANT_BUDGET_STOPS.sum() + " budget stop(s), max wave "
                        + (SEQUENTIAL_INSTANT_MAX_WAVE_NANOS.get() / 1_000L) + " us",
                "Compiled Crafting Islands: " + CRAFTING_ISLAND_WAVES.sum()
                        + " wave(s), " + CRAFTING_ISLAND_PATTERNS.sum()
                        + " pattern(s), " + CRAFTING_ISLAND_LOGICAL_EXECUTIONS.get()
                        + " logical execution(s), cache " + CRAFTING_ISLAND_CACHE_HITS.sum()
                        + " hit(s)/" + CRAFTING_ISLAND_CACHE_MISSES.sum()
                        + " miss(es), max wave "
                        + (CRAFTING_ISLAND_MAX_WAVE_NANOS.get() / 1_000L) + " us",
                "Compiled Island decisions: " + CRAFTING_ISLAND_ATTEMPTS.sum()
                        + " attempt(s), compile/backend/capacity/stale/input/provider/output/energy = "
                        + CRAFTING_ISLAND_COMPILE_REJECTS.sum() + "/"
                        + CRAFTING_ISLAND_BACKEND_REJECTS.sum() + "/"
                        + CRAFTING_ISLAND_CAPACITY_WAITS.sum() + "/"
                        + CRAFTING_ISLAND_STALE_TASKS.sum() + "/"
                        + CRAFTING_ISLAND_INPUT_WAITS.sum() + "/"
                        + CRAFTING_ISLAND_PROVIDER_REJECTS.sum() + "/"
                        + CRAFTING_ISLAND_OUTPUT_WAITS.sum() + "/"
                        + CRAFTING_ISLAND_ENERGY_WAITS.sum(),
                "Exact Vector: starts host/network "
                        + EXACT_VECTOR_HOST_ESCROWED_STARTS.sum() + "/"
                        + EXACT_VECTOR_NETWORK_STORAGE_STARTS.sum()
                        + ", active tick(s) " + EXACT_VECTOR_ACTIVE_TICKS.sum()
                        + ", completed/cancelled/quarantined "
                        + EXACT_VECTOR_COMPLETIONS.sum() + "/"
                        + EXACT_VECTOR_CANCELLATIONS.sum() + "/"
                        + EXACT_VECTOR_QUARANTINES.sum()
                        + ", max active tick "
                        + (EXACT_VECTOR_MAX_ACTIVE_TICK_NANOS.get() / 1_000L)
                        + " us",
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
        APPLIED_E_PATTERN_FALLBACKS.reset();
        APPLIED_E_DYNAMIC_PROVIDER_REFRESHES.reset();
        APPLIED_E_COMPLETED_PLAN_CACHE_BYPASSES.reset();
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
        CRAFTING_ISLAND_CACHE_HITS.reset();
        CRAFTING_ISLAND_CACHE_MISSES.reset();
        CRAFTING_ISLAND_ATTEMPTS.reset();
        CRAFTING_ISLAND_COMPILE_REJECTS.reset();
        CRAFTING_ISLAND_BACKEND_REJECTS.reset();
        CRAFTING_ISLAND_CAPACITY_WAITS.reset();
        CRAFTING_ISLAND_STALE_TASKS.reset();
        CRAFTING_ISLAND_INPUT_WAITS.reset();
        CRAFTING_ISLAND_PROVIDER_REJECTS.reset();
        CRAFTING_ISLAND_OUTPUT_WAITS.reset();
        CRAFTING_ISLAND_ENERGY_WAITS.reset();
        CRAFTING_ISLAND_WAVES.reset();
        CRAFTING_ISLAND_PATTERNS.reset();
        CRAFTING_ISLAND_LOGICAL_EXECUTIONS.reset();
        CRAFTING_ISLAND_MAX_WAVE_NANOS.reset();
        EXACT_VECTOR_HOST_ESCROWED_STARTS.reset();
        EXACT_VECTOR_NETWORK_STORAGE_STARTS.reset();
        EXACT_VECTOR_ACTIVE_TICKS.reset();
        EXACT_VECTOR_COMPLETIONS.reset();
        EXACT_VECTOR_CANCELLATIONS.reset();
        EXACT_VECTOR_QUARANTINES.reset();
        EXACT_VECTOR_MAX_ACTIVE_TICK_NANOS.reset();
    }

    private static long percent(long hits, long misses) {
        long total = hits + misses;
        return total == 0L ? 0L : Math.round(hits * 100.0D / total);
    }

    private static long saturatedAdd(long left, long right) {
        // 診断カウンタ自身がwrapしないよう、残量を超える加算はLong.MAX_VALUEで固定する。
        return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }

    public enum CraftingIslandDecision {
        COMPILE_REJECTED,
        BACKEND_UNAVAILABLE,
        CAPACITY_WAIT,
        STALE_TASK,
        INPUT_WAIT,
        PROVIDER_REJECTED,
        OUTPUT_WAIT,
        ENERGY_WAIT
    }
}
