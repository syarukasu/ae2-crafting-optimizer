package com.syaru.ae2craftingoptimizer.config;

import com.syaru.ae2craftingoptimizer.engine.BigCountMath;
import com.syaru.ae2craftingoptimizer.optimization.OptimizationDomain;
import com.syaru.ae2craftingoptimizer.optimization.OptimizationFeature;
import com.syaru.ae2craftingoptimizer.optimization.OptimizationFeatureGate;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * ACOが現在所有する機能だけを公開するConfig schema。
 *
 * <p>Issue #164のclean-breakにより、削除済み機能の互換キーと外部CPU固有の実行設定は保持しない。
 * master、domain、個別設定の順序は{@link OptimizationFeatureGate}へ集約する。</p>
 */
public final class ACOConfig {
    /** int加算で番兵値へ到達させない、ACO実行窓の最大値。 */
    public static final int MAX_SAFE_EFFECTIVE_COPROCESSORS = Integer.MAX_VALUE - 1;
    /** 巨大CPUでも一tickを独占させないための初期実行予算。 */
    public static final int DEFAULT_EFFECTIVE_COPROCESSORS_PER_CPU = 264_192;

    /** 一秒を構成するMinecraft server tick数。 */
    private static final int TICKS_PER_SECOND = 20;
    /** Configで許す一つのmain-thread処理予算。50 ms未満へ収める。 */
    private static final int MAXIMUM_TICK_BUDGET_MILLIS = 45;
    /** 配列・cache・一回の実行窓へ共通で課す要素数上限。 */
    private static final int MAXIMUM_BOUNDED_ENTRIES = 1_048_576;
    /** Authoritative採用前に同一世代のRoot Programへ要求するShadow一致数。 */
    private static final int DEFAULT_AUTHORITATIVE_SHADOW_MATCHES = 64;
    /** 設定ミスで永久に採用されない状態を作らないための一致数上限。 */
    private static final int MAXIMUM_AUTHORITATIVE_SHADOW_MATCHES = 1_048_576;
    /** MiB設定をbyteへ変換する二進単位。 */
    private static final long BYTES_PER_MEBIBYTE = 1024L * 1024L;

    private static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue ENABLE_OPTIMIZER;
    private static final ModConfigSpec.BooleanValue ENABLE_PATTERN_PROVIDER_DOMAIN;
    private static final ModConfigSpec.BooleanValue ENABLE_CRAFTING_PLANNING_DOMAIN;
    private static final ModConfigSpec.BooleanValue ENABLE_CRAFTING_EXECUTION_DOMAIN;
    private static final ModConfigSpec.BooleanValue ENABLE_BIG_INTEGER_DOMAIN;
    private static final ModConfigSpec.BooleanValue ENABLE_OPTIONAL_INTEGRATION_DOMAIN;

    private static final ModConfigSpec.BooleanValue DEDUPLICATE_ACTIVE_CRAFTING_CALCULATIONS;
    private static final ModConfigSpec.IntValue ACTIVE_CALCULATION_DEDUPLICATION_WINDOW_TICKS;
    private static final ModConfigSpec.BooleanValue LOG_CRAFTING_CALCULATION_DEDUPLICATION;
    private static final ModConfigSpec.BooleanValue CACHE_COMPLETED_CRAFTING_PLANS;
    private static final ModConfigSpec.BooleanValue CACHE_SUCCESSFUL_COMPLETED_CRAFTING_PLANS;
    private static final ModConfigSpec.IntValue COMPLETED_CRAFTING_PLAN_CACHE_SIZE;
    private static final ModConfigSpec.IntValue COMPLETED_CRAFTING_PLAN_CACHE_TTL_TICKS;
    private static final ModConfigSpec.BooleanValue CACHE_PATTERN_LOOKUPS;
    private static final ModConfigSpec.IntValue PATTERN_LOOKUP_CACHE_SIZE;
    private static final ModConfigSpec.BooleanValue LOG_PATTERN_LOOKUP_CACHE;
    private static final ModConfigSpec.BooleanValue PRUNE_INVALID_CRAFTING_CANDIDATES;
    private static final ModConfigSpec.BooleanValue MEMOIZE_CRAFTING_CALCULATION_QUERIES;
    private static final ModConfigSpec.BooleanValue COALESCE_CRAFTING_PROVIDER_REFRESHES;
    private static final ModConfigSpec.BooleanValue TRACK_PROVIDER_PATTERN_GENERATIONS;

    private static final ModConfigSpec.BooleanValue THROTTLE_CRAFTING_EXECUTION;
    private static final ModConfigSpec.IntValue MAX_EFFECTIVE_COPROCESSORS_PER_CPU;
    private static final ModConfigSpec.BooleanValue ADAPTIVE_CRAFTING_EXECUTION_BUDGET;
    private static final ModConfigSpec.IntValue TARGET_CRAFTING_EXECUTION_MILLIS;
    private static final ModConfigSpec.IntValue MINIMUM_ADAPTIVE_COPROCESSORS_PER_CPU;
    private static final ModConfigSpec.BooleanValue SHARED_CRAFTING_EXECUTION_BUDGET;
    private static final ModConfigSpec.IntValue SHARED_CRAFTING_EXECUTION_MILLIS_PER_GRID;
    private static final ModConfigSpec.IntValue MINIMUM_SHARED_OPERATIONS_PER_CPU;
    private static final ModConfigSpec.BooleanValue LOG_CRAFTING_EXECUTION_THROTTLING;
    private static final ModConfigSpec.BooleanValue THROTTLE_NEO_ECO_AE_EXECUTION;
    private static final ModConfigSpec.BooleanValue ENABLE_INSTANT_PATTERN_DISPATCH;
    private static final ModConfigSpec.IntValue INSTANT_PATTERN_DISPATCH_TIME_BUDGET_MILLIS;
    private static final ModConfigSpec.IntValue INSTANT_PATTERN_DISPATCH_PROBE_OPERATIONS;
    private static final ModConfigSpec.IntValue INSTANT_PATTERN_DISPATCH_MAXIMUM_UNMEASURED_WAVE_OPERATIONS;
    private static final ModConfigSpec.IntValue INSTANT_PATTERN_DISPATCH_MAXIMUM_WAVE_OPERATIONS;
    private static final ModConfigSpec.IntValue MAX_INSTANT_PATTERN_DISPATCH_TRANSACTIONS;
    private static final ModConfigSpec.BooleanValue ENABLE_TRANSACTIONAL_BATCHING_V2;
    private static final ModConfigSpec.BooleanValue PERSIST_BATCH_TRANSACTION_JOURNAL;
    private static final ModConfigSpec.IntValue BATCH_TRANSACTION_JOURNAL_MAXIMUM_ENTRIES;
    private static final ModConfigSpec.IntValue BATCH_TRANSACTION_RECONCILIATION_INTERVAL_TICKS;
    private static final ModConfigSpec.IntValue MAXIMUM_BATCH_EXECUTIONS;

    private static final ModConfigSpec.BooleanValue ENABLE_APPLIED_E_COMPATIBILITY;
    private static final ModConfigSpec.BooleanValue FORCE_AE2_PLANNER_FOR_APPLIED_E_PATTERNS;
    private static final ModConfigSpec.BooleanValue TREAT_APPLIED_E_PROVIDER_AS_DYNAMIC;

