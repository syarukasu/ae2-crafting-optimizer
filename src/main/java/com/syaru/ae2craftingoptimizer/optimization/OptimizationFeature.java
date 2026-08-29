package com.syaru.ae2craftingoptimizer.optimization;

import java.util.Set;

/**
 * ACOが実際に提供する機能と、その所有権・失効契約。
 *
 * <p>Issue #164のclean-breakにより、退役Configや未登録実装は列挙しない。
 * このenumに存在する機能は、ConfigまたはMixinの実入口を必ず持つ。</p>
 */
public enum OptimizationFeature {
    ACTIVE_CALCULATION_DEDUPLICATION("active-calculation-deduplication", OptimizationDomain.CRAFTING_PLANNING, OptimizationRisk.MEDIUM, StateOwnership.ACO_CACHE, FallbackBoundary.BEFORE_MUTATION, Set.of(79, 103)),
    COMPLETED_PLAN_CACHE("completed-plan-cache", OptimizationDomain.CRAFTING_PLANNING, OptimizationRisk.HIGH, StateOwnership.ACO_CACHE, FallbackBoundary.BEFORE_OWNERSHIP, Set.of(79, 90, 103)),
    PATTERN_LOOKUP_CACHE("pattern-lookup-cache", OptimizationDomain.PATTERN_PROVIDER, OptimizationRisk.LOW, StateOwnership.ACO_CACHE, FallbackBoundary.BEFORE_MUTATION, Set.of(74, 90)),
    PROVIDER_GENERATION_TRACKING("provider-generation-tracking", OptimizationDomain.PATTERN_PROVIDER, OptimizationRisk.LOW, StateOwnership.ACO_CACHE, FallbackBoundary.BEFORE_MUTATION, Set.of(79, 90, 103)),
    PROVIDER_REFRESH_COALESCING("provider-refresh-coalescing", OptimizationDomain.PATTERN_PROVIDER, OptimizationRisk.MEDIUM, StateOwnership.ACO_CACHE, FallbackBoundary.BEFORE_MUTATION, Set.of(74, 90)),
    CRAFTING_QUERY_MEMOIZATION("crafting-query-memoization", OptimizationDomain.CRAFTING_PLANNING, OptimizationRisk.LOW, StateOwnership.ACO_CACHE, FallbackBoundary.BEFORE_MUTATION, Set.of(79, 103)),
    CANDIDATE_PRUNING("candidate-pruning", OptimizationDomain.CRAFTING_PLANNING, OptimizationRisk.MEDIUM, StateOwnership.AE2, FallbackBoundary.BEFORE_MUTATION, Set.of(79, 103)),
    COMPILED_CRAFTING_GRAPH("compiled-crafting-graph", OptimizationDomain.CRAFTING_PLANNING, OptimizationRisk.HIGH, StateOwnership.ACO_CACHE, FallbackBoundary.BEFORE_OWNERSHIP, Set.of(79, 90, 103, 156)),
    AUTHORITATIVE_COMPILED_PLANNER("authoritative-compiled-planner", OptimizationDomain.CRAFTING_PLANNING, OptimizationRisk.HIGH, StateOwnership.ACO_CACHE, FallbackBoundary.BEFORE_OWNERSHIP, Set.of(79, 90, 103, 156)),
    CHECKED_CRAFTING_ARITHMETIC("checked-crafting-arithmetic", OptimizationDomain.CRAFTING_PLANNING, OptimizationRisk.MEDIUM, StateOwnership.AE2, FallbackBoundary.BEFORE_OWNERSHIP, Set.of(79, 98)),
    CRAFTING_EXECUTION_BUDGET("crafting-execution-budget", OptimizationDomain.CRAFTING_EXECUTION, OptimizationRisk.MEDIUM, StateOwnership.AE2, FallbackBoundary.BEFORE_MUTATION, Set.of(74, 102, 161)),
    TRANSACTIONAL_BATCHING("transactional-batching", OptimizationDomain.CRAFTING_EXECUTION, OptimizationRisk.HIGH, StateOwnership.ACO_TRANSACTION, FallbackBoundary.NEVER_AFTER_OWNERSHIP, Set.of(102, 115, 118, 119, 125)),
    INSTANT_PATTERN_DISPATCH("instant-pattern-dispatch", OptimizationDomain.CRAFTING_EXECUTION, OptimizationRisk.HIGH, StateOwnership.ACO_TRANSACTION, FallbackBoundary.NEVER_AFTER_OWNERSHIP, Set.of(102, 115, 125, 161)),
    BIG_INTEGER_BACKEND("big-integer-backend", OptimizationDomain.BIG_INTEGER, OptimizationRisk.HIGH, StateOwnership.ACO_CACHE, FallbackBoundary.BEFORE_OWNERSHIP, Set.of(79, 87, 90, 93, 98, 101, 103, 109)),
    LONG_ROOT_AMOUNTS("long-root-amounts", OptimizationDomain.BIG_INTEGER, OptimizationRisk.HIGH, StateOwnership.AE2, FallbackBoundary.BEFORE_OWNERSHIP, Set.of(79, 98, 109)),
    ATOMIC_BIG_CAPACITY_PLANS("atomic-big-capacity-plans", OptimizationDomain.BIG_INTEGER, OptimizationRisk.HIGH, StateOwnership.ACO_CACHE, FallbackBoundary.BEFORE_OWNERSHIP, Set.of(98, 115, 118, 125)),
    EXACT_INVENTORY_SNAPSHOT("exact-inventory-snapshot", OptimizationDomain.BIG_INTEGER, OptimizationRisk.HIGH, StateOwnership.ACO_CACHE, FallbackBoundary.BEFORE_OWNERSHIP, Set.of(87, 93, 101, 109, 148, 153)),
    BIG_INTEGER_GAMEPLAY_EXECUTION("big-integer-gameplay-execution", OptimizationDomain.BIG_INTEGER, OptimizationRisk.HIGH, StateOwnership.ACO_TRANSACTION, FallbackBoundary.NEVER_AFTER_OWNERSHIP, Set.of(98, 101, 109, 115, 118, 119, 125)),
    EXACT_VECTOR_CRAFTING("exact-vector-crafting", OptimizationDomain.BIG_INTEGER, OptimizationRisk.HIGH, StateOwnership.ACO_TRANSACTION, FallbackBoundary.NEVER_AFTER_OWNERSHIP, Set.of(115, 118, 119, 125)),
    RECIPE_INTENT_BRIDGE("recipe-intent-bridge", OptimizationDomain.OPTIONAL_INTEGRATION, OptimizationRisk.MEDIUM, StateOwnership.ACO_CACHE, FallbackBoundary.BEFORE_MUTATION, Set.of(51, 74)),
    CIRCUIT_CUTTER_RECIPE_CACHE("circuit-cutter-recipe-cache", OptimizationDomain.OPTIONAL_INTEGRATION, OptimizationRisk.MEDIUM, StateOwnership.ACO_CACHE, FallbackBoundary.BEFORE_MUTATION, Set.of(51, 74)),
    ADDON_MACHINE_CACHE("addon-machine-cache", OptimizationDomain.OPTIONAL_INTEGRATION, OptimizationRisk.MEDIUM, StateOwnership.ACO_CACHE, FallbackBoundary.BEFORE_MUTATION, Set.of(51, 74)),
    APPLIED_E_COMPATIBILITY("applied-e-compatibility", OptimizationDomain.OPTIONAL_INTEGRATION, OptimizationRisk.MEDIUM, StateOwnership.EXTERNAL_ADDON, FallbackBoundary.BEFORE_OWNERSHIP, Set.of(51, 103));

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

