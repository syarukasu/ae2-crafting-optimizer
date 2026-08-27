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
    private static final LongAdder TRANSACTIONAL_PATTERN_BATCH_COMMITS = new LongAdder();
    private static final LongAdder TRANSACTIONAL_PATTERN_BATCH_EXECUTIONS = new LongAdder();
    private static final LongAdder CRAFTING_ENGINE_SHADOW_MATCHES = new LongAdder();
    private static final LongAdder CRAFTING_ENGINE_SHADOW_MISMATCHES = new LongAdder();
    private static final LongAdder CRAFTING_ENGINE_SHADOW_SKIPS = new LongAdder();
    private static final LongAdder CRAFTING_ENGINE_SHADOW_OVERFLOWS = new LongAdder();
    private static final LongAdder DECISION_PROGRAM_CACHE_HITS = new LongAdder();
    private static final LongAdder DECISION_PROGRAM_CACHE_MISSES = new LongAdder();
    private static final LongAdder APPLIED_E_PATTERN_FALLBACKS = new LongAdder();
    private static final LongAdder APPLIED_E_DYNAMIC_PROVIDER_REFRESHES = new LongAdder();
    private static final LongAdder APPLIED_E_COMPLETED_PLAN_CACHE_BYPASSES = new LongAdder();
    private static final LongAdder CRAFTING_CALCULATION_DEDUP_HITS = new LongAdder();
    private static final LongAdder CRAFTING_CALCULATION_CACHE_HITS = new LongAdder();
    private static final Map<FallbackReasonCode, LongAdder> CRAFTING_FALLBACK_REASONS = new ConcurrentHashMap<>();
    private static final LongAdder NATIVE_BATCH_TRANSACTIONS = new LongAdder();
    private static final Map<String, LongAdder> NATIVE_BATCH_EXECUTIONS = new ConcurrentHashMap<>();
    private static final Map<String, LongAdder> NATIVE_BATCH_MINIMUM_DECLINES = new ConcurrentHashMap<>();
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
    private static final LongAdder EXACT_STORAGE_SNAPSHOT_CACHE_HITS =
            new LongAdder();
    private static final LongAdder EXACT_STORAGE_SNAPSHOT_CACHE_MISSES =
            new LongAdder();
    private static final LongAdder EXACT_STORAGE_NESTED_SCANS =
            new LongAdder();
    private static final LongAdder EXACT_STORAGE_SNAPSHOT_INVALIDATIONS =
            new LongAdder();
    private static final LongAdder EXACT_STORAGE_TERMINAL_REUSES =
            new LongAdder();
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

    public static void recordDecisionProgramCache(boolean hit) {
        (hit ? DECISION_PROGRAM_CACHE_HITS : DECISION_PROGRAM_CACHE_MISSES).increment();
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

    public static void recordNativePatternBatch(String adapterId, long executions) {
        NATIVE_BATCH_TRANSACTIONS.increment();
        NATIVE_BATCH_EXECUTIONS.computeIfAbsent(adapterId, ignored -> new LongAdder()).add(executions);
    }

    public static void recordNativeBatchMinimumDecline(String adapterId) {
        NATIVE_BATCH_MINIMUM_DECLINES
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
        SEQUENTIAL_INSTANT_MAX_WAVE_NANOS.accumulate(Math.max(0L, elapsedNanos));
    }

    public static void recordSequentialInstantBudgetStop() {
        SEQUENTIAL_INSTANT_BUDGET_STOPS.increment();
    }

    public static void recordExactStorageSnapshotCache(boolean hit) {
        (hit
                ? EXACT_STORAGE_SNAPSHOT_CACHE_HITS
                : EXACT_STORAGE_SNAPSHOT_CACHE_MISSES).increment();
    }

    public static void recordExactStorageNestedScan() {
        EXACT_STORAGE_NESTED_SCANS.increment();
    }

    public static void recordExactStorageSnapshotInvalidation() {
        EXACT_STORAGE_SNAPSHOT_INVALIDATIONS.increment();
    }

    public static void recordExactStorageTerminalReuse() {
        EXACT_STORAGE_TERMINAL_REUSES.increment();
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
        long decisionHits = DECISION_PROGRAM_CACHE_HITS.sum();
        long decisionMisses = DECISION_PROGRAM_CACHE_MISSES.sum();
        List<String> lines = new ArrayList<>(List.of(
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
                "AE2 Decision Program cache: " + decisionHits + " hit(s), " + decisionMisses
                        + " miss(es), " + percent(decisionHits, decisionMisses) + "% hit rate",
                "AppliedE compatibility: " + APPLIED_E_PATTERN_FALLBACKS.sum()
                        + " dynamic pattern fallback(s), " + APPLIED_E_DYNAMIC_PROVIDER_REFRESHES.sum()
                        + " provider refresh(es) preserved, " + APPLIED_E_COMPLETED_PLAN_CACHE_BYPASSES.sum()
                        + " completed plan cache bypass(es)",
                "AE2 calculation reuse: " + CRAFTING_CALCULATION_DEDUP_HITS.sum()
                        + " active hit(s), " + CRAFTING_CALCULATION_CACHE_HITS.sum()
                        + " completed-plan hit(s)",
                "AE2 fallback reasons: " + CRAFTING_FALLBACK_REASONS,
                "Experimental native batch: " + NATIVE_BATCH_TRANSACTIONS.sum()
                        + " transaction(s), executions by adapter " + NATIVE_BATCH_EXECUTIONS
                        + ", below-minimum fallback(s) by adapter " + NATIVE_BATCH_MINIMUM_DECLINES,
                "Sequential Instant: " + SEQUENTIAL_INSTANT_WAVES.sum()
                        + " wave(s), " + SEQUENTIAL_INSTANT_COMPLETED.sum()
                        + "/" + SEQUENTIAL_INSTANT_REQUESTED.sum() + " operation(s), "
                        + SEQUENTIAL_INSTANT_BUDGET_STOPS.sum() + " budget stop(s), max wave "
                        + (SEQUENTIAL_INSTANT_MAX_WAVE_NANOS.get() / 1_000L) + " us",
                "Exact storage snapshots: "
                        + EXACT_STORAGE_SNAPSHOT_CACHE_HITS.sum() + " cache hit(s), "
                        + EXACT_STORAGE_SNAPSHOT_CACHE_MISSES.sum() + " miss(es), "
                        + EXACT_STORAGE_NESTED_SCANS.sum() + " nested scan(s), "
                        + EXACT_STORAGE_SNAPSHOT_INVALIDATIONS.sum() + " invalidation(s), "
                        + EXACT_STORAGE_TERMINAL_REUSES.sum() + " terminal reuse(s)",
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
                    + ": denied feature(s) by master/domain/feature/retired "
                    + denial.masterDisabled() + "/"
                    + denial.domainDisabled() + "/"
                    + denial.featureDisabled() + "/"
                    + denial.retiredCompatibilityKey());
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
        TRANSACTIONAL_PATTERN_BATCH_COMMITS.reset();
        TRANSACTIONAL_PATTERN_BATCH_EXECUTIONS.reset();
        CRAFTING_ENGINE_SHADOW_MATCHES.reset();
        CRAFTING_ENGINE_SHADOW_MISMATCHES.reset();
        CRAFTING_ENGINE_SHADOW_SKIPS.reset();
        CRAFTING_ENGINE_SHADOW_OVERFLOWS.reset();
        DECISION_PROGRAM_CACHE_HITS.reset();
        DECISION_PROGRAM_CACHE_MISSES.reset();
        APPLIED_E_PATTERN_FALLBACKS.reset();
        APPLIED_E_DYNAMIC_PROVIDER_REFRESHES.reset();
        APPLIED_E_COMPLETED_PLAN_CACHE_BYPASSES.reset();
        CRAFTING_CALCULATION_DEDUP_HITS.reset();
        CRAFTING_CALCULATION_CACHE_HITS.reset();
        CRAFTING_FALLBACK_REASONS.clear();
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
        EXACT_STORAGE_SNAPSHOT_CACHE_HITS.reset();
        EXACT_STORAGE_SNAPSHOT_CACHE_MISSES.reset();
        EXACT_STORAGE_NESTED_SCANS.reset();
        EXACT_STORAGE_SNAPSHOT_INVALIDATIONS.reset();
        EXACT_STORAGE_TERMINAL_REUSES.reset();
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

}