    private static final ModConfigSpec.BooleanValue ENABLE_ADDON_MACHINE_OPTIMIZATIONS;
    private static final ModConfigSpec.BooleanValue CACHE_CIRCUIT_CUTTER_RECIPES;
    private static final ModConfigSpec.BooleanValue CACHE_CIRCUIT_CUTTER_NEGATIVE_RESULTS;
    private static final ModConfigSpec.IntValue CIRCUIT_CUTTER_RECIPE_CACHE_SIZE;
    private static final ModConfigSpec.BooleanValue CACHE_REACTION_CHAMBER_RECIPE;
    private static final ModConfigSpec.BooleanValue CACHE_AE2_OVERCLOCK_REFLECTION;
    private static final ModConfigSpec.BooleanValue USE_AE2_OVERCLOCK_METHOD_HANDLES;
    private static final ModConfigSpec.BooleanValue CACHE_AE2_OVERCLOCK_UPGRADE_COUNTS;
    private static final ModConfigSpec.BooleanValue CACHE_ASSEMBLER_MATRIX_THREAD_COUNTS;
    private static final ModConfigSpec.BooleanValue CACHE_ASSEMBLER_MATRIX_BUSY_COUNT;
    private static final ModConfigSpec.BooleanValue COALESCE_ASSEMBLER_MATRIX_STATUS_UPDATES;
    private static final ModConfigSpec.BooleanValue CACHE_ASSEMBLER_MATRIX_ROUTING;

    private static final ModConfigSpec.BooleanValue ENABLE_RECIPE_INTENT_BRIDGE;
    private static final ModConfigSpec.BooleanValue CAPTURE_PATTERN_PROVIDER_RECIPE_INTENTS;
    private static final ModConfigSpec.IntValue RECIPE_INTENT_TTL_TICKS;
    private static final ModConfigSpec.IntValue MAXIMUM_RECIPE_INTENT_ENTRIES;
    private static final ModConfigSpec.BooleanValue ENABLE_GTCEU_RECIPE_INTENT_FAST_PATH;
    private static final ModConfigSpec.IntValue GTCEU_RECIPE_INTENT_MAXIMUM_CANDIDATES;
    private static final ModConfigSpec.IntValue GTCEU_RECIPE_INTENT_INDEX_CACHE_SIZE;
    private static final ModConfigSpec.IntValue GTCEU_RECIPE_INTENT_SEARCH_RADIUS;
    private static final ModConfigSpec.IntValue GTCEU_RECIPE_INTENT_NEARBY_MAXIMUM_ENTRIES;
    private static final ModConfigSpec.BooleanValue LOG_GTCEU_RECIPE_INTENT_FAST_PATH;
    private static final ModConfigSpec.BooleanValue ENABLE_MEKANISM_RECIPE_INTENT_FAST_PATH;
    private static final ModConfigSpec.IntValue MEKANISM_RECIPE_INTENT_MAXIMUM_CANDIDATES;
    private static final ModConfigSpec.IntValue MEKANISM_RECIPE_INTENT_INDEX_CACHE_SIZE;
    private static final ModConfigSpec.BooleanValue CACHE_RESOLVED_RECIPE_INTENTS;
    private static final ModConfigSpec.IntValue RESOLVED_RECIPE_INTENT_CACHE_SIZE;
    private static final ModConfigSpec.BooleanValue LOG_MEKANISM_RECIPE_INTENT_FAST_PATH;
    private static final ModConfigSpec.BooleanValue LOG_CAPTURED_RECIPE_INTENTS;
    private static final ModConfigSpec.BooleanValue LOG_RECIPE_INTENT_REGISTRY_EVICTIONS;

    private static final ModConfigSpec.BooleanValue ENABLE_LONG_ROOT_CRAFT_AMOUNTS;
    private static final ModConfigSpec.BooleanValue ENABLE_CRAFTING_ENGINE_SHADOW_MODE;
    private static final ModConfigSpec.BooleanValue LOG_CRAFTING_ENGINE_SHADOW_MISMATCHES;
    private static final ModConfigSpec.IntValue CRAFTING_ENGINE_SHADOW_MAXIMUM_PATTERNS;
    private static final ModConfigSpec.IntValue AUTHORITATIVE_MINIMUM_SHADOW_MATCHES;
    private static final ModConfigSpec.BooleanValue REQUIRE_WIDE_PLAN_SHADOW_QUALIFICATION;
    private static final ModConfigSpec.BooleanValue ENABLE_COMPILED_CRAFTING_GRAPH;
    private static final ModConfigSpec.BooleanValue ENABLE_AUTHORITATIVE_COMPILED_PLANNER;
    private static final ModConfigSpec.BooleanValue ENABLE_PROOF_QUALIFIED_LONG_PLANS;
    private static final ModConfigSpec.BooleanValue ENABLE_CHECKED_AE2_CRAFTING_ARITHMETIC;
    private static final ModConfigSpec.BooleanValue ENABLE_BIG_INTEGER_CRAFTING_BACKEND;
    private static final ModConfigSpec.BooleanValue ENABLE_EXACT_BIG_INTEGER_INVENTORY_SNAPSHOTS;
    private static final ModConfigSpec.BooleanValue RETRY_INCOMPLETE_CRAFTING_GRAPH_SNAPSHOT;
    private static final ModConfigSpec.BooleanValue LOG_WIDE_PLAN_SUBMISSION_DECLINES;
    private static final ModConfigSpec.BooleanValue ENABLE_ATOMIC_BIG_CAPACITY_PLANS;
    private static final ModConfigSpec.BooleanValue ENABLE_BIG_INTEGER_GAMEPLAY_EXECUTION;
    private static final ModConfigSpec.IntValue BIG_INTEGER_MAXIMUM_BITS;
    private static final ModConfigSpec.IntValue BIG_INTEGER_EXECUTION_WINDOW;
    private static final ModConfigSpec.IntValue BIG_INTEGER_STATUS_PAGE_ENTRIES;
    private static final ModConfigSpec.IntValue BIG_INTEGER_RUNTIME_COUNT_BUDGET_MIB;

    private static final ModConfigSpec.BooleanValue ENABLE_EXACT_VECTOR_CRAFTING;
    private static final ModConfigSpec.BooleanValue ENABLE_EXACT_PHYSICAL_EXECUTION;
    private static final ModConfigSpec.IntValue EXACT_VECTOR_MAXIMUM_PATTERN_NODES;
    private static final ModConfigSpec.IntValue EXACT_VECTOR_MAXIMUM_INPUT_KEYS;
    private static final ModConfigSpec.IntValue EXACT_VECTOR_MAXIMUM_OUTPUT_KEYS;
    private static final ModConfigSpec.IntValue EXACT_VECTOR_MAXIMUM_STARTS_PER_GRID_TICK;
    private static final ModConfigSpec.IntValue EXACT_VECTOR_MAXIMUM_ACTIVE_STAGES_PER_GRID_TICK;
    private static final ModConfigSpec.IntValue EXACT_VECTOR_GRID_TIME_BUDGET_MILLIS;
    private static final ModConfigSpec.BooleanValue LOG_EXACT_EXECUTION_STALLS;
    private static final ModConfigSpec.BooleanValue EXACT_VECTOR_VERIFY_STORAGE_ROUTE;

    private static final ModConfigSpec.BooleanValue LOG_SLOW_CRAFT_CALCULATIONS;
    private static final ModConfigSpec.IntValue SLOW_CRAFT_CALCULATION_MILLIS;
    private static final ModConfigSpec.BooleanValue LOG_CACHE_STATISTICS;
    private static final ModConfigSpec.BooleanValue LOG_CRAFTING_DECISION_FLOW;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("general");
        ENABLE_OPTIMIZER = builder
                .comment("Master switch for all ACO planning, execution-budget, exact-count, and integration behavior.")
                .define("enableOptimizer", true);
        builder.pop();

