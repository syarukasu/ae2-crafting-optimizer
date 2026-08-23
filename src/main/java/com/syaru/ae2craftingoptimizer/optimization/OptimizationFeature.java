package com.syaru.ae2craftingoptimizer.optimization;

import java.util.Set;

/**
 * ACOの実行機能と安全契約。
 *
 * <p>設定項目を増やすだけでは責務境界を保証できないため、Issue #129で
 * 所有権、risk、fallback境界をコード上の列挙へ固定する。
 */
public enum OptimizationFeature {
    ACTIVE_CALCULATION_DEDUPLICATION("active-calculation-deduplication", OptimizationDomain.CRAFTING_PLANNING, OptimizationRisk.MEDIUM, StateOwnership.ACO_CACHE, FallbackBoundary.BEFORE_MUTATION, Set.of(79, 103)),
    COMPLETED_PLAN_CACHE("completed-plan-cache", OptimizationDomain.CRAFTING_PLANNING, OptimizationRisk.HIGH, StateOwnership.ACO_CACHE, FallbackBoundary.BEFORE_OWNERSHIP, Set.of(79, 90, 103)),
    PATTERN_LOOKUP_CACHE("pattern-lookup-cache", OptimizationDomain.PATTERN_PROVIDER, OptimizationRisk.LOW, StateOwnership.ACO_CACHE, FallbackBoundary.BEFORE_MUTATION, Set.of(74, 90)),
    CRAFTABLE_SET_CACHE("craftable-set-cache", OptimizationDomain.PATTERN_PROVIDER, OptimizationRisk.MEDIUM, StateOwnership.ACO_CACHE, FallbackBoundary.BEFORE_MUTATION, Set.of(74, 90)),
    PROVIDER_GENERATION_TRACKING("provider-generation-tracking", OptimizationDomain.PATTERN_PROVIDER, OptimizationRisk.LOW, StateOwnership.ACO_CACHE, FallbackBoundary.BEFORE_MUTATION, Set.of(79, 90, 103)),
    PROVIDER_REFRESH_COALESCING("provider-refresh-coalescing", OptimizationDomain.PATTERN_PROVIDER, OptimizationRisk.MEDIUM, StateOwnership.ACO_CACHE, FallbackBoundary.BEFORE_MUTATION, Set.of(74, 90)),
    CRAFTING_QUERY_MEMOIZATION("crafting-query-memoization", OptimizationDomain.CRAFTING_PLANNING, OptimizationRisk.LOW, StateOwnership.ACO_CACHE, FallbackBoundary.BEFORE_MUTATION, Set.of(79, 103)),
    CANDIDATE_PRUNING("candidate-pruning", OptimizationDomain.CRAFTING_PLANNING, OptimizationRisk.MEDIUM, StateOwnership.AE2, FallbackBoundary.BEFORE_MUTATION, Set.of(79, 103)),
    DETERMINISTIC_MISSING_FAST_FAIL("deterministic-missing-fast-fail", OptimizationDomain.CRAFTING_PLANNING, OptimizationRisk.HIGH, StateOwnership.AE2, FallbackBoundary.BEFORE_OWNERSHIP, Set.of(79, 90, 103, 109)),
    PATTERN_SELECTION_BY_AVAILABILITY("pattern-selection-by-availability", OptimizationDomain.CRAFTING_PLANNING, OptimizationRisk.HIGH, StateOwnership.AE2, FallbackBoundary.BEFORE_MUTATION, Set.of(74, 79, 103)),
    COMPILED_CRAFTING_GRAPH("compiled-crafting-graph", OptimizationDomain.CRAFTING_PLANNING, OptimizationRisk.HIGH, StateOwnership.ACO_CACHE, FallbackBoundary.BEFORE_OWNERSHIP, Set.of(79, 90, 103)),
    AUTHORITATIVE_COMPILED_PLANNER("authoritative-compiled-planner", OptimizationDomain.CRAFTING_PLANNING, OptimizationRisk.HIGH, StateOwnership.ACO_CACHE, FallbackBoundary.BEFORE_OWNERSHIP, Set.of(79, 90, 103)),
    CHECKED_CRAFTING_ARITHMETIC("checked-crafting-arithmetic", OptimizationDomain.CRAFTING_PLANNING, OptimizationRisk.MEDIUM, StateOwnership.AE2, FallbackBoundary.BEFORE_OWNERSHIP, Set.of(79, 98)),
    TWO_STAGE_MISSING_PREVIEW("two-stage-missing-preview", OptimizationDomain.CLIENT_SYNC, OptimizationRisk.HIGH, StateOwnership.AE2, FallbackBoundary.BEFORE_MUTATION, Set.of(30, 109)),
    TERMINAL_UPDATE_COALESCING("terminal-update-coalescing", OptimizationDomain.CLIENT_SYNC, OptimizationRisk.HIGH, StateOwnership.AE2, FallbackBoundary.BEFORE_MUTATION, Set.of(30, 109)),
    TERMINAL_ASYNC_SEARCH("terminal-async-search", OptimizationDomain.CLIENT_SYNC, OptimizationRisk.HIGH, StateOwnership.ACO_CACHE, FallbackBoundary.BEFORE_MUTATION, Set.of(30, 109)),
    TERMINAL_RANGE_SYNC("terminal-range-sync", OptimizationDomain.CLIENT_SYNC, OptimizationRisk.HIGH, StateOwnership.AE2, FallbackBoundary.BEFORE_MUTATION, Set.of(30, 109)),
    SCROLLBAR_RELEASE_SAFETY("scrollbar-release-safety", OptimizationDomain.CLIENT_SYNC, OptimizationRisk.LOW, StateOwnership.AE2, FallbackBoundary.BEFORE_MUTATION, Set.of(30)),
    GRID_TICK_BUDGET("grid-tick-budget", OptimizationDomain.NETWORK_TOPOLOGY, OptimizationRisk.HIGH, StateOwnership.AE2, FallbackBoundary.BEFORE_MUTATION, Set.of(51, 74)),
    P2P_TOPOLOGY_DEDUPLICATION("p2p-topology-deduplication", OptimizationDomain.NETWORK_TOPOLOGY, OptimizationRisk.MEDIUM, StateOwnership.ACO_CACHE, FallbackBoundary.BEFORE_MUTATION, Set.of(74)),
    NETWORK_UPDATE_COALESCING("network-update-coalescing", OptimizationDomain.NETWORK_TOPOLOGY, OptimizationRisk.HIGH, StateOwnership.AE2, FallbackBoundary.BEFORE_MUTATION, Set.of(74, 109)),
    ADJACENT_CAPABILITY_CACHE("adjacent-capability-cache", OptimizationDomain.STORAGE_IO, OptimizationRisk.MEDIUM, StateOwnership.ACO_CACHE, FallbackBoundary.BEFORE_MUTATION, Set.of(74, 109)),
    NEGATIVE_TRANSFER_CACHE("negative-transfer-cache", OptimizationDomain.STORAGE_IO, OptimizationRisk.MEDIUM, StateOwnership.ACO_CACHE, FallbackBoundary.BEFORE_MUTATION, Set.of(74, 109)),
    INCREMENTAL_IO_PORT("incremental-io-port", OptimizationDomain.STORAGE_IO, OptimizationRisk.MEDIUM, StateOwnership.AE2, FallbackBoundary.BEFORE_MUTATION, Set.of(74)),
    IO_BUS_OPERATION_LIMIT("io-bus-operation-limit", OptimizationDomain.STORAGE_IO, OptimizationRisk.HIGH, StateOwnership.AE2, FallbackBoundary.BEFORE_MUTATION, Set.of(74, 109)),
    EXPORT_CRAFT_REQUEST_BACKOFF("export-craft-request-backoff", OptimizationDomain.STORAGE_IO, OptimizationRisk.MEDIUM, StateOwnership.ACO_CACHE, FallbackBoundary.BEFORE_MUTATION, Set.of(74, 109)),
    IMPORT_LAST_SLOT_CACHE("import-last-slot-cache", OptimizationDomain.STORAGE_IO, OptimizationRisk.HIGH, StateOwnership.ACO_CACHE, FallbackBoundary.BEFORE_MUTATION, Set.of(74, 109)),
    EXPORT_CANDIDATE_CACHE("export-candidate-cache", OptimizationDomain.STORAGE_IO, OptimizationRisk.MEDIUM, StateOwnership.ACO_CACHE, FallbackBoundary.BEFORE_MUTATION, Set.of(74, 109)),
    BUS_SEARCH_CACHE("bus-search-cache", OptimizationDomain.STORAGE_IO, OptimizationRisk.HIGH, StateOwnership.ACO_CACHE, FallbackBoundary.BEFORE_MUTATION, Set.of(74, 109)),
    FLUID_PATTERN_FAST_PATH("fluid-pattern-fast-path", OptimizationDomain.STORAGE_IO, OptimizationRisk.HIGH, StateOwnership.AE2, FallbackBoundary.BEFORE_MUTATION, Set.of(74, 109)),
    STORAGE_WATCHER_THROTTLE("storage-watcher-throttle", OptimizationDomain.CLIENT_SYNC, OptimizationRisk.HIGH, StateOwnership.AE2, FallbackBoundary.BEFORE_MUTATION, Set.of(30, 109)),
    CRAFTING_EXECUTION_BUDGET("crafting-execution-budget", OptimizationDomain.CRAFTING_EXECUTION, OptimizationRisk.MEDIUM, StateOwnership.AE2, FallbackBoundary.BEFORE_MUTATION, Set.of(74, 102)),
    TRANSACTIONAL_BATCHING("transactional-batching", OptimizationDomain.CRAFTING_EXECUTION, OptimizationRisk.HIGH, StateOwnership.ACO_TRANSACTION, FallbackBoundary.NEVER_AFTER_OWNERSHIP, Set.of(102, 115, 118, 119, 125)),
    INSTANT_PATTERN_DISPATCH("instant-pattern-dispatch", OptimizationDomain.CRAFTING_EXECUTION, OptimizationRisk.HIGH, StateOwnership.ACO_TRANSACTION, FallbackBoundary.NEVER_AFTER_OWNERSHIP, Set.of(102, 115, 125)),
    FAIR_JOB_SCHEDULER("fair-job-scheduler", OptimizationDomain.CRAFTING_EXECUTION, OptimizationRisk.HIGH, StateOwnership.ACO_TRANSACTION, FallbackBoundary.NEVER_AFTER_OWNERSHIP, Set.of(102, 115, 125)),
    BIG_INTEGER_BACKEND("big-integer-backend", OptimizationDomain.BIG_INTEGER, OptimizationRisk.HIGH, StateOwnership.ACO_CACHE, FallbackBoundary.BEFORE_OWNERSHIP, Set.of(79, 87, 90, 93, 98, 101, 103, 109)),
    LONG_ROOT_AMOUNTS("long-root-amounts", OptimizationDomain.BIG_INTEGER, OptimizationRisk.HIGH, StateOwnership.AE2, FallbackBoundary.BEFORE_OWNERSHIP, Set.of(79, 98, 109)),
    ATOMIC_BIG_CAPACITY_PLANS("atomic-big-capacity-plans", OptimizationDomain.BIG_INTEGER, OptimizationRisk.HIGH, StateOwnership.ACO_TRANSACTION, FallbackBoundary.NEVER_AFTER_OWNERSHIP, Set.of(98, 115, 118, 125)),
    EXACT_INVENTORY_SNAPSHOT("exact-inventory-snapshot", OptimizationDomain.BIG_INTEGER, OptimizationRisk.HIGH, StateOwnership.ACO_CACHE, FallbackBoundary.BEFORE_OWNERSHIP, Set.of(87, 93, 101, 109)),
    BIG_INTEGER_GAMEPLAY_EXECUTION("big-integer-gameplay-execution", OptimizationDomain.BIG_INTEGER, OptimizationRisk.HIGH, StateOwnership.ACO_TRANSACTION, FallbackBoundary.NEVER_AFTER_OWNERSHIP, Set.of(98, 101, 109, 115, 118, 119, 125)),
    EXACT_VECTOR_CRAFTING("exact-vector-crafting", OptimizationDomain.BIG_INTEGER, OptimizationRisk.HIGH, StateOwnership.ACO_TRANSACTION, FallbackBoundary.NEVER_AFTER_OWNERSHIP, Set.of(115, 118, 119, 125)),
    RECIPE_INTENT_BRIDGE("recipe-intent-bridge", OptimizationDomain.OPTIONAL_INTEGRATION, OptimizationRisk.MEDIUM, StateOwnership.EXTERNAL_ADDON, FallbackBoundary.BEFORE_MUTATION, Set.of(74)),
    NATIVE_MACHINE_BATCH("native-machine-batch", OptimizationDomain.OPTIONAL_INTEGRATION, OptimizationRisk.HIGH, StateOwnership.EXTERNAL_ADDON, FallbackBoundary.NEVER_AFTER_OWNERSHIP, Set.of(51, 74, 115, 125)),
    CIRCUIT_CUTTER_RECIPE_CACHE("circuit-cutter-recipe-cache", OptimizationDomain.OPTIONAL_INTEGRATION, OptimizationRisk.MEDIUM, StateOwnership.EXTERNAL_ADDON, FallbackBoundary.BEFORE_MUTATION, Set.of(51, 74)),
    ADDON_MACHINE_CACHE("addon-machine-cache", OptimizationDomain.OPTIONAL_INTEGRATION, OptimizationRisk.MEDIUM, StateOwnership.EXTERNAL_ADDON, FallbackBoundary.BEFORE_MUTATION, Set.of(51, 74)),
    APPLIED_E_COMPATIBILITY("applied-e-compatibility", OptimizationDomain.OPTIONAL_INTEGRATION, OptimizationRisk.MEDIUM, StateOwnership.EXTERNAL_ADDON, FallbackBoundary.BEFORE_OWNERSHIP, Set.of(51, 103)),
    ADVANCED_AE_BIG_PROFILE("advanced-ae-big-profile", OptimizationDomain.OPTIONAL_INTEGRATION, OptimizationRisk.HIGH, StateOwnership.EXTERNAL_ADDON, FallbackBoundary.BEFORE_OWNERSHIP, Set.of(51, 98, 120)),
    EXTERNAL_BIG_PROFILE("external-big-profile", OptimizationDomain.OPTIONAL_INTEGRATION, OptimizationRisk.HIGH, StateOwnership.EXTERNAL_ADDON, FallbackBoundary.BEFORE_OWNERSHIP, Set.of(98, 109));

