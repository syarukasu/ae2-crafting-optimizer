package com.syaru.ae2craftingoptimizer.optimization;

import com.syaru.ae2craftingoptimizer.api.vector.VectorResourceMode;
import java.util.ArrayList;
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
    private static final LongAdder CRAFTING_ENGINE_SHADOW_MATCHES = new LongAdder();
    private static final LongAdder CRAFTING_ENGINE_SHADOW_MISMATCHES = new LongAdder();
    private static final LongAdder CRAFTING_ENGINE_SHADOW_SKIPS = new LongAdder();
    private static final LongAdder CRAFTING_ENGINE_SHADOW_OVERFLOWS = new LongAdder();
    private static final LongAdder ACTIVE_CALCULATION_DEDUP_HITS = new LongAdder();
    private static final LongAdder ACTIVE_CALCULATION_REGISTRATIONS = new LongAdder();
    private static final LongAdder ACTIVE_CALCULATION_EVICTIONS = new LongAdder();
    private static final LongAdder CALCULATION_DEDUP_STALE_REJECTIONS = new LongAdder();
    private static final LongAdder COMPLETED_PLAN_CACHE_HITS = new LongAdder();
    private static final LongAdder COMPLETED_PLAN_CACHE_STORES = new LongAdder();
    private static final LongAdder PLANNING_GRAPH_CAPTURE_HITS = new LongAdder();
    private static final LongAdder PLANNING_GRAPH_CAPTURE_MISSES = new LongAdder();
    private static final LongAdder PLANNING_GRAPH_COMPILE_HITS = new LongAdder();
    private static final LongAdder PLANNING_GRAPH_COMPILE_MISSES = new LongAdder();
    private static final LongAdder PLANNING_GRAPH_STALE_REJECTIONS = new LongAdder();
    private static final LongAdder PLANNING_GRAPH_CAPTURE_NANOS = new LongAdder();
    private static final LongAdder PLANNING_GRAPH_COMPILE_NANOS = new LongAdder();
    private static final LongAdder PLANNING_CAPTURE_ATTEMPTS = new LongAdder();
    private static final LongAdder PLANNING_CAPTURE_ACCEPTS = new LongAdder();
    private static final LongAdder PLANNING_CAPTURE_NANOS = new LongAdder();
    private static final LongAccumulator PLANNING_CAPTURE_MAX_NANOS =
            new LongAccumulator(Long::max, 0L);
    private static final LongAdder AUTHORITATIVE_PLANNER_ATTEMPTS = new LongAdder();
    private static final LongAdder AUTHORITATIVE_PLANNER_ADOPTIONS = new LongAdder();
    private static final LongAdder AUTHORITATIVE_PLANNER_NANOS = new LongAdder();
    private static final LongAccumulator AUTHORITATIVE_PLANNER_MAX_NANOS =
            new LongAccumulator(Long::max, 0L);
    private static final LongAdder APPLIED_E_PATTERN_FALLBACKS = new LongAdder();
    private static final LongAdder APPLIED_E_DYNAMIC_PROVIDER_REFRESHES = new LongAdder();
    private static final LongAdder APPLIED_E_COMPLETED_PLAN_CACHE_BYPASSES = new LongAdder();
    private static final LongAdder CRAFTING_CALCULATION_DEDUP_HITS = new LongAdder();
    private static final LongAdder CRAFTING_CALCULATION_CACHE_HITS = new LongAdder();
    private static final Map<FallbackReasonCode, LongAdder> CRAFTING_FALLBACK_REASONS = new ConcurrentHashMap<>();
    private static final LongAdder TRANSACTIONAL_V2_TRANSACTIONS = new LongAdder();
    private static final Map<String, LongAdder> TRANSACTIONAL_V2_EXECUTIONS = new ConcurrentHashMap<>();
    private static final Map<String, LongAdder> TRANSACTIONAL_V2_MINIMUM_DECLINES = new ConcurrentHashMap<>();
    private static final LongAdder INSTANT_DISPATCH_CALLS = new LongAdder();
    private static final LongAdder INSTANT_DISPATCH_MULTI_TRANSACTION_CALLS = new LongAdder();
    private static final LongAdder INSTANT_DISPATCH_TRANSACTIONS = new LongAdder();
    private static final LongAdder INSTANT_DISPATCH_EXECUTIONS = new LongAdder();
    private static final LongAdder SEQUENTIAL_INSTANT_WAVES = new LongAdder();
    private static final LongAdder SEQUENTIAL_INSTANT_REQUESTED = new LongAdder();
    private static final LongAdder SEQUENTIAL_INSTANT_COMPLETED = new LongAdder();
    private static final LongAdder SEQUENTIAL_INSTANT_BUDGET_STOPS = new LongAdder();
    private static final LongAdder SEQUENTIAL_INSTANT_TOTAL_NANOS = new LongAdder();
    private static final LongAdder WIDE_COPROCESSOR_RECONSTRUCTIONS = new LongAdder();
    private static final LongAccumulator SEQUENTIAL_INSTANT_MAX_WAVE_NANOS =
            new LongAccumulator(Long::max, 0L);
    private static final LongAdder TRANSACTIONAL_V2_PROBES = new LongAdder();
    private static final LongAdder TRANSACTIONAL_V2_NO_ADAPTER_BYPASSES = new LongAdder();
    private static final LongAdder TRANSACTIONAL_V2_TASKS_SCANNED = new LongAdder();
    private static final LongAdder TRANSACTIONAL_V2_ROUTE_MATCHES = new LongAdder();
    private static final LongAdder TRANSACTIONAL_V2_STANDARD_FALLBACKS = new LongAdder();
    private static final LongAdder TRANSACTIONAL_V2_PATTERN_METADATA_HITS = new LongAdder();
    private static final LongAdder TRANSACTIONAL_V2_PATTERN_METADATA_MISSES = new LongAdder();
    private static final LongAdder TRANSACTIONAL_V2_PATTERN_METADATA_UNSTABLE = new LongAdder();
    private static final LongAdder EXACT_VECTOR_PREPARED_PLANS =
            new LongAdder();
    private static final LongAdder EXACT_VECTOR_EXECUTOR_REJECTIONS =
            new LongAdder();
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
    private static final LongAdder EXACT_VECTOR_START_BUDGET_DEFERRALS =
            new LongAdder();
    private static final LongAdder EXACT_VECTOR_RECEIPT_FREE_ROLLBACKS =
            new LongAdder();
    private static final LongAdder EXACT_VECTOR_FINGERPRINT_REVALIDATIONS =
            new LongAdder();
    private static final LongAccumulator EXACT_VECTOR_MAX_ACTIVE_TICK_NANOS =
            new LongAccumulator(Long::max, 0L);
    private static final LongAdder EXACT_VECTOR_STEPS_SCANNED = new LongAdder();
    private static final LongAdder EXACT_VECTOR_ACTIVE_STEPS_PROCESSED = new LongAdder();
    private static final LongAdder EXACT_VECTOR_ACCOUNTING_REBUILDS = new LongAdder();
    private static final LongAdder EXACT_VECTOR_DIRTY_CALLS_AVOIDED = new LongAdder();
    private static final LongAdder EXACT_VECTOR_ZERO_ALLOCATION_WAITS = new LongAdder();

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

    public static void recordCraftingEngineShadowComparison(boolean matched) {
        (matched ? CRAFTING_ENGINE_SHADOW_MATCHES : CRAFTING_ENGINE_SHADOW_MISMATCHES).increment();
    }

    public static void recordCraftingEngineShadowSkipped() {
        CRAFTING_ENGINE_SHADOW_SKIPS.increment();
    }

    public static void recordCraftingEngineShadowOverflow() {
        CRAFTING_ENGINE_SHADOW_OVERFLOWS.increment();
    }

    public static void recordActiveCalculationDedupHit() {
        ACTIVE_CALCULATION_DEDUP_HITS.increment();
    }

    public static void recordActiveCalculationRegistration() {
        ACTIVE_CALCULATION_REGISTRATIONS.increment();
    }

    public static void recordActiveCalculationEvictions(int evicted) {
        // 上限未到達の通常登録では統計加算を行わない。
        if (evicted <= 0) {
            return;
        }
        ACTIVE_CALCULATION_EVICTIONS.add(evicted);
    }

    public static void recordCalculationDedupStaleRejection() {
        CALCULATION_DEDUP_STALE_REJECTIONS.increment();
    }

    public static void recordCompletedPlanCacheHit() {
        COMPLETED_PLAN_CACHE_HITS.increment();
    }

    public static void recordCompletedPlanCacheStore() {
        COMPLETED_PLAN_CACHE_STORES.increment();
    }

    public static void recordPlanningGraphCaptureCache(boolean hit) {
        (hit ? PLANNING_GRAPH_CAPTURE_HITS : PLANNING_GRAPH_CAPTURE_MISSES).increment();
    }

    public static void recordPlanningGraphCompileCache(boolean hit) {
        (hit ? PLANNING_GRAPH_COMPILE_HITS : PLANNING_GRAPH_COMPILE_MISSES).increment();
    }

    public static void recordPlanningGraphStaleRejection() {
        PLANNING_GRAPH_STALE_REJECTIONS.increment();
    }

    public static void recordPlanningGraphCaptureNanos(long elapsedNanos) {
        // 単調時計の負値は測定不能なので統計へ加えない。
        if (elapsedNanos < 0L) {
            return;
        }
        PLANNING_GRAPH_CAPTURE_NANOS.add(elapsedNanos);
    }

    public static void recordPlanningGraphCompileNanos(long elapsedNanos) {
        // 単調時計の負値は測定不能なので統計へ加えない。
        if (elapsedNanos < 0L) {
            return;
        }
        PLANNING_GRAPH_COMPILE_NANOS.add(elapsedNanos);
    }

    /** server threadで行うimmutable planning captureの件数と実時間を記録する。 */
    public static void recordPlanningCapture(boolean accepted, long elapsedNanos) {
        // 単調時計の負値は測定不能なので統計へ加えない。
        if (elapsedNanos < 0L) {
            return;
        }
        PLANNING_CAPTURE_ATTEMPTS.increment();
        if (accepted) {
            PLANNING_CAPTURE_ACCEPTS.increment();
        }
        PLANNING_CAPTURE_NANOS.add(elapsedNanos);
        PLANNING_CAPTURE_MAX_NANOS.accumulate(elapsedNanos);
    }

    /** worker上のAuthoritative Planner試行と採用結果を記録する。 */
    public static void recordAuthoritativePlanner(boolean adopted, long elapsedNanos) {
        // 単調時計の負値は測定不能なので統計へ加えない。
        if (elapsedNanos < 0L) {
            return;
        }
        AUTHORITATIVE_PLANNER_ATTEMPTS.increment();
        if (adopted) {
            AUTHORITATIVE_PLANNER_ADOPTIONS.increment();
        }
        AUTHORITATIVE_PLANNER_NANOS.add(elapsedNanos);
        AUTHORITATIVE_PLANNER_MAX_NANOS.accumulate(elapsedNanos);
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

    public static void recordCraftingCalculationDeduplication(boolean hit) {
        if (hit) {
            CRAFTING_CALCULATION_DEDUP_HITS.increment();
        }
    }

    public static void recordCraftingCalculationCacheHit() {
        CRAFTING_CALCULATION_CACHE_HITS.increment();
    }

    public static void recordCraftingFallback(FallbackReasonCode code) {
        CRAFTING_FALLBACK_REASONS.computeIfAbsent(code, ignored -> new LongAdder()).increment();
    }

    public static void recordTransactionalV2Commit(String adapterId, long executions) {
        TRANSACTIONAL_V2_TRANSACTIONS.increment();
        TRANSACTIONAL_V2_EXECUTIONS.computeIfAbsent(adapterId, ignored -> new LongAdder()).add(executions);
    }

    public static void recordTransactionalV2MinimumDecline(String adapterId) {
        TRANSACTIONAL_V2_MINIMUM_DECLINES
                .computeIfAbsent(
                        adapterId,
                        ignored -> new LongAdder())
                .increment();
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
        SEQUENTIAL_INSTANT_TOTAL_NANOS.add(Math.max(0L, elapsedNanos));
        SEQUENTIAL_INSTANT_MAX_WAVE_NANOS.accumulate(Math.max(0L, elapsedNanos));
    }

    public static void recordSequentialInstantBudgetStop() {
        SEQUENTIAL_INSTANT_BUDGET_STOPS.increment();
    }

    public static void recordWideCoprocessorReconstruction() {
        WIDE_COPROCESSOR_RECONSTRUCTIONS.increment();
    }

    public static void recordTransactionalV2Probe() {
        TRANSACTIONAL_V2_PROBES.increment();
    }

    public static void recordTransactionalV2NoAdapterBypass() {
        TRANSACTIONAL_V2_NO_ADAPTER_BYPASSES.increment();
    }

    public static void recordTransactionalV2TasksScanned(int tasks) {
        // 呼出側が0件を渡す場合は、診断上の意味がないため加算しない。
        if (tasks <= 0) {
            return;
        }
        TRANSACTIONAL_V2_TASKS_SCANNED.add(tasks);
    }

    public static void recordTransactionalV2RouteMatch() {
        TRANSACTIONAL_V2_ROUTE_MATCHES.increment();
    }

    public static void recordTransactionalV2StandardFallback() {
        TRANSACTIONAL_V2_STANDARD_FALLBACKS.increment();
    }

    public static void recordTransactionalV2PatternMetadataCacheHit() {
        TRANSACTIONAL_V2_PATTERN_METADATA_HITS.increment();
    }

    public static void recordTransactionalV2PatternMetadataCacheMiss() {
        TRANSACTIONAL_V2_PATTERN_METADATA_MISSES.increment();
    }

    public static void recordTransactionalV2PatternMetadataUnstable() {
        TRANSACTIONAL_V2_PATTERN_METADATA_UNSTABLE.increment();
    }

    /** 所有方式ごとのTransaction開始を一回だけ数える。 */
    public static void recordExactVectorPreparedPlan() {
        EXACT_VECTOR_PREPARED_PLANS.increment();
    }

    public static void recordExactVectorExecutorRejection() {
        EXACT_VECTOR_EXECUTOR_REJECTIONS.increment();
    }

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
        recordExactVectorActiveStages(1, elapsedNanos);
    }

    /** 設備が一括進行した論理段数と最長slice実時間だけを記録する。 */
    public static void recordExactVectorActiveStages(
            int stages,
            long elapsedNanos) {
        if (stages <= 0) {
            throw new IllegalArgumentException(
                    "Exact Vector stages must be positive");
        }
        EXACT_VECTOR_ACTIVE_TICKS.add(stages);
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

    public static void recordExactVectorStartBudgetDeferral() {
        EXACT_VECTOR_START_BUDGET_DEFERRALS.increment();
    }

    public static void recordExactVectorReceiptFreeRollback() {
        EXACT_VECTOR_RECEIPT_FREE_ROLLBACKS.increment();
    }

    public static void recordExactVectorFingerprintRevalidation() {
        EXACT_VECTOR_FINGERPRINT_REVALIDATIONS.increment();
    }

    public static void recordExactVectorQueueWork(long scanned, long processed) {
        EXACT_VECTOR_STEPS_SCANNED.add(Math.max(0L, scanned));
        EXACT_VECTOR_ACTIVE_STEPS_PROCESSED.add(Math.max(0L, processed));
    }

    public static void recordExactVectorAccountingSnapshotRebuild() {
        EXACT_VECTOR_ACCOUNTING_REBUILDS.increment();
    }

    public static void recordExactVectorDirtyCallAvoided() {
        EXACT_VECTOR_DIRTY_CALLS_AVOIDED.increment();
    }

    public static void recordExactVectorZeroAllocationWait() {
        EXACT_VECTOR_ZERO_ALLOCATION_WAITS.increment();
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
        List<String> lines = new ArrayList<>(List.of(
                "Shared CPU budget: " + SHARED_BUDGET_LIMITS.sum()
                        + " limit(s), " + SHARED_DEFERRED_OPERATIONS.sum() + " operation(s) deferred",
                "GTCEu intent candidate cache: " + gtHits + " hit(s), " + gtMisses
                        + " miss(es), " + percent(gtHits, gtMisses) + "% hit rate",
                "Mekanism resolved recipe cache: " + mekHits + " hit(s), " + mekMisses
                        + " miss(es), " + percent(mekHits, mekMisses) + "% hit rate",
                "Mekanism recipe validations: " + MEKANISM_RECIPE_VALIDATIONS.sum(),
                "Experimental planner Shadow Mode: " + CRAFTING_ENGINE_SHADOW_MATCHES.sum()
                        + " match(es), " + CRAFTING_ENGINE_SHADOW_MISMATCHES.sum()
                        + " mismatch(es), " + CRAFTING_ENGINE_SHADOW_SKIPS.sum()
                        + " skip(s), " + CRAFTING_ENGINE_SHADOW_OVERFLOWS.sum() + " overflow(s)",
                "Crafting calculation reuse: " + ACTIVE_CALCULATION_DEDUP_HITS.sum()
                        + " in-flight hit(s)/" + ACTIVE_CALCULATION_REGISTRATIONS.sum()
                        + " registration(s), " + ACTIVE_CALCULATION_EVICTIONS.sum()
                        + " index eviction(s), completed " + COMPLETED_PLAN_CACHE_HITS.sum()
                        + " hit(s)/" + COMPLETED_PLAN_CACHE_STORES.sum() + " store(s), "
                        + CALCULATION_DEDUP_STALE_REJECTIONS.sum() + " stale rejection(s)",
                "Immutable planning graph: capture " + PLANNING_GRAPH_CAPTURE_HITS.sum()
                        + " hit(s)/" + PLANNING_GRAPH_CAPTURE_MISSES.sum() + " miss(es), compile "
                        + PLANNING_GRAPH_COMPILE_HITS.sum() + " hit(s)/"
                        + PLANNING_GRAPH_COMPILE_MISSES.sum() + " miss(es), "
                        + PLANNING_GRAPH_STALE_REJECTIONS.sum() + " stale rejection(s), capture "
                        + (PLANNING_GRAPH_CAPTURE_NANOS.sum() / 1_000L) + " us total, compile "
                        + (PLANNING_GRAPH_COMPILE_NANOS.sum() / 1_000L) + " us total",
                "Planning boundary: immutable capture " + PLANNING_CAPTURE_ACCEPTS.sum()
                        + "/" + PLANNING_CAPTURE_ATTEMPTS.sum() + " accepted, total/max "
                        + (PLANNING_CAPTURE_NANOS.sum() / 1_000L) + "/"
                        + (PLANNING_CAPTURE_MAX_NANOS.get() / 1_000L) + " us; authoritative planner "
                        + AUTHORITATIVE_PLANNER_ADOPTIONS.sum() + "/"
                        + AUTHORITATIVE_PLANNER_ATTEMPTS.sum() + " adopted, total/max "
                        + (AUTHORITATIVE_PLANNER_NANOS.sum() / 1_000L) + "/"
                        + (AUTHORITATIVE_PLANNER_MAX_NANOS.get() / 1_000L) + " us",
                "AppliedE compatibility: " + APPLIED_E_PATTERN_FALLBACKS.sum()
                        + " dynamic pattern fallback(s), " + APPLIED_E_DYNAMIC_PROVIDER_REFRESHES.sum()
                        + " provider refresh(es) preserved, " + APPLIED_E_COMPLETED_PLAN_CACHE_BYPASSES.sum()
                        + " completed plan cache bypass(es)",
                "AE2 calculation reuse: " + CRAFTING_CALCULATION_DEDUP_HITS.sum()
                        + " active hit(s), " + CRAFTING_CALCULATION_CACHE_HITS.sum()
                        + " completed-plan hit(s)",
                "AE2 fallback reasons: " + CRAFTING_FALLBACK_REASONS,
                "Transactional V2 commits: " + TRANSACTIONAL_V2_TRANSACTIONS.sum()
                        + " transaction(s), executions by adapter " + TRANSACTIONAL_V2_EXECUTIONS
                        + ", below-minimum decline(s) by adapter " + TRANSACTIONAL_V2_MINIMUM_DECLINES,
                "Sequential Instant: " + SEQUENTIAL_INSTANT_WAVES.sum()
                        + " wave(s), " + SEQUENTIAL_INSTANT_COMPLETED.sum()
                        + "/" + SEQUENTIAL_INSTANT_REQUESTED.sum() + " operation(s), "
                        + SEQUENTIAL_INSTANT_BUDGET_STOPS.sum() + " budget stop(s), max wave "
                        + (SEQUENTIAL_INSTANT_MAX_WAVE_NANOS.get() / 1_000L) + " us, average wave "
                        + averageMicros(SEQUENTIAL_INSTANT_TOTAL_NANOS.sum(), SEQUENTIAL_INSTANT_WAVES.sum())
                        + " us",
                "Wide co-processor execution count: "
                        + WIDE_COPROCESSOR_RECONSTRUCTIONS.sum() + " cluster reconstruction(s)",
                "Transactional V2 probes: " + TRANSACTIONAL_V2_PROBES.sum()
                        + " call(s), " + TRANSACTIONAL_V2_NO_ADAPTER_BYPASSES.sum()
                        + " no-adapter bypass(es), " + TRANSACTIONAL_V2_TASKS_SCANNED.sum()
                        + " task(s) scanned, " + TRANSACTIONAL_V2_ROUTE_MATCHES.sum()
                        + " route match(es), " + TRANSACTIONAL_V2_STANDARD_FALLBACKS.sum()
                        + " standard fallback(s)",
                "Transactional V2 pattern metadata: "
                        + TRANSACTIONAL_V2_PATTERN_METADATA_HITS.sum() + " cache hit(s), "
                        + TRANSACTIONAL_V2_PATTERN_METADATA_MISSES.sum() + " miss(es), "
                        + TRANSACTIONAL_V2_PATTERN_METADATA_UNSTABLE.sum() + " unstable compile(s)",
                "Physical crafting tree: starts host/network "
                        + EXACT_VECTOR_HOST_ESCROWED_STARTS.sum() + "/"
                        + EXACT_VECTOR_NETWORK_STORAGE_STARTS.sum()
                        + ", prepared/rejected "
                        + EXACT_VECTOR_PREPARED_PLANS.sum() + "/"
                        + EXACT_VECTOR_EXECUTOR_REJECTIONS.sum()
                        + ", active scheduler tick(s) " + EXACT_VECTOR_ACTIVE_TICKS.sum()
                        + ", completed/cancelled/quarantined "
                        + EXACT_VECTOR_COMPLETIONS.sum() + "/"
                        + EXACT_VECTOR_CANCELLATIONS.sum() + "/"
                        + EXACT_VECTOR_QUARANTINES.sum()
                        + ", start defer/receipt rollback/revalidated "
                        + EXACT_VECTOR_START_BUDGET_DEFERRALS.sum() + "/"
                        + EXACT_VECTOR_RECEIPT_FREE_ROLLBACKS.sum() + "/"
                        + EXACT_VECTOR_FINGERPRINT_REVALIDATIONS.sum()
                        + ", queue scanned/processed "
                        + EXACT_VECTOR_STEPS_SCANNED.sum() + "/"
                        + EXACT_VECTOR_ACTIVE_STEPS_PROCESSED.sum()
                        + ", accounting rebuilds " + EXACT_VECTOR_ACCOUNTING_REBUILDS.sum()
                        + ", dirty calls avoided " + EXACT_VECTOR_DIRTY_CALLS_AVOIDED.sum()
                        + ", zero-allocation waits " + EXACT_VECTOR_ZERO_ALLOCATION_WAITS.sum()
                        + ", max active range "
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
                         + " status update(s) coalesced"));
        lines.addAll(BigIntegerPlanDiagnostics.summaryLines());
        // domain gateの拒否理由を固定個数で表示し、無効化が効いているか観測可能にする。
        for (var entry : OptimizationFeatureGate.denialSnapshot().entrySet()) {
            OptimizationFeatureGate.DenialSnapshot denial = entry.getValue();
            lines.add("Feature gate " + entry.getKey()
                    + ": denied feature(s) by master/domain/feature "
                    + denial.masterDisabled() + "/"
                    + denial.domainDisabled() + "/"
                    + denial.featureDisabled());
        }
        return List.copyOf(lines);
    }

    public static void reset() {
        OptimizationFeatureGate.resetDiagnostics();
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
        CRAFTING_ENGINE_SHADOW_MATCHES.reset();
        CRAFTING_ENGINE_SHADOW_MISMATCHES.reset();
        CRAFTING_ENGINE_SHADOW_SKIPS.reset();
        CRAFTING_ENGINE_SHADOW_OVERFLOWS.reset();
        ACTIVE_CALCULATION_DEDUP_HITS.reset();
        ACTIVE_CALCULATION_REGISTRATIONS.reset();
        ACTIVE_CALCULATION_EVICTIONS.reset();
        CALCULATION_DEDUP_STALE_REJECTIONS.reset();
        COMPLETED_PLAN_CACHE_HITS.reset();
        COMPLETED_PLAN_CACHE_STORES.reset();
        PLANNING_GRAPH_CAPTURE_HITS.reset();
        PLANNING_GRAPH_CAPTURE_MISSES.reset();
        PLANNING_GRAPH_COMPILE_HITS.reset();
        PLANNING_GRAPH_COMPILE_MISSES.reset();
        PLANNING_GRAPH_STALE_REJECTIONS.reset();
        PLANNING_GRAPH_CAPTURE_NANOS.reset();
        PLANNING_GRAPH_COMPILE_NANOS.reset();
        PLANNING_CAPTURE_ATTEMPTS.reset();
        PLANNING_CAPTURE_ACCEPTS.reset();
        PLANNING_CAPTURE_NANOS.reset();
        PLANNING_CAPTURE_MAX_NANOS.reset();
        AUTHORITATIVE_PLANNER_ATTEMPTS.reset();
        AUTHORITATIVE_PLANNER_ADOPTIONS.reset();
        AUTHORITATIVE_PLANNER_NANOS.reset();
        AUTHORITATIVE_PLANNER_MAX_NANOS.reset();
        APPLIED_E_PATTERN_FALLBACKS.reset();
        APPLIED_E_DYNAMIC_PROVIDER_REFRESHES.reset();
        APPLIED_E_COMPLETED_PLAN_CACHE_BYPASSES.reset();
        CRAFTING_CALCULATION_DEDUP_HITS.reset();
        CRAFTING_CALCULATION_CACHE_HITS.reset();
        CRAFTING_FALLBACK_REASONS.clear();
        TRANSACTIONAL_V2_TRANSACTIONS.reset();
        TRANSACTIONAL_V2_EXECUTIONS.clear();
        TRANSACTIONAL_V2_MINIMUM_DECLINES.clear();
        INSTANT_DISPATCH_CALLS.reset();
        INSTANT_DISPATCH_MULTI_TRANSACTION_CALLS.reset();
        INSTANT_DISPATCH_TRANSACTIONS.reset();
        INSTANT_DISPATCH_EXECUTIONS.reset();
        SEQUENTIAL_INSTANT_WAVES.reset();
        SEQUENTIAL_INSTANT_REQUESTED.reset();
        SEQUENTIAL_INSTANT_COMPLETED.reset();
        SEQUENTIAL_INSTANT_BUDGET_STOPS.reset();
        SEQUENTIAL_INSTANT_TOTAL_NANOS.reset();
        WIDE_COPROCESSOR_RECONSTRUCTIONS.reset();
        SEQUENTIAL_INSTANT_MAX_WAVE_NANOS.reset();
        TRANSACTIONAL_V2_PROBES.reset();
        TRANSACTIONAL_V2_NO_ADAPTER_BYPASSES.reset();
        TRANSACTIONAL_V2_TASKS_SCANNED.reset();
        TRANSACTIONAL_V2_ROUTE_MATCHES.reset();
        TRANSACTIONAL_V2_STANDARD_FALLBACKS.reset();
        TRANSACTIONAL_V2_PATTERN_METADATA_HITS.reset();
        TRANSACTIONAL_V2_PATTERN_METADATA_MISSES.reset();
        TRANSACTIONAL_V2_PATTERN_METADATA_UNSTABLE.reset();
        EXACT_VECTOR_HOST_ESCROWED_STARTS.reset();
        EXACT_VECTOR_NETWORK_STORAGE_STARTS.reset();
        EXACT_VECTOR_PREPARED_PLANS.reset();
        EXACT_VECTOR_EXECUTOR_REJECTIONS.reset();
        EXACT_VECTOR_ACTIVE_TICKS.reset();
        EXACT_VECTOR_COMPLETIONS.reset();
        EXACT_VECTOR_CANCELLATIONS.reset();
        EXACT_VECTOR_QUARANTINES.reset();
        EXACT_VECTOR_START_BUDGET_DEFERRALS.reset();
        EXACT_VECTOR_RECEIPT_FREE_ROLLBACKS.reset();
        EXACT_VECTOR_FINGERPRINT_REVALIDATIONS.reset();
        EXACT_VECTOR_MAX_ACTIVE_TICK_NANOS.reset();
        BigIntegerPlanDiagnostics.reset();
        EXACT_VECTOR_STEPS_SCANNED.reset();
        EXACT_VECTOR_ACTIVE_STEPS_PROCESSED.reset();
        EXACT_VECTOR_ACCOUNTING_REBUILDS.reset();
        EXACT_VECTOR_DIRTY_CALLS_AVOIDED.reset();
        EXACT_VECTOR_ZERO_ALLOCATION_WAITS.reset();
    }

    private static long percent(long hits, long misses) {
        long total = hits + misses;
        return total == 0L ? 0L : Math.round(hits * 100.0D / total);
    }

    private static long averageMicros(long totalNanos, long samples) {
        // サンプル0件では除算せず、未計測を0 usとして表示する。
        if (samples <= 0L) {
            return 0L;
        }
        return totalNanos / samples / 1_000L;
    }

}