        builder.push("optimizationDomains");
        ENABLE_PATTERN_PROVIDER_DOMAIN = builder
                .comment("Pattern Provider generation, lookup, and refresh optimizations.")
                .define("patternProvider", true);
        ENABLE_CRAFTING_PLANNING_DOMAIN = builder
                .comment("Craft calculation memoization, compiled graphs, checked arithmetic, and plan caches.")
                .define("craftingPlanning", true);
        ENABLE_CRAFTING_EXECUTION_DOMAIN = builder
                .comment("Standard AE2 execution budgets, sequential dispatch, and public V2 transactions.")
                .define("craftingExecution", true);
        ENABLE_BIG_INTEGER_DOMAIN = builder
                .comment("Exact-count planning and ACO-owned execution for standard AE2 CPUs.")
                .define("bigInteger", true);
        ENABLE_OPTIONAL_INTEGRATION_DOMAIN = builder
                .comment("Read-only recipe intent and add-on lookup caches. External add-ons retain execution ownership.")
                .define("optionalIntegration", true);
        builder.pop();

        builder.push("craftingPlanning");
        DEDUPLICATE_ACTIVE_CRAFTING_CALCULATIONS = builder
                .comment("Share one running Future only for an identical requester, output, amount, and strategy.")
                .define("deduplicateActiveCalculations", true);
        ACTIVE_CALCULATION_DEDUPLICATION_WINDOW_TICKS = builder
                .comment("Maximum age of a reusable running calculation, in ticks.")
                .defineInRange("activeCalculationWindowTicks", 200, 1, TICKS_PER_SECOND * 60);
        LOG_CRAFTING_CALCULATION_DEDUPLICATION = builder
                .comment("Log calculation single-flight hits. Disabled to avoid normal-operation spam.")
                .define("logCalculationDeduplication", false);
        CACHE_COMPLETED_CRAFTING_PLANS = builder
                .comment("Cache short-lived completed plans. Storage/provider generation changes clear them earlier.")
                .define("cacheCompletedPlans", true);
        CACHE_SUCCESSFUL_COMPLETED_CRAFTING_PLANS = builder
                .comment("Also cache successful plans. Disabled because stock can change before submission.")
                .define("cacheSuccessfulPlans", false);
        COMPLETED_CRAFTING_PLAN_CACHE_SIZE = builder
                .comment("Maximum number of completed plans kept in memory.")
                .defineInRange("completedPlanCacheSize", 1024, 0, 65_536);
        COMPLETED_CRAFTING_PLAN_CACHE_TTL_TICKS = builder
                .comment("Completed-plan cache lifetime in ticks.")
                .defineInRange("completedPlanCacheTtlTicks", 40, 1, TICKS_PER_SECOND * 60);
        CACHE_PATTERN_LOOKUPS = builder
                .comment("Cache CraftingService pattern lookups in AE2 order until provider generation changes.")
                .define("cachePatternLookups", true);
        PATTERN_LOOKUP_CACHE_SIZE = builder
                .comment("Maximum number of output-to-pattern lookup entries.")
                .defineInRange("patternLookupCacheSize", 8192, 0, MAXIMUM_BOUNDED_ENTRIES);
        LOG_PATTERN_LOOKUP_CACHE = builder
                .comment("Log lookup-cache hits and clears.")
                .define("logPatternLookupCache", false);
        PRUNE_INVALID_CRAFTING_CANDIDATES = builder
                .comment("Remove only null, identity-duplicate, or structurally invalid candidates without reordering AE2 choices.")
                .define("pruneInvalidCandidates", true);
        MEMOIZE_CRAFTING_CALCULATION_QUERIES = builder
                .comment("Memoize calculation-invariant queries only inside one crafting calculation.")
                .define("memoizeCalculationQueries", true);
        COALESCE_CRAFTING_PROVIDER_REFRESHES = builder
                .comment("Coalesce duplicate provider refreshes in one tick and flush before every crafting read.")
                .define("coalesceProviderRefreshes", true);
        TRACK_PROVIDER_PATTERN_GENERATIONS = builder
                .comment("Rebuild provider indexes only when immutable pattern content or priority changes.")
                .define("trackProviderGenerations", true);
        ENABLE_LONG_ROOT_CRAFT_AMOUNTS = builder
                .comment("Allow root order amounts through Long.MAX_VALUE when UELM does not own this surface.")
                .define("enableLongRootAmounts", true);
        ENABLE_COMPILED_CRAFTING_GRAPH = builder
                .comment("Compile immutable generation-keyed recipe graphs for strict planning paths.")
                .define("enableCompiledGraph", true);
        ENABLE_AUTHORITATIVE_COMPILED_PLANNER = builder
                .comment("Replace AE2 planning only after the strict proof and Shadow qualification contracts pass.")
                .define("enableAuthoritativePlanner", false);
        ENABLE_PROOF_QUALIFIED_LONG_PLANS = builder
                .comment("Allow proven signed-long requests to use the compiled planner without changing recipe choice.")
                .define("enableProofQualifiedLongPlans", true);
        ENABLE_CHECKED_AE2_CRAFTING_ARITHMETIC = builder
                .comment("Detect overflow at AE2 arithmetic boundaries instead of allowing wrapped counts.")
                .define("enableCheckedArithmetic", true);
        ENABLE_CRAFTING_ENGINE_SHADOW_MODE = builder
                .comment("Compare eligible compiled plans with AE2 results without changing the AE2 result.")
                .define("enableShadowMode", true);
        LOG_CRAFTING_ENGINE_SHADOW_MISMATCHES = builder
                .comment("Log bounded Shadow mismatches.")
                .define("logShadowMismatches", true);
        CRAFTING_ENGINE_SHADOW_MAXIMUM_PATTERNS = builder
                .comment("Skip Shadow comparison above this pattern count.")
                .defineInRange("shadowMaximumPatterns", 262_144, 1, MAXIMUM_BOUNDED_ENTRIES);
        AUTHORITATIVE_MINIMUM_SHADOW_MATCHES = builder
                .comment("Matching AE2 comparisons required for the same generation-keyed root program.")
                .defineInRange(
                        "authoritativeMinimumShadowMatches",
                        DEFAULT_AUTHORITATIVE_SHADOW_MATCHES,
                        0,
                        MAXIMUM_AUTHORITATIVE_SHADOW_MATCHES);
        REQUIRE_WIDE_PLAN_SHADOW_QUALIFICATION = builder
                .comment("Require prior matching long-range Shadow evidence before accepting a proven wide plan.")
                .define("requireWidePlanShadowQualification", false);
        RETRY_INCOMPLETE_CRAFTING_GRAPH_SNAPSHOT = builder
                .comment("Rebuild once only when the captured graph is proven incomplete; structural failures are not retried.")
                .define("retryIncompleteGraphSnapshot", true);
        builder.pop();