    private final String id;
    private final OptimizationDomain domain;
    private final OptimizationRisk risk;
    private final StateOwnership ownership;
    private final FallbackBoundary fallbackBoundary;
    private final Set<Integer> regressionIssues;

    OptimizationFeature(
            String id,
            OptimizationDomain domain,
            OptimizationRisk risk,
            StateOwnership ownership,
            FallbackBoundary fallbackBoundary,
            Set<Integer> regressionIssues) {
        this.id = id;
        this.domain = domain;
        this.risk = risk;
        this.ownership = ownership;
        this.fallbackBoundary = fallbackBoundary;
        this.regressionIssues = Set.copyOf(regressionIssues);
    }

    public String id() {
        return id;
    }

    public OptimizationDomain domain() {
        return domain;
    }

    public OptimizationRisk risk() {
        return risk;
    }

    public StateOwnership ownership() {
        return ownership;
    }

    public FallbackBoundary fallbackBoundary() {
        return fallbackBoundary;
    }

    public Set<Integer> regressionIssues() {
        return regressionIssues;
    }

    /**
     * Config互換キーと稼働中の実装を混同しないための状態。
     *
     * <p>Issue #30/#74/#109で停止させた可変在庫・GUI・Grid Tick経路は、
     * 専用の原子性試験が整うまでConfig値にかかわらず再登録しない。
     */
    public OptimizationImplementationStatus implementationStatus() {
        return switch (this) {
            case CRAFTABLE_SET_CACHE,
                    TWO_STAGE_MISSING_PREVIEW,
                    TERMINAL_UPDATE_COALESCING,
                    TERMINAL_ASYNC_SEARCH,
                    TERMINAL_RANGE_SYNC,
                    GRID_TICK_BUDGET,
                    NETWORK_UPDATE_COALESCING,
                    ADJACENT_CAPABILITY_CACHE,
                    NEGATIVE_TRANSFER_CACHE,
                    INCREMENTAL_IO_PORT,
                    IO_BUS_OPERATION_LIMIT,
                    IMPORT_LAST_SLOT_CACHE,
                    EXPORT_CANDIDATE_CACHE,
                    BUS_SEARCH_CACHE,
                    STORAGE_WATCHER_THROTTLE ->
                    OptimizationImplementationStatus.COMPATIBILITY_NOOP;
            default -> OptimizationImplementationStatus.ACTIVE;
        };
    }