    /** ACOが保持する派生状態を破棄する正本イベント。 */
    public Set<OptimizationInvalidation> invalidationTriggers() {
        return switch (this) {
            case ACTIVE_CALCULATION_DEDUPLICATION, COMPLETED_PLAN_CACHE -> Set.of(
                    OptimizationInvalidation.PROVIDER_GENERATION,
                    OptimizationInvalidation.STORAGE_GENERATION,
                    OptimizationInvalidation.PLANNING_COMPLETION,
                    OptimizationInvalidation.SERVER_LIFECYCLE);
            case PATTERN_LOOKUP_CACHE,
                    PROVIDER_GENERATION_TRACKING,
                    PROVIDER_REFRESH_COALESCING,
                    COMPILED_CRAFTING_GRAPH,
                    AUTHORITATIVE_COMPILED_PLANNER -> Set.of(
                    OptimizationInvalidation.PROVIDER_GENERATION,
                    OptimizationInvalidation.RESOURCE_RELOAD,
                    OptimizationInvalidation.SERVER_LIFECYCLE);
            case CRAFTING_QUERY_MEMOIZATION -> Set.of(
                    OptimizationInvalidation.PLANNING_COMPLETION);
            case TRANSACTIONAL_BATCHING,
                    INSTANT_PATTERN_DISPATCH,
                    BIG_INTEGER_GAMEPLAY_EXECUTION,
                    EXACT_VECTOR_CRAFTING -> Set.of(
                    OptimizationInvalidation.TRANSACTION_TERMINAL_STATE,
                    OptimizationInvalidation.SERVER_LIFECYCLE);
            case BIG_INTEGER_BACKEND,
                    ATOMIC_BIG_CAPACITY_PLANS,
                    EXACT_INVENTORY_SNAPSHOT -> Set.of(
                    OptimizationInvalidation.STORAGE_GENERATION,
                    OptimizationInvalidation.PROVIDER_GENERATION,
                    OptimizationInvalidation.SERVER_LIFECYCLE);
            case RECIPE_INTENT_BRIDGE,
                    CIRCUIT_CUTTER_RECIPE_CACHE,
                    ADDON_MACHINE_CACHE -> Set.of(
                    OptimizationInvalidation.EXTERNAL_OWNER,
                    OptimizationInvalidation.RESOURCE_RELOAD,
                    OptimizationInvalidation.SERVER_LIFECYCLE);
            default -> Set.of();
        };
    }
}