        builder.push("craftingExecution");
        THROTTLE_CRAFTING_EXECUTION = builder
                .comment("Bound standard AE2 pattern pushes per CPU without changing capacity or displayed co-processors.")
                .define("throttleCraftingExecution", true);
        MAX_EFFECTIVE_COPROCESSORS_PER_CPU = builder
                .comment("Maximum effective co-processors spent by one CPU in an execution window.")
                .defineInRange(
                        "maxEffectiveCoprocessorsPerCpu",
                        DEFAULT_EFFECTIVE_COPROCESSORS_PER_CPU,
                        1,
                        MAX_SAFE_EFFECTIVE_COPROCESSORS);
        ADAPTIVE_CRAFTING_EXECUTION_BUDGET = builder
                .comment("Adapt the next CPU budget from measured execution time.")
                .define("adaptiveBudget", true);
        TARGET_CRAFTING_EXECUTION_MILLIS = builder
                .comment("Target wall-clock milliseconds for one CPU burst.")
                .defineInRange("targetExecutionMillis", 4, 1, 50);
        MINIMUM_ADAPTIVE_COPROCESSORS_PER_CPU = builder
                .comment("Minimum adaptive operation allowance for an active CPU.")
                .defineInRange("minimumAdaptiveOperations", 1024, 1, MAX_SAFE_EFFECTIVE_COPROCESSORS);
        SHARED_CRAFTING_EXECUTION_BUDGET = builder
                .comment("Share a measured execution budget between standard AE2 CPUs on one grid.")
                .define("sharedGridBudget", true);
        SHARED_CRAFTING_EXECUTION_MILLIS_PER_GRID = builder
                .comment("Target milliseconds spent by all standard AE2 CPUs on one grid per tick.")
                .defineInRange("sharedGridBudgetMillis", 8, 1, MAXIMUM_TICK_BUDGET_MILLIS);
        MINIMUM_SHARED_OPERATIONS_PER_CPU = builder
                .comment("Minimum progress allowance after a grid consumes its shared budget.")
                .defineInRange("minimumSharedOperations", 1, 1, 65_536);
        LOG_CRAFTING_EXECUTION_THROTTLING = builder
                .comment("Log execution-budget caps.")
                .define("logExecutionThrottling", false);
        THROTTLE_NEO_ECO_AE_EXECUTION = builder
                .comment("Apply only ACO's execution budget to NeoECO CPUs; NeoECO keeps execution ownership.")
                .define("throttleNeoEcoAeExecution", true);
        ENABLE_INSTANT_PATTERN_DISPATCH = builder
                .comment("Run AE2's original one-pattern dispatch in measured waves until its time budget or backpressure.")
                .define("enableInstantDispatch", true);
        INSTANT_PATTERN_DISPATCH_TIME_BUDGET_MILLIS = builder
                .comment("Per-CPU sequential dispatch budget in milliseconds.")
                .defineInRange("instantDispatchBudgetMillis", 4, 1, MAXIMUM_TICK_BUDGET_MILLIS);
        INSTANT_PATTERN_DISPATCH_PROBE_OPERATIONS = builder
                .comment("Configured cold wave request; the unmeasured ceiling below still caps the first wave.")
                .defineInRange("instantDispatchProbeOperations", 65_536, 1, 65_536);
        INSTANT_PATTERN_DISPATCH_MAXIMUM_UNMEASURED_WAVE_OPERATIONS = builder
                .comment("Hard first-wave ceiling before ACO has measured one normal AE2 push.")
                .defineInRange("instantDispatchMaximumUnmeasuredWave", 1024, 1, 65_536);
        INSTANT_PATTERN_DISPATCH_MAXIMUM_WAVE_OPERATIONS = builder
                .comment("Maximum operations in one measured dispatch wave.")
                .defineInRange("instantDispatchMaximumWave", 65_536, 1, MAXIMUM_BOUNDED_ENTRIES);
        MAX_INSTANT_PATTERN_DISPATCH_TRANSACTIONS = builder
                .comment("Maximum V2 adapter transactions per call. Sequential dispatch is time-budgeted separately.")
                .defineInRange("maximumInstantTransactions", 1024, 1, 65_536);
        ENABLE_TRANSACTIONAL_BATCHING_V2 = builder
                .comment("Enable the public prepare/commit/account/reconcile V2 contract for explicitly registered adapters.")
                .define("enableTransactionalBatchingV2", true);
        PERSIST_BATCH_TRANSACTION_JOURNAL = builder
                .comment("Persist non-terminal V2 transactions before target ownership transfer.")
                .define("persistTransactionJournal", true);
        BATCH_TRANSACTION_JOURNAL_MAXIMUM_ENTRIES = builder
                .comment("Maximum non-terminal V2 transaction records.")
                .defineInRange("transactionJournalMaximumEntries", 16_384, 16, 16_384);
        BATCH_TRANSACTION_RECONCILIATION_INTERVAL_TICKS = builder
                .comment("Ticks between bounded recovery scans.")
                .defineInRange("transactionReconciliationTicks", 20, 1, TICKS_PER_SECOND * 60);
        MAXIMUM_BATCH_EXECUTIONS = builder
                .comment("Maximum executions offered to one externally registered V2 adapter transaction.")
                .defineInRange("maximumBatchExecutions", 65_536, 1, MAXIMUM_BOUNDED_ENTRIES);
        builder.pop();

        builder.push("bigInteger");
        ENABLE_BIG_INTEGER_CRAFTING_BACKEND = builder
                .comment("Expose ACO's versioned exact-count plan and host APIs.")
                .define("enableBackend", true);
        ENABLE_EXACT_BIG_INTEGER_INVENTORY_SNAPSHOTS = builder
                .comment("Capture exact counts from supported storage while AE2 receives a saturated long facade.")
                .define("enableExactInventorySnapshots", true);
        ENABLE_ATOMIC_BIG_CAPACITY_PLANS = builder
                .comment("Calculate exact plan counts and CPU-byte costs beyond Long.MAX_VALUE without approximation.")
                .define("enableAtomicWidePlans", true);
        ENABLE_BIG_INTEGER_GAMEPLAY_EXECUTION = builder
                .comment("Execute exact jobs owned by a standard AE2 CPU; external CPU add-ons retain their own execution.")
                .define("enableStandardAe2ExactExecution", true);
        BIG_INTEGER_MAXIMUM_BITS = builder
                .comment("Maximum accepted exact-count magnitude in binary bits.")
                .defineInRange("maximumBits", BigCountMath.HARD_MAXIMUM_BITS, 64, BigCountMath.HARD_MAXIMUM_BITS);
        BIG_INTEGER_EXECUTION_WINDOW = builder
                .comment("Maximum executions exposed to a long/int adapter in one exact execution window.")
                .defineInRange("executionWindow", 65_536, 1, MAXIMUM_BOUNDED_ENTRIES);
        BIG_INTEGER_STATUS_PAGE_ENTRIES = builder
                .comment("Maximum summaries carried by one exact crafting-status page.")
                .defineInRange("statusPageEntries", 1024, 16, 16_384);
        BIG_INTEGER_RUNTIME_COUNT_BUDGET_MIB = builder
                .comment("Aggregate memory-accounting budget for exact-count magnitudes in one CPU runtime, in MiB.")
                .defineInRange("runtimeCountBudgetMiB", 256, 32, 4096);
        LOG_WIDE_PLAN_SUBMISSION_DECLINES = builder
                .comment("Log exact submission decline reasons without changing fail-closed decisions.")
                .define("logSubmissionDeclines", true);
        builder.pop();