    /**
     * この機能が保持する派生状態の失効契約。
     *
     * <p>純粋な算術hookは空集合でよい。ACO cacheまたはtransactionを所有する機能は、
     * 少なくとも一つの正本イベントで必ず閉じる。
     */
    public Set<OptimizationInvalidation> invalidationTriggers() {
        return switch (this) {
            case ACTIVE_CALCULATION_DEDUPLICATION, COMPLETED_PLAN_CACHE -> Set.of(
                    OptimizationInvalidation.PROVIDER_GENERATION,
                    OptimizationInvalidation.STORAGE_GENERATION,
                    OptimizationInvalidation.PLANNING_COMPLETION,
                    OptimizationInvalidation.SERVER_LIFECYCLE);
            case PATTERN_LOOKUP_CACHE,
                    CRAFTABLE_SET_CACHE,
                    PROVIDER_GENERATION_TRACKING,
                    PROVIDER_REFRESH_COALESCING,
                    COMPILED_CRAFTING_GRAPH,
                    AUTHORITATIVE_COMPILED_PLANNER -> Set.of(
                    OptimizationInvalidation.PROVIDER_GENERATION,
                    OptimizationInvalidation.RESOURCE_RELOAD,
                    OptimizationInvalidation.SERVER_LIFECYCLE);
            case CRAFTING_QUERY_MEMOIZATION -> Set.of(OptimizationInvalidation.PLANNING_COMPLETION);
            case TERMINAL_UPDATE_COALESCING,
                    TERMINAL_ASYNC_SEARCH,
                    TERMINAL_RANGE_SYNC,
                    STORAGE_WATCHER_THROTTLE -> Set.of(
                    OptimizationInvalidation.STORAGE_GENERATION,
                    OptimizationInvalidation.TOPOLOGY_GENERATION,
                    OptimizationInvalidation.SERVER_LIFECYCLE);
            case GRID_TICK_BUDGET,
                    ADJACENT_CAPABILITY_CACHE,
                    NEGATIVE_TRANSFER_CACHE,
                    INCREMENTAL_IO_PORT,
                    IO_BUS_OPERATION_LIMIT,
                    EXPORT_CRAFT_REQUEST_BACKOFF,
                    IMPORT_LAST_SLOT_CACHE,
                    EXPORT_CANDIDATE_CACHE,
                    BUS_SEARCH_CACHE -> Set.of(
                    OptimizationInvalidation.SERVER_TICK,
                    OptimizationInvalidation.STORAGE_GENERATION,
                    OptimizationInvalidation.SERVER_LIFECYCLE);
            case P2P_TOPOLOGY_DEDUPLICATION, NETWORK_UPDATE_COALESCING -> Set.of(
                    OptimizationInvalidation.TOPOLOGY_GENERATION,
                    OptimizationInvalidation.SERVER_LIFECYCLE);
            case TRANSACTIONAL_BATCHING,
                    INSTANT_PATTERN_DISPATCH,
                    FAIR_JOB_SCHEDULER,
                    ATOMIC_BIG_CAPACITY_PLANS,
                    BIG_INTEGER_GAMEPLAY_EXECUTION,
                    EXACT_VECTOR_CRAFTING -> Set.of(
                    OptimizationInvalidation.TRANSACTION_TERMINAL_STATE,
                    OptimizationInvalidation.SERVER_LIFECYCLE);
            case BIG_INTEGER_BACKEND, EXACT_INVENTORY_SNAPSHOT -> Set.of(
                    OptimizationInvalidation.STORAGE_GENERATION,
                    OptimizationInvalidation.PROVIDER_GENERATION,
                    OptimizationInvalidation.SERVER_LIFECYCLE);
            case RECIPE_INTENT_BRIDGE,
                    NATIVE_MACHINE_BATCH,
                    CIRCUIT_CUTTER_RECIPE_CACHE,
                    ADDON_MACHINE_CACHE,
                    APPLIED_E_COMPATIBILITY,
                    ADVANCED_AE_BIG_PROFILE,
                    EXTERNAL_BIG_PROFILE -> Set.of(
                    OptimizationInvalidation.EXTERNAL_OWNER,
                    OptimizationInvalidation.RESOURCE_RELOAD,
                    OptimizationInvalidation.SERVER_LIFECYCLE);
            default -> Set.of();
        };
    }
}