        builder.push("exactVectorCrafting");
        ENABLE_EXACT_VECTOR_CRAFTING = builder
                .comment("Execute strictly proven ordinary crafting DAGs as quantity-independent physical transactions.")
                .define("enabled", true);
        ENABLE_EXACT_PHYSICAL_EXECUTION = builder
                .comment("Take physical execution ownership only for a standard AE2 exact job after all preflight checks pass.")
                .define("enablePhysicalExecution", true);
        EXACT_VECTOR_MAXIMUM_PATTERN_NODES = builder
                .comment("Maximum distinct deterministic pattern nodes in one exact transaction.")
                .defineInRange("maximumPatternNodes", 1024, 1, MAXIMUM_BOUNDED_ENTRIES);
        EXACT_VECTOR_MAXIMUM_INPUT_KEYS = builder
                .comment("Maximum distinct external input keys in one exact transaction.")
                .defineInRange("maximumUniqueInputKeys", 128, 1, 65_536);
        EXACT_VECTOR_MAXIMUM_OUTPUT_KEYS = builder
                .comment("Maximum distinct final and fixed remaining-output keys in one exact transaction.")
                .defineInRange("maximumUniqueOutputKeys", 128, 1, 65_536);
        EXACT_VECTOR_MAXIMUM_STARTS_PER_GRID_TICK = builder
                .comment("Maximum new exact ownership transfers per ME grid and tick.")
                .defineInRange("maximumStartsPerGridPerTick", 1, 1, 64);
        EXACT_VECTOR_MAXIMUM_ACTIVE_STAGES_PER_GRID_TICK = builder
                .comment("Maximum physical recipe nodes inspected per grid and tick; order quantity never controls this loop.")
                .defineInRange("maximumActiveStagesPerGridPerTick", 256, 1, MAXIMUM_BOUNDED_ENTRIES);
        EXACT_VECTOR_GRID_TIME_BUDGET_MILLIS = builder
                .comment("Soft main-thread scheduling budget per grid, measured from exact processing start.")
                .defineInRange("gridTimeBudgetMillis", 2, 1, MAXIMUM_TICK_BUDGET_MILLIS);
        LOG_EXACT_EXECUTION_STALLS = builder
                .comment("Log a changed stall reason immediately and an unchanged reason every 600 ticks.")
                .define("logExecutionStalls", true);
        EXACT_VECTOR_VERIFY_STORAGE_ROUTE = builder
                .comment("Prove exact input release and final output insertion before ownership transfer.")
                .define("verifyStorageRouteBeforeOwnership", true);
        builder.pop();

        builder.push("optionalIntegrations");
        ENABLE_APPLIED_E_COMPATIBILITY = builder
                .comment("Keep AppliedE temporary patterns on AppliedE/AE2-owned lifecycle paths.")
                .define("enableAppliedECompatibility", true);
        FORCE_AE2_PLANNER_FOR_APPLIED_E_PATTERNS = builder
                .comment("Never compile request-sized AppliedE TransmutationPattern instances as fixed recipes.")
                .define("forceAe2PlannerForTransmutationPatterns", true);
        TREAT_APPLIED_E_PROVIDER_AS_DYNAMIC = builder
                .comment("Treat AppliedE EMC providers as dynamic while still coalescing duplicate notifications.")
                .define("treatAppliedEProviderAsDynamic", true);
        ENABLE_ADDON_MACHINE_OPTIMIZATIONS = builder
                .comment("Enable read-only lookup caches for supported add-on machines.")
                .define("enableAddonMachineCaches", true);
        CACHE_CIRCUIT_CUTTER_RECIPES = builder
                .comment("Cache validated Circuit Cutter recipe candidates by exact immutable input signature.")
                .define("cacheCircuitCutterRecipes", true);
        CACHE_CIRCUIT_CUTTER_NEGATIVE_RESULTS = builder
                .comment("Cache exact no-recipe Circuit Cutter results until input change or datapack reload.")
                .define("cacheCircuitCutterNegativeResults", true);
        CIRCUIT_CUTTER_RECIPE_CACHE_SIZE = builder
                .comment("Maximum Circuit Cutter signatures retained until reload.")
                .defineInRange("circuitCutterCacheSize", 4096, 16, 262_144);
        CACHE_REACTION_CHAMBER_RECIPE = builder
                .comment("Reuse an AdvancedAE Reaction Chamber recipe only until its input generation changes.")
                .define("cacheReactionChamberRecipe", true);
        CACHE_AE2_OVERCLOCK_REFLECTION = builder
                .comment("Cache AE2 Overclock reflection metadata, not machine results.")
                .define("cacheAe2OverclockReflection", true);
        USE_AE2_OVERCLOCK_METHOD_HANDLES = builder
                .comment("Use prebuilt MethodHandles with reflection fallback for cached metadata.")
                .define("useAe2OverclockMethodHandles", true);
        CACHE_AE2_OVERCLOCK_UPGRADE_COUNTS = builder
                .comment("Reuse upgrade counts for the same machine during one server tick.")
                .define("cacheAe2OverclockUpgradeCounts", true);
        CACHE_ASSEMBLER_MATRIX_THREAD_COUNTS = builder
                .comment("Reuse Assembly Matrix thread totals during one tick with mutation invalidation.")
                .define("cacheAssemblerMatrixThreadCounts", true);
        CACHE_ASSEMBLER_MATRIX_BUSY_COUNT = builder
                .comment("Reuse Assembly Matrix busy totals during one tick with status invalidation.")
                .define("cacheAssemblerMatrixBusyCount", true);
        COALESCE_ASSEMBLER_MATRIX_STATUS_UPDATES = builder
                .comment("Coalesce identical visual/status broadcasts within one tick.")
                .define("coalesceAssemblerMatrixStatusUpdates", true);
        CACHE_ASSEMBLER_MATRIX_ROUTING = builder
                .comment("Reuse a validated Matrix crafter route until thread or structure generation changes.")
                .define("cacheAssemblerMatrixRouting", true);
        ENABLE_RECIPE_INTENT_BRIDGE = builder
                .comment("Capture short-lived Pattern Provider recipe intent without taking machine execution ownership.")
                .define("enableRecipeIntentBridge", true);
        CAPTURE_PATTERN_PROVIDER_RECIPE_INTENTS = builder
                .comment("Record successful Pattern Provider pushes as bounded machine-side hints.")
                .define("capturePatternProviderRecipeIntents", true);
        RECIPE_INTENT_TTL_TICKS = builder
                .comment("Recipe-intent lifetime in ticks.")
                .defineInRange("recipeIntentTtlTicks", 20, 1, TICKS_PER_SECOND * 30);
        MAXIMUM_RECIPE_INTENT_ENTRIES = builder
                .comment("Hard cap for captured recipe-intent entries.")
                .defineInRange("maximumRecipeIntentEntries", 4096, 16, MAXIMUM_BOUNDED_ENTRIES);
        ENABLE_GTCEU_RECIPE_INTENT_FAST_PATH = builder
                .comment("Try output-indexed GTCEu candidates first, then let GTCEu validate and own the recipe.")
                .define("enableGtceuRecipeIntentFastPath", true);
        GTCEU_RECIPE_INTENT_MAXIMUM_CANDIDATES = builder
                .comment("Maximum intent-matched GTCEu candidates prepended to its original iterator.")
                .defineInRange("gtceuMaximumCandidates", 16, 1, 1024);
        GTCEU_RECIPE_INTENT_INDEX_CACHE_SIZE = builder
                .comment("Maximum output indexes retained for GTCEu recipe types.")
                .defineInRange("gtceuIndexCacheSize", 64, 1, 1024);
        GTCEU_RECIPE_INTENT_SEARCH_RADIUS = builder
                .comment("Maximum controller-to-input distance for GTCEu intent association, in blocks.")
                .defineInRange("gtceuSearchRadius", 16, 0, 64);
        GTCEU_RECIPE_INTENT_NEARBY_MAXIMUM_ENTRIES = builder
                .comment("Maximum nearby intents inspected for one GTCEu recipe search.")
                .defineInRange("gtceuNearbyMaximumEntries", 64, 1, 4096);
        LOG_GTCEU_RECIPE_INTENT_FAST_PATH = builder
                .comment("Log GTCEu intent hits and bounded candidate counts.")
                .define("logGtceuRecipeIntentFastPath", false);
        ENABLE_MEKANISM_RECIPE_INTENT_FAST_PATH = builder
                .comment("Try output-indexed Mekanism candidates first, then run Mekanism's own recipe test.")
                .define("enableMekanismRecipeIntentFastPath", true);
        MEKANISM_RECIPE_INTENT_MAXIMUM_CANDIDATES = builder
                .comment("Maximum intent-matched Mekanism candidates tested before its original lookup.")
                .defineInRange("mekanismMaximumCandidates", 16, 1, 1024);
        MEKANISM_RECIPE_INTENT_INDEX_CACHE_SIZE = builder
                .comment("Maximum output indexes retained for Mekanism recipe types.")
                .defineInRange("mekanismIndexCacheSize", 128, 1, 1024);
        CACHE_RESOLVED_RECIPE_INTENTS = builder
                .comment("Reuse a validated candidate only while the originating intent remains current.")
                .define("cacheResolvedRecipeIntents", true);
        RESOLVED_RECIPE_INTENT_CACHE_SIZE = builder
                .comment("Maximum resolved intent entries per optional integration.")
                .defineInRange("resolvedIntentCacheSize", 8192, 16, MAXIMUM_BOUNDED_ENTRIES);
        LOG_MEKANISM_RECIPE_INTENT_FAST_PATH = builder
                .comment("Log Mekanism intent hits and bounded candidate counts.")
                .define("logMekanismRecipeIntentFastPath", false);
        LOG_CAPTURED_RECIPE_INTENTS = builder
                .comment("Log each captured recipe intent. Intended only for targeted diagnostics.")
                .define("logCapturedRecipeIntents", false);
        LOG_RECIPE_INTENT_REGISTRY_EVICTIONS = builder
                .comment("Log recipe-intent expiry and hard-cap eviction.")
                .define("logRecipeIntentRegistryEvictions", false);
        builder.pop();

        builder.push("diagnostics");
        LOG_SLOW_CRAFT_CALCULATIONS = builder
                .comment("Log crafting calculations above the configured duration.")
                .define("logSlowCraftCalculations", true);
        SLOW_CRAFT_CALCULATION_MILLIS = builder
                .comment("Slow calculation threshold in milliseconds.")
                .defineInRange("slowCraftCalculationMillis", 500, 1, 300_000);
        LOG_CACHE_STATISTICS = builder
                .comment("Log bounded cache statistics.")
                .define("logCacheStatistics", false);
        LOG_CRAFTING_DECISION_FLOW = builder
                .comment("Write structured planning and exact-execution boundary events to debug.log without per-tick success spam.")
                .define("logCraftingDecisionFlow", true);
        builder.pop();

        SPEC = builder.build();
    }

    private ACOConfig() {
    }

    public static void register(ModContainer container) {
        container.registerConfig(
                ModConfig.Type.COMMON,
                SPEC,
                "ae2_crafting_optimizer-common.toml");
    }

    public static boolean enableOptimizer() {
        return ENABLE_OPTIMIZER.get();
    }

    /** 共通gateだけが使用する、masterを重ねていないdomain設定値。 */
    public static boolean rawDomainEnabled(OptimizationDomain domain) {
        return switch (domain) {
            case PATTERN_PROVIDER -> ENABLE_PATTERN_PROVIDER_DOMAIN.get();
            case CRAFTING_PLANNING -> ENABLE_CRAFTING_PLANNING_DOMAIN.get();
            case CRAFTING_EXECUTION -> ENABLE_CRAFTING_EXECUTION_DOMAIN.get();
            case BIG_INTEGER -> ENABLE_BIG_INTEGER_DOMAIN.get();
            case OPTIONAL_INTEGRATION -> ENABLE_OPTIONAL_INTEGRATION_DOMAIN.get();
        };
    }

    public static boolean deduplicateActiveCraftingCalculations() {
        return OptimizationFeatureGate.allows(OptimizationFeature.ACTIVE_CALCULATION_DEDUPLICATION, DEDUPLICATE_ACTIVE_CRAFTING_CALCULATIONS.get());
    }

    public static int getActiveCalculationDeduplicationWindowTicks() { return ACTIVE_CALCULATION_DEDUPLICATION_WINDOW_TICKS.get(); }
    public static boolean logCraftingCalculationDeduplication() { return enableOptimizer() && LOG_CRAFTING_CALCULATION_DEDUPLICATION.get(); }

    public static boolean cacheCompletedCraftingPlans() {
        return OptimizationFeatureGate.allows(OptimizationFeature.COMPLETED_PLAN_CACHE, CACHE_COMPLETED_CRAFTING_PLANS.get())
                && getCompletedCraftingPlanCacheSize() > 0;
    }

    public static boolean cacheSuccessfulCompletedCraftingPlans() { return cacheCompletedCraftingPlans() && CACHE_SUCCESSFUL_COMPLETED_CRAFTING_PLANS.get(); }
    public static int getCompletedCraftingPlanCacheSize() { return COMPLETED_CRAFTING_PLAN_CACHE_SIZE.get(); }
    public static int getCompletedCraftingPlanCacheTtlTicks() { return COMPLETED_CRAFTING_PLAN_CACHE_TTL_TICKS.get(); }

    public static boolean cachePatternLookups() {
        return OptimizationFeatureGate.allows(OptimizationFeature.PATTERN_LOOKUP_CACHE, CACHE_PATTERN_LOOKUPS.get())
                && getPatternLookupCacheSize() > 0;
    }

    public static int getPatternLookupCacheSize() { return PATTERN_LOOKUP_CACHE_SIZE.get(); }
    public static boolean logPatternLookupCache() { return enableOptimizer() && LOG_PATTERN_LOOKUP_CACHE.get(); }
    public static boolean pruneInvalidCraftingCandidates() { return OptimizationFeatureGate.allows(OptimizationFeature.CANDIDATE_PRUNING, PRUNE_INVALID_CRAFTING_CANDIDATES.get()); }
    public static boolean memoizeCraftingCalculationQueries() { return OptimizationFeatureGate.allows(OptimizationFeature.CRAFTING_QUERY_MEMOIZATION, MEMOIZE_CRAFTING_CALCULATION_QUERIES.get()); }
    public static boolean coalesceCraftingProviderRefreshes() { return OptimizationFeatureGate.allows(OptimizationFeature.PROVIDER_REFRESH_COALESCING, COALESCE_CRAFTING_PROVIDER_REFRESHES.get()); }
    public static boolean trackProviderPatternGenerations() { return OptimizationFeatureGate.allows(OptimizationFeature.PROVIDER_GENERATION_TRACKING, TRACK_PROVIDER_PATTERN_GENERATIONS.get()); }

    public static boolean enableLongRootCraftAmounts() {
        return OptimizationFeatureGate.allows(
                OptimizationFeature.LONG_ROOT_AMOUNTS,
                ENABLE_LONG_ROOT_CRAFT_AMOUNTS.get());
    }

    public static boolean enableCompiledCraftingGraph() { return OptimizationFeatureGate.allows(OptimizationFeature.COMPILED_CRAFTING_GRAPH, ENABLE_COMPILED_CRAFTING_GRAPH.get()); }
    public static boolean enableAuthoritativeCompiledPlanner() { return enableCompiledCraftingGraph() && OptimizationFeatureGate.allows(OptimizationFeature.AUTHORITATIVE_COMPILED_PLANNER, ENABLE_AUTHORITATIVE_COMPILED_PLANNER.get()); }
    public static boolean enableProofQualifiedLongPlans() { return enableCompiledCraftingGraph() && ENABLE_PROOF_QUALIFIED_LONG_PLANS.get(); }
    public static boolean enableCheckedAe2CraftingArithmetic() { return OptimizationFeatureGate.allows(OptimizationFeature.CHECKED_CRAFTING_ARITHMETIC, ENABLE_CHECKED_AE2_CRAFTING_ARITHMETIC.get()); }

    public static boolean enableCraftingEngineShadowMode() {
        return shouldEnableCraftingEngineShadowMode(enableCompiledCraftingGraph(), ENABLE_CRAFTING_ENGINE_SHADOW_MODE.get(), enableAuthoritativeCompiledPlanner() || enableProofQualifiedLongPlans(), requireWidePlanShadowQualification());
    }

    static boolean shouldEnableCraftingEngineShadowMode(boolean compiledGraphEnabled, boolean shadowModeConfigured, boolean normalReplacementEnabled, boolean wideQualificationRequired) {
        return compiledGraphEnabled && shadowModeConfigured && (normalReplacementEnabled || wideQualificationRequired);
    }

    public static boolean logCraftingEngineShadowMismatches() { return enableCraftingEngineShadowMode() && LOG_CRAFTING_ENGINE_SHADOW_MISMATCHES.get(); }
    public static int getCraftingEngineShadowMaximumPatterns() { return CRAFTING_ENGINE_SHADOW_MAXIMUM_PATTERNS.get(); }
    public static int getAuthoritativeMinimumShadowMatches() { return AUTHORITATIVE_MINIMUM_SHADOW_MATCHES.get(); }
    public static boolean requireWidePlanShadowQualification() { return REQUIRE_WIDE_PLAN_SHADOW_QUALIFICATION.get(); }
    public static boolean retryIncompleteCraftingGraphSnapshot() { return enableCompiledCraftingGraph() && RETRY_INCOMPLETE_CRAFTING_GRAPH_SNAPSHOT.get(); }

    public static boolean throttleCraftingExecution() { return OptimizationFeatureGate.allows(OptimizationFeature.CRAFTING_EXECUTION_BUDGET, THROTTLE_CRAFTING_EXECUTION.get()); }
    public static int getMaxEffectiveCoprocessorsPerCpu() { return MAX_EFFECTIVE_COPROCESSORS_PER_CPU.get(); }
    public static boolean adaptiveCraftingExecutionBudget() { return throttleCraftingExecution() && ADAPTIVE_CRAFTING_EXECUTION_BUDGET.get(); }
    public static int getTargetCraftingExecutionMillis() { return TARGET_CRAFTING_EXECUTION_MILLIS.get(); }
    public static int getMinimumAdaptiveCoprocessorsPerCpu() { return Math.min(getMaxEffectiveCoprocessorsPerCpu(), MINIMUM_ADAPTIVE_COPROCESSORS_PER_CPU.get()); }
    public static boolean sharedCraftingExecutionBudget() { return throttleCraftingExecution() && SHARED_CRAFTING_EXECUTION_BUDGET.get(); }
    public static int getSharedCraftingExecutionMillisPerGrid() { return SHARED_CRAFTING_EXECUTION_MILLIS_PER_GRID.get(); }
    public static int getMinimumSharedOperationsPerCpu() { return MINIMUM_SHARED_OPERATIONS_PER_CPU.get(); }
    public static boolean logCraftingExecutionThrottling() { return enableOptimizer() && LOG_CRAFTING_EXECUTION_THROTTLING.get(); }
    public static boolean throttleNeoEcoAeExecution() { return throttleCraftingExecution() && THROTTLE_NEO_ECO_AE_EXECUTION.get(); }
    public static boolean enableInstantPatternDispatch() { return OptimizationFeatureGate.allows(OptimizationFeature.INSTANT_PATTERN_DISPATCH, ENABLE_INSTANT_PATTERN_DISPATCH.get()); }
    public static int getInstantPatternDispatchTimeBudgetMillis() { return INSTANT_PATTERN_DISPATCH_TIME_BUDGET_MILLIS.get(); }
    public static int getInstantPatternDispatchProbeOperations() { return INSTANT_PATTERN_DISPATCH_PROBE_OPERATIONS.get(); }
    public static int getInstantPatternDispatchMaximumUnmeasuredWaveOperations() { return INSTANT_PATTERN_DISPATCH_MAXIMUM_UNMEASURED_WAVE_OPERATIONS.get(); }
    public static int getInstantPatternDispatchMaximumWaveOperations() { return INSTANT_PATTERN_DISPATCH_MAXIMUM_WAVE_OPERATIONS.get(); }
    public static int getMaxInstantPatternDispatchTransactions() { return MAX_INSTANT_PATTERN_DISPATCH_TRANSACTIONS.get(); }

    public static boolean enableTransactionalBatchingV2() {
        return OptimizationFeatureGate.allows(OptimizationFeature.TRANSACTIONAL_BATCHING, ENABLE_TRANSACTIONAL_BATCHING_V2.get())
                && persistBatchTransactionJournal();
    }

    public static boolean persistBatchTransactionJournal() { return PERSIST_BATCH_TRANSACTION_JOURNAL.get(); }
    public static int getBatchTransactionJournalMaximumEntries() { return BATCH_TRANSACTION_JOURNAL_MAXIMUM_ENTRIES.get(); }
    public static int getBatchTransactionReconciliationIntervalTicks() { return BATCH_TRANSACTION_RECONCILIATION_INTERVAL_TICKS.get(); }
    public static int getMaximumBatchExecutions() { return MAXIMUM_BATCH_EXECUTIONS.get(); }

    public static boolean enableAppliedECompatibility() { return OptimizationFeatureGate.allows(OptimizationFeature.APPLIED_E_COMPATIBILITY, ENABLE_APPLIED_E_COMPATIBILITY.get()); }
    public static boolean forceAe2PlannerForAppliedEPatterns() { return enableAppliedECompatibility() && FORCE_AE2_PLANNER_FOR_APPLIED_E_PATTERNS.get(); }
    public static boolean treatAppliedEProviderAsDynamic() { return enableAppliedECompatibility() && TREAT_APPLIED_E_PROVIDER_AS_DYNAMIC.get(); }
    public static boolean enableAddonMachineOptimizations() { return OptimizationFeatureGate.allows(OptimizationFeature.ADDON_MACHINE_CACHE, ENABLE_ADDON_MACHINE_OPTIMIZATIONS.get()); }
    public static boolean cacheCircuitCutterRecipes() { return OptimizationFeatureGate.allows(OptimizationFeature.CIRCUIT_CUTTER_RECIPE_CACHE, CACHE_CIRCUIT_CUTTER_RECIPES.get()); }
    public static boolean cacheCircuitCutterNegativeResults() { return cacheCircuitCutterRecipes() && CACHE_CIRCUIT_CUTTER_NEGATIVE_RESULTS.get(); }
    public static int getCircuitCutterRecipeCacheSize() { return CIRCUIT_CUTTER_RECIPE_CACHE_SIZE.get(); }
    public static boolean cacheReactionChamberRecipe() { return enableAddonMachineOptimizations() && CACHE_REACTION_CHAMBER_RECIPE.get(); }
    public static boolean cacheAe2OverclockReflection() { return enableAddonMachineOptimizations() && CACHE_AE2_OVERCLOCK_REFLECTION.get(); }
    public static boolean useAe2OverclockMethodHandles() { return cacheAe2OverclockReflection() && USE_AE2_OVERCLOCK_METHOD_HANDLES.get(); }
    public static boolean cacheAe2OverclockUpgradeCounts() { return enableAddonMachineOptimizations() && CACHE_AE2_OVERCLOCK_UPGRADE_COUNTS.get(); }
    public static boolean cacheAssemblerMatrixThreadCounts() { return enableAddonMachineOptimizations() && CACHE_ASSEMBLER_MATRIX_THREAD_COUNTS.get(); }
    public static boolean cacheAssemblerMatrixBusyCount() { return enableAddonMachineOptimizations() && CACHE_ASSEMBLER_MATRIX_BUSY_COUNT.get(); }
    public static boolean coalesceAssemblerMatrixStatusUpdates() { return enableAddonMachineOptimizations() && COALESCE_ASSEMBLER_MATRIX_STATUS_UPDATES.get(); }
    public static boolean cacheAssemblerMatrixRouting() { return enableAddonMachineOptimizations() && CACHE_ASSEMBLER_MATRIX_ROUTING.get(); }

    public static boolean enableRecipeIntentBridge() { return OptimizationFeatureGate.allows(OptimizationFeature.RECIPE_INTENT_BRIDGE, ENABLE_RECIPE_INTENT_BRIDGE.get()); }
    public static boolean capturePatternProviderRecipeIntents() { return enableRecipeIntentBridge() && CAPTURE_PATTERN_PROVIDER_RECIPE_INTENTS.get(); }
    public static int getRecipeIntentTtlTicks() { return RECIPE_INTENT_TTL_TICKS.get(); }
    public static int getMaximumRecipeIntentEntries() { return MAXIMUM_RECIPE_INTENT_ENTRIES.get(); }
    public static boolean enableGtceuRecipeIntentFastPath() { return enableRecipeIntentBridge() && ENABLE_GTCEU_RECIPE_INTENT_FAST_PATH.get(); }
    public static int getGtceuRecipeIntentMaximumCandidates() { return GTCEU_RECIPE_INTENT_MAXIMUM_CANDIDATES.get(); }
    public static int getGtceuRecipeIntentIndexCacheSize() { return GTCEU_RECIPE_INTENT_INDEX_CACHE_SIZE.get(); }
    public static int getGtceuRecipeIntentSearchRadius() { return GTCEU_RECIPE_INTENT_SEARCH_RADIUS.get(); }
    public static int getGtceuRecipeIntentNearbyMaximumEntries() { return GTCEU_RECIPE_INTENT_NEARBY_MAXIMUM_ENTRIES.get(); }
    public static boolean logGtceuRecipeIntentFastPath() { return enableGtceuRecipeIntentFastPath() && LOG_GTCEU_RECIPE_INTENT_FAST_PATH.get(); }
    public static boolean enableMekanismRecipeIntentFastPath() { return enableRecipeIntentBridge() && ENABLE_MEKANISM_RECIPE_INTENT_FAST_PATH.get(); }
    public static int getMekanismRecipeIntentMaximumCandidates() { return MEKANISM_RECIPE_INTENT_MAXIMUM_CANDIDATES.get(); }
    public static int getMekanismRecipeIntentIndexCacheSize() { return MEKANISM_RECIPE_INTENT_INDEX_CACHE_SIZE.get(); }
    public static boolean cacheResolvedRecipeIntents() { return enableRecipeIntentBridge() && CACHE_RESOLVED_RECIPE_INTENTS.get(); }
    public static int getResolvedRecipeIntentCacheSize() { return RESOLVED_RECIPE_INTENT_CACHE_SIZE.get(); }
    public static boolean logMekanismRecipeIntentFastPath() { return enableMekanismRecipeIntentFastPath() && LOG_MEKANISM_RECIPE_INTENT_FAST_PATH.get(); }
    public static boolean logCapturedRecipeIntents() { return enableRecipeIntentBridge() && LOG_CAPTURED_RECIPE_INTENTS.get(); }
    public static boolean logRecipeIntentRegistryEvictions() { return enableRecipeIntentBridge() && LOG_RECIPE_INTENT_REGISTRY_EVICTIONS.get(); }

    public static boolean enableBigIntegerCraftingBackend() { return OptimizationFeatureGate.allows(OptimizationFeature.BIG_INTEGER_BACKEND, ENABLE_BIG_INTEGER_CRAFTING_BACKEND.get()); }
    public static boolean enableExactBigIntegerInventorySnapshots() { return enableBigIntegerCraftingBackend() && OptimizationFeatureGate.allows(OptimizationFeature.EXACT_INVENTORY_SNAPSHOT, ENABLE_EXACT_BIG_INTEGER_INVENTORY_SNAPSHOTS.get()); }
    public static boolean enableAtomicBigCapacityPlans() { return enableCompiledCraftingGraph() && enableBigIntegerCraftingBackend() && OptimizationFeatureGate.allows(OptimizationFeature.ATOMIC_BIG_CAPACITY_PLANS, ENABLE_ATOMIC_BIG_CAPACITY_PLANS.get()); }
    public static boolean enableBigIntegerGameplayExecution() { return enableCompiledCraftingGraph() && enableBigIntegerCraftingBackend() && OptimizationFeatureGate.allows(OptimizationFeature.BIG_INTEGER_GAMEPLAY_EXECUTION, ENABLE_BIG_INTEGER_GAMEPLAY_EXECUTION.get()); }
    public static int getBigIntegerMaximumBits() { return BIG_INTEGER_MAXIMUM_BITS.get(); }
    public static int getBigIntegerExecutionWindow() { return BIG_INTEGER_EXECUTION_WINDOW.get(); }
    public static int getBigIntegerStatusPageEntries() { return BIG_INTEGER_STATUS_PAGE_ENTRIES.get(); }
    public static long getBigIntegerRuntimeCountBudgetBytes() { return Math.multiplyExact((long) BIG_INTEGER_RUNTIME_COUNT_BUDGET_MIB.get(), BYTES_PER_MEBIBYTE); }

    public static boolean enableExactVectorCrafting() { return enableCompiledCraftingGraph() && OptimizationFeatureGate.allows(OptimizationFeature.EXACT_VECTOR_CRAFTING, ENABLE_EXACT_VECTOR_CRAFTING.get()); }

    /** ACOが標準AE2 CPUの物理transactionを所有してよい場合だけtrueを返す。 */
    public static boolean enableExactBigIntegerPhysicalExecution() {
        return enableExactVectorCrafting() && enableBigIntegerGameplayExecution() && ENABLE_EXACT_PHYSICAL_EXECUTION.get();
    }

    public static int getExactVectorMaximumPatternNodes() { return EXACT_VECTOR_MAXIMUM_PATTERN_NODES.get(); }
    public static int getExactVectorMaximumInputKeys() { return EXACT_VECTOR_MAXIMUM_INPUT_KEYS.get(); }
    public static int getExactVectorMaximumOutputKeys() { return EXACT_VECTOR_MAXIMUM_OUTPUT_KEYS.get(); }
    public static int getExactVectorMaximumStartsPerGridTick() { return EXACT_VECTOR_MAXIMUM_STARTS_PER_GRID_TICK.get(); }
    public static int getExactVectorMaximumActiveStagesPerGridTick() { return EXACT_VECTOR_MAXIMUM_ACTIVE_STAGES_PER_GRID_TICK.get(); }
    public static int getExactVectorGridTimeBudgetMillis() { return EXACT_VECTOR_GRID_TIME_BUDGET_MILLIS.get(); }
    public static boolean logExactExecutionStalls() { return enableExactVectorCrafting() && LOG_EXACT_EXECUTION_STALLS.get(); }
    public static boolean verifyExactStorageRouteBeforeOwnership() { return enableExactVectorCrafting() && EXACT_VECTOR_VERIFY_STORAGE_ROUTE.get(); }
    public static boolean logWidePlanSubmissionDeclines() { return enableOptimizer() && LOG_WIDE_PLAN_SUBMISSION_DECLINES.get(); }
    public static boolean logSlowCraftCalculations() { return enableOptimizer() && LOG_SLOW_CRAFT_CALCULATIONS.get(); }
    public static int getSlowCraftCalculationMillis() { return SLOW_CRAFT_CALCULATION_MILLIS.get(); }
    public static boolean logCacheStatistics() { return enableOptimizer() && LOG_CACHE_STATISTICS.get(); }
    public static boolean logCraftingDecisionFlow() { return enableOptimizer() && LOG_CRAFTING_DECISION_FLOW.get(); }
}
