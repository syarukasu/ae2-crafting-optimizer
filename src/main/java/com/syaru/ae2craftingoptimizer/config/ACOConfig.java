package com.syaru.ae2craftingoptimizer.config;

import com.syaru.ae2craftingoptimizer.engine.BigCountMath;
import java.util.List;
import java.util.Locale;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * ACOの設定Schemaと、実行側が使用する型付きgetterを一か所に集約する。
 * 実験機能は親スイッチと個別スイッチの両方が有効な場合だけtrueを返す。
 */
public final class ACOConfig {
    public static final int MAX_SAFE_EFFECTIVE_COPROCESSORS = Integer.MAX_VALUE - 1;
    public static final int DEFAULT_EFFECTIVE_COPROCESSORS_PER_CPU = 264_192;
    /** Authoritative採用前に同じ世代のRoot Programへ要求する連続Shadow一致数。 */
    private static final int DEFAULT_AUTHORITATIVE_SHADOW_MATCHES = 64;
    /** 設定ミスで永遠に採用されない状態を作らないための一致回数上限。 */
    private static final int MAXIMUM_AUTHORITATIVE_SHADOW_MATCHES = 1_048_576;
    private static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue ENABLE_OPTIMIZER;
    private static final ForgeConfigSpec.BooleanValue TWO_STAGE_MISSING_PREVIEW;
    private static final ForgeConfigSpec.BooleanValue CANCEL_CALCULATION_AFTER_PRELIMINARY_MISSING_PREVIEW;
    private static final ForgeConfigSpec.BooleanValue SKIP_CALCULATION_ON_CACHED_MISSING_PREVIEW;
    private static final ForgeConfigSpec.BooleanValue USE_MISSING_PREVIEW_CACHE;
    private static final ForgeConfigSpec.IntValue MISSING_PREVIEW_CACHE_SIZE;
    private static final ForgeConfigSpec.IntValue MISSING_PREVIEW_CACHE_TTL_SECONDS;
    private static final ForgeConfigSpec.BooleanValue INVALIDATE_CACHE_ON_STORAGE_CHANGE;
    private static final ForgeConfigSpec.BooleanValue INVALIDATE_CACHE_ON_PATTERN_CHANGE;
    private static final ForgeConfigSpec.IntValue MINIMUM_CALCULATION_MILLIS_FOR_PREVIEW;
    private static final ForgeConfigSpec.LongValue MINIMUM_REQUESTED_AMOUNT_FOR_PREVIEW;
    private static final ForgeConfigSpec.IntValue PREVIEW_MAXIMUM_ENTRIES;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> HEAVY_PROCESS_HINTS;
    private static final ForgeConfigSpec.BooleanValue DEDUPLICATE_ACTIVE_CRAFTING_CALCULATIONS;
    private static final ForgeConfigSpec.IntValue ACTIVE_CALCULATION_DEDUPLICATION_WINDOW_TICKS;
    private static final ForgeConfigSpec.BooleanValue LOG_CRAFTING_CALCULATION_DEDUPLICATION;
    private static final ForgeConfigSpec.BooleanValue CACHE_COMPLETED_CRAFTING_PLANS;
    private static final ForgeConfigSpec.BooleanValue CACHE_SUCCESSFUL_COMPLETED_CRAFTING_PLANS;
    private static final ForgeConfigSpec.IntValue COMPLETED_CRAFTING_PLAN_CACHE_SIZE;
    private static final ForgeConfigSpec.IntValue COMPLETED_CRAFTING_PLAN_CACHE_TTL_TICKS;
    private static final ForgeConfigSpec.BooleanValue FAST_FAIL_MISSING_CRAFTS;
    private static final ForgeConfigSpec.LongValue MINIMUM_REQUESTED_AMOUNT_FOR_FAST_FAIL;
    private static final ForgeConfigSpec.IntValue DETERMINISTIC_PREFLIGHT_MAX_DEPTH;
    private static final ForgeConfigSpec.IntValue DETERMINISTIC_PREFLIGHT_MAX_NODES;
    private static final ForgeConfigSpec.BooleanValue LOG_FAST_FAIL_MISSING_CRAFTS;
    private static final ForgeConfigSpec.BooleanValue CACHE_PATTERN_LOOKUPS;
    private static final ForgeConfigSpec.IntValue PATTERN_LOOKUP_CACHE_SIZE;
    private static final ForgeConfigSpec.BooleanValue CACHE_CRAFTABLE_SETS;
    private static final ForgeConfigSpec.IntValue CRAFTABLE_SET_CACHE_SIZE;
    private static final ForgeConfigSpec.BooleanValue LOG_PATTERN_LOOKUP_CACHE;
    private static final ForgeConfigSpec.BooleanValue THROTTLE_CRAFTING_EXECUTION;
    private static final ForgeConfigSpec.IntValue MAX_EFFECTIVE_COPROCESSORS_PER_CPU;
    private static final ForgeConfigSpec.BooleanValue ADAPTIVE_CRAFTING_EXECUTION_BUDGET;
    private static final ForgeConfigSpec.IntValue TARGET_CRAFTING_EXECUTION_MILLIS;
    private static final ForgeConfigSpec.IntValue MINIMUM_ADAPTIVE_COPROCESSORS_PER_CPU;
    private static final ForgeConfigSpec.BooleanValue SHARED_CRAFTING_EXECUTION_BUDGET;
    private static final ForgeConfigSpec.IntValue SHARED_CRAFTING_EXECUTION_MILLIS_PER_GRID;
    private static final ForgeConfigSpec.IntValue MINIMUM_SHARED_OPERATIONS_PER_CPU;
    private static final ForgeConfigSpec.BooleanValue LOG_CRAFTING_EXECUTION_THROTTLING;
    private static final ForgeConfigSpec.BooleanValue THROTTLE_NEO_ECO_AE_EXECUTION;
    private static final ForgeConfigSpec.BooleanValue ENABLE_APPLIED_E_COMPATIBILITY;
    private static final ForgeConfigSpec.BooleanValue FORCE_AE2_PLANNER_FOR_APPLIED_E_PATTERNS;
    private static final ForgeConfigSpec.BooleanValue TREAT_APPLIED_E_PROVIDER_AS_DYNAMIC;
    private static final ForgeConfigSpec.BooleanValue ENABLE_GRID_TICK_BUDGET;
    private static final ForgeConfigSpec.BooleanValue DEFER_HEAVY_GRID_TICKABLES;
    private static final ForgeConfigSpec.IntValue GRID_TICK_BUDGET_MILLIS_PER_SERVER_TICK;
    private static final ForgeConfigSpec.IntValue GRID_TICK_MINIMUM_INTERVAL_TICKS;
    private static final ForgeConfigSpec.IntValue SLOW_GRID_TICKABLE_MICROS;
    private static final ForgeConfigSpec.IntValue SLOW_GRID_TICKABLE_BACKOFF_TICKS;
    private static final ForgeConfigSpec.BooleanValue BACKOFF_IDLE_GRID_TICKABLES;
    private static final ForgeConfigSpec.IntValue IDLE_GRID_TICKABLE_BACKOFF_AFTER_FAILURES;
    private static final ForgeConfigSpec.IntValue IDLE_GRID_TICKABLE_BACKOFF_TICKS;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> HEAVY_GRID_TICKABLE_CLASS_HINTS;
    private static final ForgeConfigSpec.BooleanValue LIMIT_IO_BUS_OPERATIONS_PER_TICK;
    private static final ForgeConfigSpec.IntValue MAX_IO_BUS_OPERATIONS_PER_TICK;
    private static final ForgeConfigSpec.BooleanValue THROTTLE_EXPORT_BUS_CRAFT_REQUESTS;
    private static final ForgeConfigSpec.IntValue EXPORT_BUS_CRAFT_FAILURE_COOLDOWN_TICKS;
    private static final ForgeConfigSpec.IntValue EXPORT_BUS_CRAFT_THROTTLE_CACHE_SIZE;
    private static final ForgeConfigSpec.BooleanValue LOG_GRID_TICK_BUDGET;
    private static final ForgeConfigSpec.BooleanValue CACHE_ADJACENT_CAPABILITY_LOOKUPS;
    private static final ForgeConfigSpec.BooleanValue CACHE_ADJACENT_CAPABILITIES_ACROSS_TICKS;
    private static final ForgeConfigSpec.BooleanValue CACHE_NEGATIVE_BUS_TRANSFER_SIMULATIONS;
    private static final ForgeConfigSpec.BooleanValue PRUNE_INVALID_CRAFTING_CANDIDATES;
    private static final ForgeConfigSpec.BooleanValue MEMOIZE_CRAFTING_CALCULATION_QUERIES;
    private static final ForgeConfigSpec.BooleanValue COALESCE_CRAFTING_PROVIDER_REFRESHES;
    private static final ForgeConfigSpec.BooleanValue TRACK_PROVIDER_PATTERN_GENERATIONS;
    private static final ForgeConfigSpec.BooleanValue INCREMENTAL_IO_PORT_PROCESSING;
    private static final ForgeConfigSpec.IntValue IO_PORT_CELL_SLOTS_PER_TICK;
    private static final ForgeConfigSpec.BooleanValue CACHE_IMPORT_BUS_LAST_SUCCESSFUL_SLOT;
    private static final ForgeConfigSpec.BooleanValue CACHE_EXPORT_BUS_CANDIDATE_KEYS;
    private static final ForgeConfigSpec.BooleanValue COALESCE_CLIENT_TERMINAL_VIEW_UPDATES;
    private static final ForgeConfigSpec.BooleanValue ASYNC_TERMINAL_SEARCH_SORT;
    private static final ForgeConfigSpec.IntValue ASYNC_TERMINAL_MINIMUM_ENTRIES;
    private static final ForgeConfigSpec.BooleanValue FIX_STUCK_AE2_SCROLLBAR_REPEAT;
    private static final ForgeConfigSpec.BooleanValue CACHE_CIRCUIT_CUTTER_RECIPES;
    private static final ForgeConfigSpec.BooleanValue CACHE_CIRCUIT_CUTTER_NEGATIVE_RESULTS;
    private static final ForgeConfigSpec.IntValue CIRCUIT_CUTTER_RECIPE_CACHE_SIZE;
    private static final ForgeConfigSpec.BooleanValue ENABLE_ADDON_MACHINE_OPTIMIZATIONS;
    private static final ForgeConfigSpec.BooleanValue CACHE_REACTION_CHAMBER_RECIPE;
    private static final ForgeConfigSpec.BooleanValue CACHE_AE2_OVERCLOCK_REFLECTION;
    private static final ForgeConfigSpec.BooleanValue USE_AE2_OVERCLOCK_METHOD_HANDLES;
    private static final ForgeConfigSpec.BooleanValue CACHE_AE2_OVERCLOCK_UPGRADE_COUNTS;
    private static final ForgeConfigSpec.BooleanValue CACHE_ASSEMBLER_MATRIX_THREAD_COUNTS;
    private static final ForgeConfigSpec.BooleanValue CACHE_ASSEMBLER_MATRIX_BUSY_COUNT;
    private static final ForgeConfigSpec.BooleanValue COALESCE_ASSEMBLER_MATRIX_STATUS_UPDATES;
    private static final ForgeConfigSpec.BooleanValue CACHE_ASSEMBLER_MATRIX_ROUTING;
    private static final ForgeConfigSpec.BooleanValue THROTTLE_STORAGE_WATCHER_UPDATES;
    private static final ForgeConfigSpec.IntValue STORAGE_WATCHER_UPDATE_INTERVAL_TICKS;
    private static final ForgeConfigSpec.BooleanValue THROTTLE_TERMINAL_INVENTORY_SNAPSHOTS;
    private static final ForgeConfigSpec.IntValue TERMINAL_INVENTORY_SNAPSHOT_INTERVAL_TICKS;
    private static final ForgeConfigSpec.BooleanValue CACHE_TERMINAL_CRAFTABLES;
    private static final ForgeConfigSpec.IntValue TERMINAL_CRAFTABLE_CACHE_TICKS;
    private static final ForgeConfigSpec.BooleanValue FLUSH_IMMEDIATELY_ON_SCREEN_OPEN;
    private static final ForgeConfigSpec.BooleanValue FLUSH_IMMEDIATELY_ON_CELL_CHANGE;
    private static final ForgeConfigSpec.BooleanValue FLUSH_IMMEDIATELY_ON_NETWORK_TOPOLOGY_CHANGE;
    private static final ForgeConfigSpec.IntValue MAXIMUM_BUFFERED_CHANGES;
    private static final ForgeConfigSpec.BooleanValue ENABLE_DEEP_AE2_REWRITE_FLAGS;
    private static final ForgeConfigSpec.BooleanValue DEEP_PATTERN_SELECTION_BY_AVAILABILITY;
    private static final ForgeConfigSpec.IntValue DEEP_PATTERN_SELECTION_MAXIMUM_CANDIDATES;
    private static final ForgeConfigSpec.BooleanValue DEEP_NETWORK_FORCE_UPDATE_COALESCING;
    private static final ForgeConfigSpec.IntValue DEEP_NETWORK_UPDATE_INTERVAL_TICKS;
    private static final ForgeConfigSpec.BooleanValue DEEP_VISIBLE_TERMINAL_RANGE_SYNC;
    private static final ForgeConfigSpec.IntValue DEEP_TERMINAL_RANGE_ENTRIES_PER_TICK;
    private static final ForgeConfigSpec.BooleanValue DEEP_P2P_TOPOLOGY_CHANGE_ONLY_RECHECK;
    private static final ForgeConfigSpec.IntValue DEEP_P2P_DUPLICATE_WINDOW_TICKS;
    private static final ForgeConfigSpec.BooleanValue DEEP_BUS_SEARCH_REWRITE;
    private static final ForgeConfigSpec.IntValue DEEP_BUS_FUZZY_CACHE_TICKS;
    private static final ForgeConfigSpec.IntValue DEEP_BUS_FUZZY_CACHE_SIZE;
    private static final ForgeConfigSpec.BooleanValue DEEP_FLUID_PATTERN_REWORK;
    private static final ForgeConfigSpec.BooleanValue LOG_DEEP_AE2_REWRITE_FLAGS;
    private static final ForgeConfigSpec.BooleanValue ENABLE_RECIPE_INTENT_BRIDGE;
    private static final ForgeConfigSpec.BooleanValue CAPTURE_PATTERN_PROVIDER_RECIPE_INTENTS;
    private static final ForgeConfigSpec.IntValue RECIPE_INTENT_TTL_TICKS;
    private static final ForgeConfigSpec.IntValue MAXIMUM_RECIPE_INTENT_ENTRIES;
    private static final ForgeConfigSpec.BooleanValue ENABLE_PATTERN_MICRO_BATCHING;
    private static final ForgeConfigSpec.IntValue MAX_PATTERN_EXECUTIONS_PER_MICRO_BATCH;
    private static final ForgeConfigSpec.BooleanValue REQUIRE_SINGLE_PATTERN_PROVIDER_TARGET;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> PATTERN_MICRO_BATCH_TARGET_NAMESPACES;
    private static final ForgeConfigSpec.BooleanValue ENABLE_TRANSACTIONAL_PATTERN_BATCHING;
    private static final ForgeConfigSpec.IntValue MAX_TRANSACTIONAL_PATTERN_BATCH_EXECUTIONS;
    private static final ForgeConfigSpec.BooleanValue ENABLE_SEQUENTIAL_PATTERN_PROVIDER_BATCH_ADAPTER;
    private static final ForgeConfigSpec.IntValue MAX_SEQUENTIAL_PROVIDER_EXECUTIONS_PER_CALL;
    private static final ForgeConfigSpec.BooleanValue ENABLE_INSTANT_PATTERN_DISPATCH;
    private static final ForgeConfigSpec.IntValue INSTANT_PATTERN_DISPATCH_TIME_BUDGET_MILLIS;
    private static final ForgeConfigSpec.IntValue INSTANT_PATTERN_DISPATCH_PROBE_OPERATIONS;
    private static final ForgeConfigSpec.IntValue INSTANT_PATTERN_DISPATCH_MAXIMUM_WAVE_OPERATIONS;
    private static final ForgeConfigSpec.IntValue MAX_INSTANT_PATTERN_DISPATCH_TRANSACTIONS;
    private static final ForgeConfigSpec.BooleanValue REQUIRE_SINGLE_TRANSACTIONAL_BATCH_TARGET;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> TRANSACTIONAL_BATCH_TARGET_NAMESPACES;
    private static final ForgeConfigSpec.BooleanValue ENABLE_GTCEU_RECIPE_INTENT_FAST_PATH;
    private static final ForgeConfigSpec.IntValue GTCEU_RECIPE_INTENT_MAXIMUM_CANDIDATES;
    private static final ForgeConfigSpec.IntValue GTCEU_RECIPE_INTENT_INDEX_CACHE_SIZE;
    private static final ForgeConfigSpec.IntValue GTCEU_RECIPE_INTENT_SEARCH_RADIUS;
    private static final ForgeConfigSpec.IntValue GTCEU_RECIPE_INTENT_NEARBY_MAXIMUM_ENTRIES;
    private static final ForgeConfigSpec.BooleanValue LOG_GTCEU_RECIPE_INTENT_FAST_PATH;
    private static final ForgeConfigSpec.BooleanValue ENABLE_MEKANISM_RECIPE_INTENT_FAST_PATH;
    private static final ForgeConfigSpec.IntValue MEKANISM_RECIPE_INTENT_MAXIMUM_CANDIDATES;
    private static final ForgeConfigSpec.IntValue MEKANISM_RECIPE_INTENT_INDEX_CACHE_SIZE;
    private static final ForgeConfigSpec.BooleanValue CACHE_RESOLVED_RECIPE_INTENTS;
    private static final ForgeConfigSpec.IntValue RESOLVED_RECIPE_INTENT_CACHE_SIZE;
    private static final ForgeConfigSpec.BooleanValue LOG_MEKANISM_RECIPE_INTENT_FAST_PATH;
    private static final ForgeConfigSpec.BooleanValue ENABLE_CREATE_RECIPE_INTENT_FAST_PATH;
    private static final ForgeConfigSpec.BooleanValue LOG_CAPTURED_RECIPE_INTENTS;
    private static final ForgeConfigSpec.BooleanValue LOG_RECIPE_INTENT_REGISTRY_EVICTIONS;
    private static final ForgeConfigSpec.BooleanValue LOG_SLOW_CRAFT_CALCULATIONS;
    private static final ForgeConfigSpec.IntValue SLOW_CRAFT_CALCULATION_MILLIS;
    private static final ForgeConfigSpec.BooleanValue LOG_CACHE_STATISTICS;
    private static final ForgeConfigSpec.BooleanValue ENABLE_AQE_BIG_CRAFTING_PROFILE;
    private static final ForgeConfigSpec.BooleanValue ENABLE_EXPERIMENTAL_CRAFTING_ENGINE;
    private static final ForgeConfigSpec.BooleanValue ENABLE_CRAFTING_ENGINE_SHADOW_MODE;
    private static final ForgeConfigSpec.BooleanValue LOG_CRAFTING_ENGINE_SHADOW_MISMATCHES;
    private static final ForgeConfigSpec.IntValue CRAFTING_ENGINE_SHADOW_MAXIMUM_PATTERNS;
    private static final ForgeConfigSpec.IntValue AUTHORITATIVE_MINIMUM_SHADOW_MATCHES;
    private static final ForgeConfigSpec.BooleanValue REQUIRE_AQE_BIG_PLAN_SHADOW_QUALIFICATION;
    private static final ForgeConfigSpec.BooleanValue ENABLE_COMPILED_CRAFTING_GRAPH;
    private static final ForgeConfigSpec.BooleanValue ENABLE_AUTHORITATIVE_COMPILED_PLANNER;
    private static final ForgeConfigSpec.BooleanValue ENABLE_CHECKED_AE2_CRAFTING_ARITHMETIC;
    private static final ForgeConfigSpec.BooleanValue ENABLE_TRANSACTIONAL_BATCHING_V2;
    private static final ForgeConfigSpec.BooleanValue ENABLE_GTCEU_NATIVE_BATCHING;
    private static final ForgeConfigSpec.BooleanValue ENABLE_MEKANISM_NATIVE_BATCHING;
    private static final ForgeConfigSpec.BooleanValue ENABLE_FAIR_CRAFTING_JOB_SCHEDULER;
    private static final ForgeConfigSpec.IntValue FAIR_SCHEDULER_OPERATIONS_PER_TICK;
    private static final ForgeConfigSpec.IntValue FAIR_SCHEDULER_QUANTUM;
    private static final ForgeConfigSpec.IntValue FAIR_SCHEDULER_TIME_BUDGET_MILLIS;
    private static final ForgeConfigSpec.BooleanValue PERSIST_BATCH_TRANSACTION_JOURNAL;
    private static final ForgeConfigSpec.IntValue BATCH_TRANSACTION_JOURNAL_MAXIMUM_ENTRIES;
    private static final ForgeConfigSpec.IntValue BATCH_TRANSACTION_RECONCILIATION_INTERVAL_TICKS;
    private static final ForgeConfigSpec.IntValue NATIVE_BATCH_MAXIMUM_EXECUTIONS;
    private static final ForgeConfigSpec.BooleanValue ENABLE_BIG_INTEGER_CRAFTING_BACKEND;
    private static final ForgeConfigSpec.BooleanValue ENABLE_ATOMIC_BIG_CAPACITY_PLANS;
    private static final ForgeConfigSpec.BooleanValue ENABLE_BIG_INTEGER_GAMEPLAY_EXECUTION;
    private static final ForgeConfigSpec.IntValue BIG_INTEGER_MAXIMUM_BITS;
    private static final ForgeConfigSpec.IntValue BIG_INTEGER_EXECUTION_WINDOW;
    private static final ForgeConfigSpec.IntValue BIG_INTEGER_MAXIMUM_WINDOW_CALCULATIONS_PER_TICK;
    private static final ForgeConfigSpec.IntValue BIG_INTEGER_RETRY_BACKOFF_TICKS;
    private static final ForgeConfigSpec.IntValue BIG_INTEGER_STATUS_PAGE_ENTRIES;
    private static final ForgeConfigSpec.IntValue BIG_INTEGER_RUNTIME_COUNT_BUDGET_MIB;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("general");
        ENABLE_OPTIMIZER = builder
                .comment("Master switch for all AE2 Crafting Optimizer mixin behavior.")
                .define("enableOptimizer", true);
        builder.pop();

        builder.push("craftingCalculation");
        TWO_STAGE_MISSING_PREVIEW = builder
                .comment("Disabled by default. The preliminary missing preview mixins are not applied in the conservative build because they can interfere with AE2's Craft Confirm calculation screen.")
                .define("twoStageMissingPreview", false);
        CANCEL_CALCULATION_AFTER_PRELIMINARY_MISSING_PREVIEW = builder
                .comment("Disabled by default. Kept only for config compatibility with older builds.")
                .define("cancelCalculationAfterPreliminaryMissingPreview", false);
        SKIP_CALCULATION_ON_CACHED_MISSING_PREVIEW = builder
                .comment("Legacy safety switch. Immediate no-calculation cache replies are disabled by default because they can arrive before the client Craft Confirm screen is ready.")
                .define("skipCalculationOnCachedMissingPreview", false);
        USE_MISSING_PREVIEW_CACHE = builder
                .comment("Disabled by default with the conservative no-Craft-Confirm-mixin build.")
                .define("useMissingPreviewCache", false);
        MISSING_PREVIEW_CACHE_SIZE = builder
                .comment("Maximum number of preliminary missing craft entries kept in memory.")
                .defineInRange("missingPreviewCacheSize", 2048, 0, 65536);
        MISSING_PREVIEW_CACHE_TTL_SECONDS = builder
                .comment("Time before preliminary missing craft cache entries expire.")
                .defineInRange("missingPreviewCacheTtlSeconds", 300, 1, 86400);
        INVALIDATE_CACHE_ON_STORAGE_CHANGE = builder
                .comment("Clear preliminary missing preview cache when AE2 invalidates its storage cache.")
                .define("invalidateCacheOnStorageChange", true);
        INVALIDATE_CACHE_ON_PATTERN_CHANGE = builder
                .comment("Clear preliminary missing preview cache when AE2 crafting providers or pattern hosts change.")
                .define("invalidateCacheOnPatternChange", true);
        MINIMUM_CALCULATION_MILLIS_FOR_PREVIEW = builder
                .comment("Only send preliminary missing previews after a calculation has been open for this many milliseconds. 0 sends as soon as the first missing branch is known.")
                .defineInRange("minimumCalculationMillisForPreview", 100, 0, 60_000);
        MINIMUM_REQUESTED_AMOUNT_FOR_PREVIEW = builder
                .comment("Do not apply two-stage missing preview to small craft requests. Small requests stay on AE2's normal calculation path.")
                .defineInRange("minimumRequestedAmountForPreview", 1024L, 1L, Long.MAX_VALUE);
        PREVIEW_MAXIMUM_ENTRIES = builder
                .comment("Maximum number of preliminary missing entries shown. The current safe implementation supports one entry.")
                .defineInRange("previewMaximumEntries", 1, 0, 1);
        HEAVY_PROCESS_HINTS = builder
                .comment("Output id or key-string fragments treated as heavy process families. Useful for Astral-style celestial/electronic process lines. These hints prioritize cached preliminary previews; they never change AE2's final crafting result.")
                .defineListAllowEmpty("heavyProcessHints", List.of(
                        "astral_mekanism:",
                        "celestial",
                        "electronic"
                ), value -> value instanceof String);
        DEDUPLICATE_ACTIVE_CRAFTING_CALCULATIONS = builder
                .comment("Share an already-running AE2 crafting calculation when the exact same requester asks for the exact same output, amount, and calculation strategy again. This does not cache completed plans and does not alter AE2's final result.")
                .define("deduplicateActiveCraftingCalculations", true);
        ACTIVE_CALCULATION_DEDUPLICATION_WINDOW_TICKS = builder
                .comment("Maximum age of an active calculation entry that can be reused, in server ticks. Completed, cancelled, or expired entries are ignored.")
                .defineInRange("activeCalculationDeduplicationWindowTicks", 200, 1, 20 * 60);
        LOG_CRAFTING_CALCULATION_DEDUPLICATION = builder
                .comment("Log active crafting calculation single-flight hits and table clears. Disabled by default to avoid log spam.")
                .define("logCraftingCalculationDeduplication", false);
        CACHE_COMPLETED_CRAFTING_PLANS = builder
                .comment("Cache short-lived completed crafting plans for identical requests. By default only missing/simulation plans are reused; successful plans require cacheSuccessfulCompletedCraftingPlans.")
                .define("cacheCompletedCraftingPlans", true);
        CACHE_SUCCESSFUL_COMPLETED_CRAFTING_PLANS = builder
                .comment("Also cache completed successful crafting plans. Disabled by default because successful plans can become stale when storage changes between calculation and submission.")
                .define("cacheSuccessfulCompletedCraftingPlans", false);
        COMPLETED_CRAFTING_PLAN_CACHE_SIZE = builder
                .comment("Maximum completed crafting plans cached per JVM.")
                .defineInRange("completedCraftingPlanCacheSize", 1024, 0, 65536);
        COMPLETED_CRAFTING_PLAN_CACHE_TTL_TICKS = builder
                .comment("Time-to-live for completed crafting plan cache entries, in server ticks. Storage or crafting topology invalidation clears this earlier.")
                .defineInRange("completedCraftingPlanCacheTtlTicks", 40, 1, 20 * 60);
        FAST_FAIL_MISSING_CRAFTS = builder
                .comment(
                        "Experimental opt-in: for REPORT_MISSING_ITEMS requests, return an immediate missing-only plan when a strict deterministic preflight proves the craft is impossible.",
                        "This deliberately ends AE2's full calculation after the first proven blocker, so it is disabled by default. Item, fluid, and chemical keys use the same proof path.")
                .define("fastFailMissingCrafts", false);
        MINIMUM_REQUESTED_AMOUNT_FOR_FAST_FAIL = builder
                .comment("Do not run deterministic missing preflight below this requested amount.")
                .defineInRange("minimumRequestedAmountForFastFail", 1L, 1L, Long.MAX_VALUE);
        DETERMINISTIC_PREFLIGHT_MAX_DEPTH = builder
                .comment("Maximum recursive depth for deterministic missing preflight.")
                .defineInRange("deterministicPreflightMaxDepth", 64, 1, 1024);
        DETERMINISTIC_PREFLIGHT_MAX_NODES = builder
                .comment("Maximum recipe graph nodes visited by deterministic missing preflight before falling back to AE2.")
                .defineInRange("deterministicPreflightMaxNodes", 4096, 1, 1_000_000);
        LOG_FAST_FAIL_MISSING_CRAFTS = builder
                .comment("Log when deterministic missing preflight returns a missing-only plan.")
                .define("logFastFailMissingCrafts", false);
        CACHE_PATTERN_LOOKUPS = builder
                .comment("Cache AE2 CraftingService.getCraftingFor(output) lookups until crafting providers or network nodes change. This is a read-through cache only.")
                .define("cachePatternLookups", true);
        PATTERN_LOOKUP_CACHE_SIZE = builder
                .comment("Maximum cached pattern lookup entries per JVM.")
                .defineInRange("patternLookupCacheSize", 8192, 0, 1_000_000);
        CACHE_CRAFTABLE_SETS = builder
                .comment("Cache CraftingService.getCraftables(filter) results until crafting providers or network nodes change. This targets terminals and provider scans; it does not cache completed crafting plans.")
                .define("cacheCraftableSets", false);
        CRAFTABLE_SET_CACHE_SIZE = builder
                .comment("Maximum cached craftable-set entries per JVM.")
                .defineInRange("craftableSetCacheSize", 256, 0, 65536);
        LOG_PATTERN_LOOKUP_CACHE = builder
                .comment("Log pattern lookup cache hits and clears. Disabled by default to avoid log spam.")
                .define("logPatternLookupCache", false);
        builder.pop();

        builder.push("craftingExecution");
        THROTTLE_CRAFTING_EXECUTION = builder
                .comment("Limit how many pattern pushes a single crafting CPU may burst through AE2's crafting execution window. This is the main TPS protection for giant CPUs: capacity and displayed co-processors stay unchanged, but per-tick pattern pushes are bounded.")
                .define("throttleCraftingExecution", true);
        MAX_EFFECTIVE_COPROCESSORS_PER_CPU = builder
                .comment("Maximum effective co-processors one CPU may spend in AE2's crafting execution window. The default matches AQE's non-experimental full structure. Raise to 2147483646 only for explicit maximum-value testing.")
                .defineInRange("maxEffectiveCoprocessorsPerCpu", DEFAULT_EFFECTIVE_COPROCESSORS_PER_CPU, 1, MAX_SAFE_EFFECTIVE_COPROCESSORS);
        ADAPTIVE_CRAFTING_EXECUTION_BUDGET = builder
                .comment("Adapt each active crafting CPU's effective execution budget based on observed server-side execution time. This keeps CrazyAE-class CPUs useful without letting one CPU spend the whole server tick.")
                .define("adaptiveCraftingExecutionBudget", true);
        TARGET_CRAFTING_EXECUTION_MILLIS = builder
                .comment("Target maximum time in milliseconds that one crafting CPU execution burst should spend in a server tick before its adaptive budget is reduced.")
                .defineInRange("targetCraftingExecutionMillis", 4, 1, 50);
        MINIMUM_ADAPTIVE_COPROCESSORS_PER_CPU = builder
                .comment("Lowest adaptive effective co-processor budget per CPU. Small CPUs below this value are not slowed further.")
                .defineInRange("minimumAdaptiveCoprocessorsPerCpu", 1024, 1, MAX_SAFE_EFFECTIVE_COPROCESSORS);
        SHARED_CRAFTING_EXECUTION_BUDGET = builder
                .comment("Share a real-time execution budget between all standard AE2 crafting CPUs on the same ME grid. This only paces pattern pushes after planning has completed.")
                .define("sharedCraftingExecutionBudget", true);
        SHARED_CRAFTING_EXECUTION_MILLIS_PER_GRID = builder
                .comment("Target total time in milliseconds that standard AE2 crafting CPUs on one ME grid may spend pushing patterns in one server tick. Every active CPU still receives a small progress allowance.")
                .defineInRange("sharedCraftingExecutionMillisPerGrid", 8, 1, 45);
        MINIMUM_SHARED_OPERATIONS_PER_CPU = builder
                .comment("Minimum pattern-push operations granted to an active CPU after its ME grid has consumed the shared tick budget. Keep this low to prevent many CPUs from recreating a large burst.")
                .defineInRange("minimumSharedOperationsPerCpu", 1, 1, 65536);
        LOG_CRAFTING_EXECUTION_THROTTLING = builder
                .comment("Log when a crafting CPU's execution burst is capped. Disabled by default to avoid log spam on large systems.")
                .define("logCraftingExecutionThrottling", false);
        builder.pop();

        builder.push("compatibility");
        builder.push("appliedE");
        ENABLE_APPLIED_E_COMPATIBILITY = builder
                .comment(
                        "Enable conservative compatibility for AppliedE and AppliedE TPS Fix.",
                        "ACO does not replace EMC accounting or AppliedE's temporary-pattern lifecycle.")
                .define("enableAppliedECompatibility", true);
        FORCE_AE2_PLANNER_FOR_APPLIED_E_PATTERNS = builder
                .comment(
                        "Keep AppliedE TransmutationPattern routes on AE2's original planner.",
                        "AppliedE creates request-sized temporary patterns inside AE2's crafting tree, so compiling them as fixed recipes would bypass required lifecycle hooks.")
                .define("forceAe2PlannerForTransmutationPatterns", true);
        TREAT_APPLIED_E_PROVIDER_AS_DYNAMIC = builder
                .comment(
                        "Treat AppliedE's EMC Module as a dynamic crafting provider.",
                        "Repeated same-tick notifications are still coalesced, but ACO does not enumerate every EMC pattern merely to decide whether the final refresh may be discarded.")
                .define("treatAppliedEProviderAsDynamic", true);
        builder.pop();
        builder.push("neoEcoAe");
        THROTTLE_NEO_ECO_AE_EXECUTION = builder
                .comment(
                        "Apply ACO's adaptive per-CPU and shared per-grid pattern-push budgets to Neo ECO AE Extension's custom ECO CPU.",
                        "Neo ECO's own normal, batch, and aggressive fast paths remain authoritative; ACO only bounds their per-tick execution window.",
                        "Supported target: Neo ECO AE Extension 20.3.x.")
                .define("throttleNeoEcoAeExecution", true);
        builder.pop();
        builder.pop();

        builder.push("gridTickBudget");
        ENABLE_GRID_TICK_BUDGET = builder
                .comment("Enable server-side budget controls for selected AE2 grid tickables such as IO ports, import/export buses, and ExtendedAE circuit cutters. Disabled by default because AE2 buses are correctness-sensitive and must be opt-in tested per pack.")
                .define("enableGridTickBudget", false);
        DEFER_HEAVY_GRID_TICKABLES = builder
                .comment("When enabled, selected AE2 grid tickables are deferred with TickRateModulation.SLOWER after the per-server-tick budget is spent or after a slow tick triggers backoff.")
                .define("deferHeavyGridTickables", false);
        GRID_TICK_BUDGET_MILLIS_PER_SERVER_TICK = builder
                .comment("Total server tick time budget, in milliseconds, spent by selected AE2 grid tickables before later matching devices are deferred. This is not a hard JVM preemption; it only affects future tickable calls in the same tick.")
                .defineInRange("gridTickBudgetMillisPerServerTick", 6, 1, 45);
        GRID_TICK_MINIMUM_INTERVAL_TICKS = builder
                .comment("Minimum spacing between executions for selected AE2 grid tickables. 1 keeps normal AE2 scheduling unless the budget or slow backoff triggers.")
                .defineInRange("gridTickMinimumIntervalTicks", 1, 1, 200);
        SLOW_GRID_TICKABLE_MICROS = builder
                .comment("Diagnostic threshold for one selected AE2 grid tickable call, in microseconds.")
                .defineInRange("slowGridTickableMicros", 2000, 1, 1_000_000);
        SLOW_GRID_TICKABLE_BACKOFF_TICKS = builder
                .comment("If one selected AE2 grid tickable call exceeds slowGridTickableMicros, defer that device for this many ticks. 0 disables per-device slow backoff.")
                .defineInRange("slowGridTickableBackoffTicks", 2, 0, 200);
        BACKOFF_IDLE_GRID_TICKABLES = builder
                .comment("Apply a short backoff to selected AE2 grid tickables that repeatedly report no work. This targets empty import buses, full export targets, blocked IO ports, and similar failed polling loops.")
                .define("backoffIdleGridTickables", false);
        IDLE_GRID_TICKABLE_BACKOFF_AFTER_FAILURES = builder
                .comment("How many consecutive IDLE/SLOWER returns a selected tickable may produce before idle backoff starts.")
                .defineInRange("idleGridTickableBackoffAfterFailures", 4, 1, 1000);
        IDLE_GRID_TICKABLE_BACKOFF_TICKS = builder
                .comment("How many ticks to defer a selected repeatedly-idle tickable.")
                .defineInRange("idleGridTickableBackoffTicks", 5, 1, 200);
        HEAVY_GRID_TICKABLE_CLASS_HINTS = builder
                .comment("Class-name fragments selected for AE2 grid tick budgeting. Keep this narrow; matching devices may run later under load.")
                .defineListAllowEmpty("heavyGridTickableClassHints", List.of(
                        "appeng.parts.automation.ImportBusPart",
                        "appeng.parts.automation.ExportBusPart",
                        "appeng.blockentity.storage.IOPortBlockEntity",
                        "com.glodblock.github.extendedae.common.tileentities.TileCircuitCutter",
                        "com.glodblock.github.extendedae.common.tileentities.TileExIOPort",
                        "com.glodblock.github.extendedae.common.parts.PartExImportBus",
                        "com.glodblock.github.extendedae.common.parts.PartExExportBus",
                        "com.glodblock.github.extendedae.common.parts.PartPreciseExportBus",
                        "com.glodblock.github.extendedae.common.parts.PartTagExportBus",
                        "com.glodblock.github.extendedae.common.parts.PartThresholdExportBus",
                        "com.glodblock.github.extendedae.common.parts.PartModExportBus"
                ), value -> value instanceof String);
        LIMIT_IO_BUS_OPERATIONS_PER_TICK = builder
                .comment("Cap AE2 import/export bus operations per tick after other mods apply their speed-card changes. This does not change storage contents or filters; it only prevents one bus from doing an extreme burst in one tick.")
                .define("limitIoBusOperationsPerTick", false);
        MAX_IO_BUS_OPERATIONS_PER_TICK = builder
                .comment("Maximum operations per tick for AE2 import/export buses when limitIoBusOperationsPerTick is enabled. Vanilla AE2 tops out at 96; this default still allows accelerated buses while avoiding integer-saturated bursts.")
                .defineInRange("maxIoBusOperationsPerTick", 4096, 1, MAX_SAFE_EFFECTIVE_COPROCESSORS);
        THROTTLE_EXPORT_BUS_CRAFT_REQUESTS = builder
                .comment("Throttle repeated failed crafting requests from AE2 MultiCraftingTracker owners such as export buses with crafting cards. Successful or in-progress jobs are not throttled.")
                .define("throttleExportBusCraftRequests", false);
        EXPORT_BUS_CRAFT_FAILURE_COOLDOWN_TICKS = builder
                .comment("Cooldown after a crafting request finishes without producing a crafting link, in ticks.")
                .defineInRange("exportBusCraftFailureCooldownTicks", 40, 1, 20 * 60);
        EXPORT_BUS_CRAFT_THROTTLE_CACHE_SIZE = builder
                .comment("Maximum remembered failed export-bus-style crafting requests.")
                .defineInRange("exportBusCraftThrottleCacheSize", 4096, 1, 1_000_000);
        LOG_GRID_TICK_BUDGET = builder
                .comment("Log slow/deferred selected AE2 grid tickables. Disabled by default to avoid log spam.")
                .define("logGridTickBudget", false);
        builder.pop();

        builder.push("uelOptimizations");
        CACHE_ADJACENT_CAPABILITY_LOOKUPS = builder
                .comment("Reuse a non-null adjacent Forge capability for the remainder of the current server tick. The block entity identity is checked on every lookup, and missing capabilities are never cached.")
                .define("cacheAdjacentCapabilityLookups", true);
        CACHE_ADJACENT_CAPABILITIES_ACROSS_TICKS = builder
                .comment("Keep a successful adjacent capability across ticks until Forge invalidates its LazyOptional. The block entity identity is still checked on every access. Disabled by default for unusual capability providers that do not invalidate correctly.")
                .define("cacheAdjacentCapabilitiesAcrossTicks", false);
        CACHE_NEGATIVE_BUS_TRANSFER_SIMULATIONS = builder
                .comment("Remember exact failed import/export insertion simulations for one server tick. Successful simulations and real transfers are always executed normally.")
                .define("cacheNegativeBusTransferSimulations", true);
        PRUNE_INVALID_CRAFTING_CANDIDATES = builder
                .comment("Remove only null, duplicate-by-identity, or structurally invalid pattern candidates before AE2 builds crafting branches. Inventory availability is never used to reject a valid pattern.")
                .define("pruneInvalidCraftingCandidates", true);
        MEMOIZE_CRAFTING_CALCULATION_QUERIES = builder
                .comment("Memoize only calculation-invariant AE2 queries inside one crafting job: emit checks, pattern lists, fuzzy craftable alternatives, and container returns. Mutable simulated inventory amounts are never cached.")
                .define("memoizeCraftingCalculationQueries", true);
        COALESCE_CRAFTING_PROVIDER_REFRESHES = builder
                .comment("Combine repeated refreshes of the same Pattern Provider during one server tick. Pending refreshes are flushed before every crafting read and at server end tick.")
                .define("coalesceCraftingProviderRefreshes", true);
        TRACK_PROVIDER_PATTERN_GENERATIONS = builder
                .comment("Fingerprint Pattern Provider content and rebuild AE2's provider indexes only when patterns, outputs, inputs, emitables, or priority actually change. Cleared on datapack reload.")
                .define("trackProviderPatternGenerations", true);
        INCREMENTAL_IO_PORT_PROCESSING = builder
                .comment("Process AE2 IO Port cell slots from a persistent round-robin cursor instead of restarting at slot zero. Every selected cell transfer still uses AE2's original atomic transfer routine.")
                .define("incrementalIoPortProcessing", true);
        IO_PORT_CELL_SLOTS_PER_TICK = builder
                .comment("Maximum IO Port input-cell slots inspected per grid tick when incremental processing is enabled.")
                .defineInRange("ioPortCellSlotsPerTick", 2, 1, 6);
        CACHE_IMPORT_BUS_LAST_SUCCESSFUL_SLOT = builder
                .comment("Compatibility key. The custom Import Bus transfer Mixin is unregistered; AE2 always owns extraction and insertion.")
                .define("cacheImportBusLastSuccessfulSlot", false);
        CACHE_EXPORT_BUS_CANDIDATE_KEYS = builder
                .comment("Reuse Export Bus configured candidate keys until the bus configuration is changed. Fuzzy lookup and actual extraction/insertion remain validated by AE2.")
                .define("cacheExportBusCandidateKeys", true);
        COALESCE_CLIENT_TERMINAL_VIEW_UPDATES = builder
                .comment("Allow at most one immediate ME terminal filter/sort rebuild per client tick. Experimental and disabled by default to keep clickable virtual slots synchronized with the current repository generation.")
                .define("coalesceClientTerminalViewUpdates", false);
        ASYNC_TERMINAL_SEARCH_SORT = builder
                .comment("Project terminal names, IDs, tags, tooltips, and sort keys on the client thread, then offload only immutable search matching and sorting. Stale generations are discarded. Disabled by default.")
                .define("asyncTerminalSearchSort", false);
        ASYNC_TERMINAL_MINIMUM_ENTRIES = builder
                .comment("Minimum terminal entry count before the safe asynchronous amount-sort path is used.")
                .defineInRange("asyncTerminalMinimumEntries", 2048, 128, 1_000_000);
        FIX_STUCK_AE2_SCROLLBAR_REPEAT = builder
                .comment("Stop AE2 scrollbar page-repeat when the physical left mouse button is already released. Prevents Pattern Access Terminal scrolling from continuing after another screen mod loses the mouse-up event.")
                .define("fixStuckAe2ScrollbarRepeat", true);
        CACHE_CIRCUIT_CUTTER_RECIPES = builder
                .comment("Share validated ExtendedAE Circuit Cutter recipe candidates between machines with identical item/fluid inputs. ExtendedAE still performs its own test before a cached recipe is used.")
                .define("cacheCircuitCutterRecipes", true);
        CACHE_CIRCUIT_CUTTER_NEGATIVE_RESULTS = builder
                .comment("Share exact ExtendedAE Circuit Cutter no-recipe results for identical item/fluid inputs. Negative results are invalidated on input change naturally and globally on datapack reload.")
                .define("cacheCircuitCutterNegativeResults", true);
        CIRCUIT_CUTTER_RECIPE_CACHE_SIZE = builder
                .comment("Maximum validated ExtendedAE Circuit Cutter input signatures retained until a datapack reload.")
                .defineInRange("circuitCutterRecipeCacheSize", 4096, 16, 262144);
        builder.pop();

        builder.push("addonMachineOptimizations");
        ENABLE_ADDON_MACHINE_OPTIMIZATIONS = builder
                .comment("Master switch for conservative AdvancedAE, ExtendedAE, and AE2 Overclock machine optimizations. These paths cache duplicate lookups only; they do not reduce machine throughput or change recipes.")
                .define("enableAddonMachineOptimizations", true);
        CACHE_REACTION_CHAMBER_RECIPE = builder
                .comment("Reuse the AdvancedAE Reaction Chamber recipe already resolved after an inventory change instead of immediately searching the same inputs again.")
                .define("cacheReactionChamberRecipe", true);
        CACHE_AE2_OVERCLOCK_REFLECTION = builder
                .comment("Cache Field and Method metadata used by AE2 Overclock's own runtime helper classes. Invocation and recipe validation still run normally.")
                .define("cacheAe2OverclockReflection", true);
        USE_AE2_OVERCLOCK_METHOD_HANDLES = builder
                .comment("Invoke cached Methods used by AE2 Overclock runtime helpers through prebuilt MethodHandles, with reflection fallback.")
                .define("useAe2OverclockMethodHandles", true);
        CACHE_AE2_OVERCLOCK_UPGRADE_COUNTS = builder
                .comment("Reuse AE2 Overclock overclock/parallel card counts for the same machine during one server tick. Upgrade changes are visible no later than the next tick.")
                .define("cacheAe2OverclockUpgradeCounts", true);
        CACHE_ASSEMBLER_MATRIX_THREAD_COUNTS = builder
                .comment("Reuse ExtendedAE Assembly Matrix crafter used-thread counts during one server tick. The cache is invalidated before execution and on every job, inventory, state, load, or stop change.")
                .define("cacheAssemblerMatrixThreadCounts", true);
        CACHE_ASSEMBLER_MATRIX_BUSY_COUNT = builder
                .comment("Reuse the complete ExtendedAE Assembly Matrix busy-thread total during one server tick, invalidating it whenever a crafter status is updated.")
                .define("cacheAssemblerMatrixBusyCount", true);
        COALESCE_ASSEMBLER_MATRIX_STATUS_UPDATES = builder
                .comment("Coalesce identical ExtendedAE Assembly Matrix visual/status broadcasts for the same cluster and server tick. Structure formation and destruction are not skipped.")
                .define("coalesceAssemblerMatrixStatusUpdates", true);
        CACHE_ASSEMBLER_MATRIX_ROUTING = builder
                .comment("Reuse the last available ExtendedAE Assembly Matrix crafter until its thread state or the multiblock structure changes, validating capacity before every use.")
                .define("cacheAssemblerMatrixRouting", true);
        builder.pop();

        builder.push("storageSync");
        THROTTLE_STORAGE_WATCHER_UPDATES = builder
                .comment("Throttle AE2 StorageService watcher updates. ME terminal/monitor visible storage updates may be delayed by the configured interval, but storage contents are not changed.")
                .define("throttleStorageWatcherUpdates", false);
        STORAGE_WATCHER_UPDATE_INTERVAL_TICKS = builder
                .comment("Storage watcher update interval in ticks when throttling is enabled. 1 is vanilla behavior.")
                .defineInRange("storageWatcherUpdateIntervalTicks", 4, 1, 40);
        THROTTLE_TERMINAL_INVENTORY_SNAPSHOTS = builder
                .comment("Throttle ME terminal server-side inventory snapshots. Disabled by default because stale zero-stock entries can interfere with interactive terminal slots in heavily modified clients.")
                .define("throttleTerminalInventorySnapshots", false);
        TERMINAL_INVENTORY_SNAPSHOT_INTERVAL_TICKS = builder
                .comment("How often an open ME terminal should rebuild its server-side available-stack snapshot.")
                .defineInRange("terminalInventorySnapshotIntervalTicks", 4, 1, 40);
        CACHE_TERMINAL_CRAFTABLES = builder
                .comment("Cache the craftable set used by open ME terminals for a few ticks. Disabled by default so zero-stock transitions use AE2's immediate terminal state.")
                .define("cacheTerminalCraftables", false);
        TERMINAL_CRAFTABLE_CACHE_TICKS = builder
                .comment("How many ticks an open ME terminal may reuse its craftable set.")
                .defineInRange("terminalCraftableCacheTicks", 4, 1, 40);
        FLUSH_IMMEDIATELY_ON_SCREEN_OPEN = builder
                .comment("Reserved safety option for future visible-sync buffering. Current implementation does not delay screen-open synchronization.")
                .define("flushImmediatelyOnScreenOpen", true);
        FLUSH_IMMEDIATELY_ON_CELL_CHANGE = builder
                .comment("Reserved safety option for future visible-sync buffering. Current implementation clears preview cache on storage invalidation instead of buffering cell changes.")
                .define("flushImmediatelyOnCellChange", true);
        FLUSH_IMMEDIATELY_ON_NETWORK_TOPOLOGY_CHANGE = builder
                .comment("Reserved safety option for future visible-sync buffering. Current implementation does not buffer topology changes.")
                .define("flushImmediatelyOnNetworkTopologyChange", true);
        MAXIMUM_BUFFERED_CHANGES = builder
                .comment("Reserved safety ceiling for future visible-sync buffering. Current implementation does not buffer individual storage changes.")
                .defineInRange("maximumBufferedChanges", 4096, 1, 1_000_000);
        builder.pop();

        builder.push("deepAe2Rewrite");
        ENABLE_DEEP_AE2_REWRITE_FLAGS = builder
                .comment("Master switch for the independently configurable UEL/GTNH-inspired deep optimizations below. Disable this to return every deep path to AE2 behavior without changing the individual settings.")
                .define("enableDeepAe2RewriteFlags", true);
        DEEP_PATTERN_SELECTION_BY_AVAILABILITY = builder
                .comment("Experimental: reorder equivalent output patterns by how many direct inputs are currently available. AE2 still validates every branch and decides whether the craft succeeds.")
                .define("patternSelectionByAvailability", false);
        DEEP_PATTERN_SELECTION_MAXIMUM_CANDIDATES = builder
                .comment("Maximum number of patterns for one output that the availability sorter may inspect. Larger sets retain AE2's original order to bound calculation overhead.")
                .defineInRange("patternSelectionMaximumCandidates", 64, 2, 4096);
        DEEP_NETWORK_FORCE_UPDATE_COALESCING = builder
                .comment("Coalesce AE2 StorageService watcher rebuilds into a short interval. Direct inventory operations remain immediate; only cached aggregate/watcher refresh work is paced.")
                .define("networkForceUpdateCoalescing", false);
        DEEP_NETWORK_UPDATE_INTERVAL_TICKS = builder
                .comment("Maximum interval between coalesced StorageService aggregate rebuilds.")
                .defineInRange("networkUpdateIntervalTicks", 2, 1, 40);
        DEEP_VISIBLE_TERMINAL_RANGE_SYNC = builder
                .comment("Split terminal full/delta synchronization into bounded rolling ranges. Experimental and disabled by default because interactive virtual slots require one coherent AE2 update generation.")
                .define("visibleTerminalRangeSync", false);
        DEEP_TERMINAL_RANGE_ENTRIES_PER_TICK = builder
                .comment("Maximum terminal entries serialized per menu tick while rolling range synchronization is active.")
                .defineInRange("terminalRangeEntriesPerTick", 4096, 64, 32767);
        DEEP_P2P_TOPOLOGY_CHANGE_ONLY_RECHECK = builder
                .comment("AE2 15.4.10 already reevaluates P2P topology only on structural changes. This option coalesces duplicate full input-tunnel wake sweeps caused by boot/power events and never suppresses add/remove/frequency callbacks.")
                .define("p2pTopologyChangeOnlyRecheck", true);
        DEEP_P2P_DUPLICATE_WINDOW_TICKS = builder
                .comment("Duplicate P2P network-change suppression window. Keep this at 1 unless testing a known topology event storm.")
                .defineInRange("p2pDuplicateWindowTicks", 1, 1, 20);
        DEEP_BUS_SEARCH_REWRITE = builder
                .comment("Experimental: reuse short-lived immutable fuzzy-search results used by export buses. Non-fuzzy transfers and AE2's insertion/extraction validation remain unchanged.")
                .define("busSearchRewrite", false);
        DEEP_BUS_FUZZY_CACHE_TICKS = builder
                .comment("Lifetime of export-bus fuzzy search results. Keep short because storage amounts can change every tick.")
                .defineInRange("busFuzzySearchCacheTicks", 2, 1, 20);
        DEEP_BUS_FUZZY_CACHE_SIZE = builder
                .comment("Maximum cached fuzzy-search keys shared by export buses.")
                .defineInRange("busFuzzySearchCacheSize", 4096, 16, 262144);
        DEEP_FLUID_PATTERN_REWORK = builder
                .comment("Use an allocation-light exact-input path for single-fluid crafting inputs. AE2 15.4.10 already stores fluids as first-class GenericStack values, so no dummy-item conversion is introduced.")
                .define("fluidPatternRework", true);
        LOG_DEEP_AE2_REWRITE_FLAGS = builder
                .comment("Log active deep optimization paths and their limits on server start.")
                .define("logDeepAe2RewriteFlags", true);
        builder.pop();

        builder.push("recipeIntentBridge");
        ENABLE_RECIPE_INTENT_BRIDGE = builder
                .comment("Enable the AE2 Pattern Provider recipe intent bridge inside ACO. Current implementation records short-lived intents only; machine fast paths remain separate switches below.")
                .define("enableRecipeIntentBridge", true);
        CAPTURE_PATTERN_PROVIDER_RECIPE_INTENTS = builder
                .comment("Record successful AE2 Pattern Provider pushes as short-lived recipe intents. This is the intent-based crafting foundation for GTCEu, Mekanism, and future Create fast paths.")
                .define("capturePatternProviderRecipeIntents", true);
        RECIPE_INTENT_TTL_TICKS = builder
                .comment("How long a captured recipe intent remains visible to machine integrations. Keep short to avoid stale machine-side hints.")
                .defineInRange("recipeIntentTtlTicks", 20, 1, 20 * 30);
        MAXIMUM_RECIPE_INTENT_ENTRIES = builder
                .comment("Hard cap for captured recipe intent entries. Oldest entries are evicted when the cap is exceeded.")
                .defineInRange("maximumRecipeIntentEntries", 4096, 16, 1_048_576);
        ENABLE_PATTERN_MICRO_BATCHING = builder
                .comment(
                        "Compatibility-disabled in ACO 1.1.1. The key is retained so existing world configs remain readable.",
                        "Aggregate machine acceptance cannot prove that every represented processing-pattern execution will produce its declared output, so AE2's original one-execution accounting is always used.")
                .define("enablePatternMicroBatching", false);
        MAX_PATTERN_EXECUTIONS_PER_MICRO_BATCH = builder
                .comment("Maximum identical processing-pattern executions collapsed into one external-inventory push. This reduces Pattern Provider calls; it does not grant extra co-processors or bypass the crafting execution budget.")
                .defineInRange("maxPatternExecutionsPerMicroBatch", 65_536, 2, 1_048_576);
        REQUIRE_SINGLE_PATTERN_PROVIDER_TARGET = builder
                .comment("Only micro-batch Pattern Providers configured with exactly one target side. Keeping this true preserves deterministic routing and is strongly recommended.")
                .define("requireSinglePatternProviderTarget", true);
        PATTERN_MICRO_BATCH_TARGET_NAMESPACES = builder
                .comment("Registry namespaces of adjacent machine blocks allowed to receive aggregate pattern pushes. Every target still has to accept the full aggregate atomically through AE2's original Pattern Provider adapter.")
                .defineListAllowEmpty("patternMicroBatchTargetNamespaces", List.of(
                        "gtceu",
                        "mekanism"
                ), value -> value instanceof String);
        ENABLE_GTCEU_RECIPE_INTENT_FAST_PATH = builder
                .comment("Enable GTCEu recipe intent fast path. When a GTCEu machine has a fresh AE2 Pattern Provider intent for its position, ACO tries matching output-indexed GT recipes before GTCEu's normal full search. If no candidate works, GTCEu's original search still runs.")
                .define("enableGtceuRecipeIntentFastPath", true);
        GTCEU_RECIPE_INTENT_MAXIMUM_CANDIDATES = builder
                .comment("Maximum GTCEu output-matched recipe candidates to prepend before falling back to GTCEu's original iterator. Lower values reduce worst-case work when many recipes share one output.")
                .defineInRange("gtceuRecipeIntentMaximumCandidates", 16, 1, 1024);
        GTCEU_RECIPE_INTENT_INDEX_CACHE_SIZE = builder
                .comment("Maximum GTCEu recipe type indexes kept by ACO. Each index maps output item and fluid ids to GT recipes for that recipe type.")
                .defineInRange("gtceuRecipeIntentIndexCacheSize", 64, 1, 1024);
        GTCEU_RECIPE_INTENT_SEARCH_RADIUS = builder
                .comment("Maximum block radius used to associate a GTCEu multiblock controller with an intent delivered to one of its input buses or hatches. Zero limits matching to the controller position itself. Spatial chunk buckets keep this lookup bounded.")
                .defineInRange("gtceuRecipeIntentSearchRadius", 16, 0, 64);
        GTCEU_RECIPE_INTENT_NEARBY_MAXIMUM_ENTRIES = builder
                .comment("Maximum nearby Pattern Provider intents considered for one GTCEu recipe search.")
                .defineInRange("gtceuRecipeIntentNearbyMaximumEntries", 64, 1, 4096);
        LOG_GTCEU_RECIPE_INTENT_FAST_PATH = builder
                .comment("Log GTCEu recipe intent hits, candidate counts, and reflection/index failures. Disabled by default to avoid log spam.")
                .define("logGtceuRecipeIntentFastPath", false);
        ENABLE_MEKANISM_RECIPE_INTENT_FAST_PATH = builder
                .comment("Enable Mekanism recipe intent fast path. When a Mekanism machine has a fresh AE2 Pattern Provider intent for its position, ACO tries matching output-indexed Mekanism recipes and validates them with Mekanism's own recipe test before the normal lookup runs.")
                .define("enableMekanismRecipeIntentFastPath", true);
        MEKANISM_RECIPE_INTENT_MAXIMUM_CANDIDATES = builder
                .comment("Maximum Mekanism output-matched recipe candidates to test before falling back to Mekanism's original recipe lookup.")
                .defineInRange("mekanismRecipeIntentMaximumCandidates", 16, 1, 1024);
        MEKANISM_RECIPE_INTENT_INDEX_CACHE_SIZE = builder
                .comment("Maximum Mekanism recipe type indexes kept by ACO. Each index maps item, fluid, and chemical output ids to Mekanism recipes for that recipe type.")
                .defineInRange("mekanismRecipeIntentIndexCacheSize", 128, 1, 1024);
        CACHE_RESOLVED_RECIPE_INTENTS = builder
                .comment("Reuse short-lived, already validated machine recipe intent candidates. Mekanism still runs its own recipe test on every hit, and GTCEu still runs its original candidate validation.")
                .define("cacheResolvedRecipeIntents", true);
        RESOLVED_RECIPE_INTENT_CACHE_SIZE = builder
                .comment("Maximum resolved recipe-intent entries shared by each optional machine integration. Entries also expire with the originating Pattern Provider intent and are cleared on recipe reload.")
                .defineInRange("resolvedRecipeIntentCacheSize", 8192, 16, 1_048_576);
        LOG_MEKANISM_RECIPE_INTENT_FAST_PATH = builder
                .comment("Log Mekanism recipe intent hits, candidate counts, and reflection/index failures. Disabled by default to avoid log spam.")
                .define("logMekanismRecipeIntentFastPath", false);
        ENABLE_CREATE_RECIPE_INTENT_FAST_PATH = builder
                .comment("Reserved Create machine recipe fast path. Disabled and not active in this build; captured intents are available for diagnostics and future implementation.")
                .define("enableCreateRecipeIntentFastPath", false);
        LOG_CAPTURED_RECIPE_INTENTS = builder
                .comment("Log each captured Pattern Provider recipe intent. Leave false unless debugging a specific machine line.")
                .define("logCapturedRecipeIntents", false);
        LOG_RECIPE_INTENT_REGISTRY_EVICTIONS = builder
                .comment("Log recipe intent expiration, clears, and hard-cap evictions.")
                .define("logRecipeIntentRegistryEvictions", false);
        builder.pop();

        builder.push("transactionalPatternBatching");
        ENABLE_TRANSACTIONAL_PATTERN_BATCHING = builder
                .comment(
                        "Enable ACO's accepted-execution-count batch API. Only registered adapters may handle a batch; unsupported providers always use AE2's original path.",
                        "The built-in adapter preserves one pushPattern call per accepted execution and never treats aggregate inventory insertion as recipe completion.")
                .define("enableTransactionalPatternBatching", false);
        MAX_TRANSACTIONAL_PATTERN_BATCH_EXECUTIONS = builder
                .comment("Maximum exact processing-pattern executions prepared by one ACO batch transaction. The selected adapter may accept fewer.")
                .defineInRange("maxTransactionalPatternBatchExecutions", 65_536, 2, 1_048_576);
        ENABLE_SEQUENTIAL_PATTERN_PROVIDER_BATCH_ADAPTER = builder
                .comment("Enable the conservative built-in Pattern Provider adapter. It returns the exact number of successful one-execution AE2 pushes and stops immediately on provider backpressure.")
                .define("enableSequentialPatternProviderBatchAdapter", false);
        MAX_SEQUENTIAL_PROVIDER_EXECUTIONS_PER_CALL = builder
                .comment("Maximum one-execution pushPattern calls made by the conservative adapter in one transaction. Instant dispatch may run more than one bounded transaction. Native adapters use their own acceptance limits.")
                .defineInRange("maxSequentialProviderExecutionsPerCall", 256, 1, 65_536);
        ENABLE_INSTANT_PATTERN_DISPATCH = builder
                .comment(
                        "Time-slice AE2's original one-pattern-at-a-time dispatch loop at wave boundaries.",
                        "No aggregate input stack is created: AE2 remains responsible for extraction, provider backpressure, waiting outputs, energy, and task accounting.")
                .define("enableInstantPatternDispatch", true);
        INSTANT_PATTERN_DISPATCH_TIME_BUDGET_MILLIS = builder
                .comment("Per-CPU wall-clock budget for sequential Instant dispatch in one server tick. The grid-wide crafting budget may stop it earlier.")
                .defineInRange("instantPatternDispatchTimeBudgetMillis", 4, 1, 45);
        INSTANT_PATTERN_DISPATCH_PROBE_OPERATIONS = builder
                .comment(
                        "Cold-start wave size before ACO has measured the cost of one normal AE2 pattern push.",
                        "This limits only one timing wave, not the total number dispatched during the tick.")
                .defineInRange("instantPatternDispatchProbeOperations", 65_536, 1, 65_536);
        INSTANT_PATTERN_DISPATCH_MAXIMUM_WAVE_OPERATIONS = builder
                .comment(
                        "Maximum operations passed to one measured AE2 execution wave.",
                        "ACO may run many waves until maxPatterns, provider backpressure, or the CPU/grid time budget is reached.")
                .defineInRange("instantPatternDispatchMaximumWaveOperations", 65_536, 1, 1_048_576);
        MAX_INSTANT_PATTERN_DISPATCH_TRANSACTIONS = builder
                .comment("Maximum experimental V2 aggregate-adapter transactions per call. This does not limit sequential Instant dispatch.")
                .defineInRange("maxInstantPatternDispatchTransactions", 1024, 1, 65_536);
        REQUIRE_SINGLE_TRANSACTIONAL_BATCH_TARGET = builder
                .comment("Require one configured Pattern Provider target side before using a transactional batch adapter. This keeps target ownership deterministic.")
                .define("requireSingleTransactionalBatchTarget", true);
        TRANSACTIONAL_BATCH_TARGET_NAMESPACES = builder
                .comment("Registry namespaces eligible for ACO's transactional Pattern Provider batching. Unlisted targets use AE2's original execution path.")
                .defineListAllowEmpty("transactionalBatchTargetNamespaces", List.of(
                        "gtceu",
                        "mekanism"
                ), value -> value instanceof String);
        builder.pop();

        builder.push("experimentalCraftingEngine");
        ENABLE_AQE_BIG_CRAFTING_PROFILE = builder
                .comment(
                        "Enable only the AQE BigInteger calculation and execution path when Advanced AE and Advanced Quantum Engineering are installed.",
                        "This does not enable Native Batch, bus rewrites, terminal rewrites, or normal-AE2 authoritative replacement.")
                .define("enableAqeBigCraftingProfile", true);
        ENABLE_EXPERIMENTAL_CRAFTING_ENGINE = builder
                .comment(
                        "Master switch for ACO's next-generation compiled planner, native machine batching, fair scheduler, and persistent transaction journal.",
                        "This is deliberately false. The implementation can be built and tested without changing live AE2 behavior.")
                .define("enableExperimentalCraftingEngine", false);
        ENABLE_CRAFTING_ENGINE_SHADOW_MODE = builder
                .comment(
                        "Compare the compiled long planner with completed AE2 plans without replacing or modifying AE2's result.",
                        "Requires the AQE profile or enableExperimentalCraftingEngine. Shadow mismatches only update diagnostics.")
                .define("enableShadowMode", true);
        LOG_CRAFTING_ENGINE_SHADOW_MISMATCHES = builder
                .comment("Log the first bounded set of Shadow Mode differences. Disabled engine means no comparison or logging.")
                .define("logShadowMismatches", true);
        CRAFTING_ENGINE_SHADOW_MAXIMUM_PATTERNS = builder
                .comment("Skip Shadow Mode validation above this many patterns to keep diagnostic work bounded.")
                .defineInRange("shadowMaximumPatterns", 262_144, 1, 1_048_576);
        AUTHORITATIVE_MINIMUM_SHADOW_MATCHES = builder
                .comment(
                        "Required matching AE2 Shadow comparisons for the same generation-keyed root program before it may become authoritative.",
                        "Set to 0 only to explicitly bypass qualification. One mismatch rejects that root until its Pattern or recipe generation changes.")
                .defineInRange(
                        "authoritativeMinimumShadowMatches",
                        DEFAULT_AUTHORITATIVE_SHADOW_MATCHES,
                        0,
                        MAXIMUM_AUTHORITATIVE_SHADOW_MATCHES);
        REQUIRE_AQE_BIG_PLAN_SHADOW_QUALIFICATION = builder
                .comment(
                        "Require prior matching AE2 Shadow calculations before accepting an otherwise strictly proven AQE BigInteger plan.",
                        "Disabled by default because a true long-overflow request cannot itself complete in AE2 for comparison.")
                .define("requireAqeBigPlanShadowQualification", false);
        ENABLE_COMPILED_CRAFTING_GRAPH = builder
                .comment("Build an immutable, generation-keyed crafting graph for experimental planning.")
                .define("enableCompiledCraftingGraph", true);
        ENABLE_AUTHORITATIVE_COMPILED_PLANNER = builder
                .comment(
                        "Use a compiled plan only for the strictly provable single-pattern path. Any ambiguity, generation change, fuzzy input, overflow, or unsupported recipe falls back to AE2.",
                        "Kept false until the user completes live comparison testing.")
                .define("enableAuthoritativeCompiledPlanner", false);
        ENABLE_CHECKED_AE2_CRAFTING_ARITHMETIC = builder
                .comment(
                        "Reject AE2 tree calculations before unchecked long/double arithmetic can wrap or saturate.",
                        "This does not make a standard AE2 job BigInteger-capable; oversized work must use execution windows.")
                .define("enableCheckedAe2CraftingArithmetic", true);
        ENABLE_TRANSACTIONAL_BATCHING_V2 = builder
                .comment("Use the prepare/commit/account/reconcile transaction protocol. Kept false until recovery testing is complete.")
                .define("enableTransactionalBatchingV2", false);
        ENABLE_GTCEU_NATIVE_BATCHING = builder
                .comment("Allow the V2 transaction engine to use the exact-recipe GTCEu native batch adapter. Unsupported recipes always fall back.")
                .define("enableGtceuNativeBatching", false);
        ENABLE_MEKANISM_NATIVE_BATCHING = builder
                .comment("Allow the V2 transaction engine to use the exact-recipe Mekanism item/fluid/chemical native batch adapter. Unsupported recipes always fall back.")
                .define("enableMekanismNativeBatching", false);
        ENABLE_FAIR_CRAFTING_JOB_SCHEDULER = builder
                .comment("Use the experimental deficit-round-robin scheduler for multiple crafting jobs. Kept false until multiplayer soak testing.")
                .define("enableFairCraftingJobScheduler", false);
        FAIR_SCHEDULER_OPERATIONS_PER_TICK = builder
                .comment("Shared hard operation budget for the experimental scheduler per server tick.")
                .defineInRange("fairSchedulerOperationsPerTick", 4096, 1, 1_048_576);
        FAIR_SCHEDULER_QUANTUM = builder
                .comment("Deficit-round-robin operation quantum granted to each runnable job per scheduling round.")
                .defineInRange("fairSchedulerQuantum", 64, 1, 65_536);
        FAIR_SCHEDULER_TIME_BUDGET_MILLIS = builder
                .comment("Shared wall-clock budget for experimental scheduling in one server tick.")
                .defineInRange("fairSchedulerTimeBudgetMillis", 4, 1, 45);
        PERSIST_BATCH_TRANSACTION_JOURNAL = builder
                .comment(
                        "Persist non-terminal V2 batch transactions in overworld SavedData/NBT before target ownership transfer.",
                        "Disabling this also disables transactional V2 execution; V2 is never allowed to run without recovery state.")
                .define("persistBatchTransactionJournal", true);
        BATCH_TRANSACTION_JOURNAL_MAXIMUM_ENTRIES = builder
                .comment("Hard cap for non-terminal persistent transaction records. New native batches fall back when full.")
                .defineInRange("batchTransactionJournalMaximumEntries", 16_384, 16, 16_384);
        BATCH_TRANSACTION_RECONCILIATION_INTERVAL_TICKS = builder
                .comment("Interval between bounded recovery scans for prepared or target-accepted transactions.")
                .defineInRange("batchTransactionReconciliationIntervalTicks", 20, 1, 20 * 60);
        NATIVE_BATCH_MAXIMUM_EXECUTIONS = builder
                .comment("Hard per-transaction execution cap shared by native machine adapters. Machine limits may reduce it further.")
                .defineInRange("nativeBatchMaximumExecutions", 65_536, 1, 1_048_576);
        ENABLE_BIG_INTEGER_CRAFTING_BACKEND = builder
                .comment(
                        "Expose ACO's versioned BigInteger host and job backend to explicitly integrated CPU add-ons.",
                        "This does not patch normal AE2 CPUs. It is safe to leave enabled when no compatible host add-on is installed.")
                .define("enableBigIntegerCraftingBackend", true);
        ENABLE_ATOMIC_BIG_CAPACITY_PLANS = builder
                .comment(
                        "Safely calculate plans whose individual AEKey and Pattern counts fit signed long, but whose aggregate input or exact CPU-byte cost exceeds Long.MAX_VALUE.",
                        "Aggregate-input-only plans keep exact per-key long counters. Big CPU-byte plans additionally require an integrated AQE BigInteger host. Any individual count above signed long is never accepted.")
                .define("enableAtomicBigCapacityPlans", true);
        ENABLE_BIG_INTEGER_GAMEPLAY_EXECUTION = builder
                .comment(
                        "Execute explicitly submitted BigInteger root jobs as bounded standard AE2 child jobs on an AQE Quantum Computer.",
                        "False until phase-9 live testing. Capacity display and ordinary long jobs continue to work while this is false.")
                .define("enableBigIntegerGameplayExecution", true);
        BIG_INTEGER_MAXIMUM_BITS = builder
                .comment(
                        "Maximum magnitude stored by the BigInteger backend, in binary bits. 256 bits is about 77 decimal digits.",
                        "The hard implementation maximum is 16384 decimal digits (currently 54427 bits). Values above 10^16384-1 are rejected exactly before NBT or packet allocation.")
                .defineInRange("bigIntegerMaximumBits", 256, 64, BigCountMath.HARD_MAXIMUM_BITS);
        BIG_INTEGER_EXECUTION_WINDOW = builder
                .comment(
                        "Maximum pattern executions exposed to a long/int machine adapter in one BigInteger execution window.",
                        "Larger jobs remain BigInteger counters and are resumed through additional windows.")
                .defineInRange("bigIntegerExecutionWindow", 65_536, 1, 1_048_576);
        BIG_INTEGER_MAXIMUM_WINDOW_CALCULATIONS_PER_TICK = builder
                .comment(
                        "Maximum new BigInteger child-plan calculations started in one server tick.",
                        "Each Big job can own at most one unfinished child window, so this does not create an unbounded CPU list.")
                .defineInRange("bigIntegerMaximumWindowCalculationsPerTick", 4, 1, 64);
        BIG_INTEGER_RETRY_BACKOFF_TICKS = builder
                .comment("Backoff after a child plan is missing, rejected, or cancelled before retrying that BigInteger job.")
                .defineInRange("bigIntegerRetryBackoffTicks", 20, 1, 20 * 60);
        BIG_INTEGER_STATUS_PAGE_ENTRIES = builder
                .comment("Maximum job summaries carried by one BigInteger crafting-status page.")
                .defineInRange("bigIntegerStatusPageEntries", 1024, 16, 16_384);
        BIG_INTEGER_RUNTIME_COUNT_BUDGET_MIB = builder
                .comment(
                        "Aggregate memory-accounting budget for BigInteger count magnitudes in one CPU runtime, in MiB.",
                        "New jobs or output reservations fall back before exceeding this bound.")
                .defineInRange("bigIntegerRuntimeCountBudgetMiB", 256, 32, 4096);
        builder.pop();

        builder.push("diagnostics");
        LOG_SLOW_CRAFT_CALCULATIONS = builder
                .comment("Log AE2 crafting calculations that take longer than slowCraftCalculationMillis.")
                .define("logSlowCraftCalculations", true);
        SLOW_CRAFT_CALCULATION_MILLIS = builder
                .comment("Slow crafting calculation threshold in milliseconds.")
                .defineInRange("slowCraftCalculationMillis", 500, 1, 300_000);
        LOG_CACHE_STATISTICS = builder
                .comment("Log preliminary missing preview cache hits, writes, and clears.")
                .define("logCacheStatistics", false);
        builder.pop();

        SPEC = builder.build();
    }

    private ACOConfig() {
    }

    public static void register() {
        // サーバー側の最適化とクライアント側の表示設定を、一つの共通Configとして登録する。
        // ワールドごとのserverconfigには複製せず、各環境のconfigフォルダから読み込む。
        ModLoadingContext.get().registerConfig(
                ModConfig.Type.COMMON,
                SPEC,
                "ae2_crafting_optimizer-common.toml");
    }

    public static boolean enableOptimizer() {
        return ENABLE_OPTIMIZER.get();
    }

    public static boolean twoStageMissingPreview() {
        return enableOptimizer() && TWO_STAGE_MISSING_PREVIEW.get() && getPreviewMaximumEntries() > 0;
    }

    public static boolean cancelCalculationAfterPreliminaryMissingPreview() {
        return twoStageMissingPreview() && CANCEL_CALCULATION_AFTER_PRELIMINARY_MISSING_PREVIEW.get();
    }

    public static boolean useMissingPreviewCache() {
        return twoStageMissingPreview() && USE_MISSING_PREVIEW_CACHE.get() && getMissingPreviewCacheSize() > 0;
    }

    public static boolean skipCalculationOnCachedMissingPreview() {
        return useMissingPreviewCache() && SKIP_CALCULATION_ON_CACHED_MISSING_PREVIEW.get();
    }

    public static int getMissingPreviewCacheSize() {
        return Math.min(65536, Math.max(0, MISSING_PREVIEW_CACHE_SIZE.get()));
    }

    public static int getMissingPreviewCacheTtlSeconds() {
        return Math.min(86400, Math.max(1, MISSING_PREVIEW_CACHE_TTL_SECONDS.get()));
    }

    public static boolean invalidateCacheOnStorageChange() {
        return enableOptimizer() && INVALIDATE_CACHE_ON_STORAGE_CHANGE.get();
    }

    public static boolean invalidateCacheOnPatternChange() {
        return enableOptimizer() && INVALIDATE_CACHE_ON_PATTERN_CHANGE.get();
    }

    public static int getMinimumCalculationMillisForPreview() {
        return Math.min(60_000, Math.max(0, MINIMUM_CALCULATION_MILLIS_FOR_PREVIEW.get()));
    }

    public static long getMinimumRequestedAmountForPreview() {
        return Math.max(1L, MINIMUM_REQUESTED_AMOUNT_FOR_PREVIEW.get());
    }

    public static boolean shouldUsePreliminaryMissingPreview(Object output, long requestedAmount) {
        return twoStageMissingPreview()
                && requestedAmount >= getMinimumRequestedAmountForPreview()
                && matchesHeavyProcessHint(output);
    }

    private static boolean matchesHeavyProcessHint(Object output) {
        String key = String.valueOf(output).toLowerCase(Locale.ROOT);
        for (String hint : getHeavyProcessHints()) {
            if ("*".equals(hint) || key.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    public static int getPreviewMaximumEntries() {
        return Math.min(1, Math.max(0, PREVIEW_MAXIMUM_ENTRIES.get()));
    }

    public static List<String> getHeavyProcessHints() {
        return HEAVY_PROCESS_HINTS.get().stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(String::toLowerCase)
                .toList();
    }

    public static boolean deduplicateActiveCraftingCalculations() {
        return enableOptimizer() && DEDUPLICATE_ACTIVE_CRAFTING_CALCULATIONS.get();
    }

    public static int getActiveCalculationDeduplicationWindowTicks() {
        return Math.min(20 * 60, Math.max(1, ACTIVE_CALCULATION_DEDUPLICATION_WINDOW_TICKS.get()));
    }

    public static boolean logCraftingCalculationDeduplication() {
        return enableOptimizer() && LOG_CRAFTING_CALCULATION_DEDUPLICATION.get();
    }

    public static boolean cacheCompletedCraftingPlans() {
        return enableOptimizer() && CACHE_COMPLETED_CRAFTING_PLANS.get() && getCompletedCraftingPlanCacheSize() > 0;
    }

    public static boolean cacheSuccessfulCompletedCraftingPlans() {
        return cacheCompletedCraftingPlans() && CACHE_SUCCESSFUL_COMPLETED_CRAFTING_PLANS.get();
    }

    public static int getCompletedCraftingPlanCacheSize() {
        return Math.min(65536, Math.max(0, COMPLETED_CRAFTING_PLAN_CACHE_SIZE.get()));
    }

    public static int getCompletedCraftingPlanCacheTtlTicks() {
        return Math.min(20 * 60, Math.max(1, COMPLETED_CRAFTING_PLAN_CACHE_TTL_TICKS.get()));
    }

    public static boolean fastFailMissingCrafts() {
        return enableOptimizer() && FAST_FAIL_MISSING_CRAFTS.get();
    }

    public static long getMinimumRequestedAmountForFastFail() {
        return Math.max(1L, MINIMUM_REQUESTED_AMOUNT_FOR_FAST_FAIL.get());
    }

    public static int getDeterministicPreflightMaxDepth() {
        return Math.min(1024, Math.max(1, DETERMINISTIC_PREFLIGHT_MAX_DEPTH.get()));
    }

    public static int getDeterministicPreflightMaxNodes() {
        return Math.min(1_000_000, Math.max(1, DETERMINISTIC_PREFLIGHT_MAX_NODES.get()));
    }

    public static boolean logFastFailMissingCrafts() {
        return enableOptimizer() && LOG_FAST_FAIL_MISSING_CRAFTS.get();
    }

    public static boolean cachePatternLookups() {
        return enableOptimizer() && CACHE_PATTERN_LOOKUPS.get() && getPatternLookupCacheSize() > 0;
    }

    public static int getPatternLookupCacheSize() {
        return Math.min(1_000_000, Math.max(0, PATTERN_LOOKUP_CACHE_SIZE.get()));
    }

    public static boolean cacheCraftableSets() {
        return enableOptimizer() && CACHE_CRAFTABLE_SETS.get() && getCraftableSetCacheSize() > 0;
    }

    public static int getCraftableSetCacheSize() {
        return Math.min(65536, Math.max(0, CRAFTABLE_SET_CACHE_SIZE.get()));
    }

    public static boolean logPatternLookupCache() {
        return enableOptimizer() && LOG_PATTERN_LOOKUP_CACHE.get();
    }

    public static boolean throttleCraftingExecution() {
        return enableOptimizer() && THROTTLE_CRAFTING_EXECUTION.get();
    }

    public static int getMaxEffectiveCoprocessorsPerCpu() {
        return Math.min(MAX_SAFE_EFFECTIVE_COPROCESSORS, Math.max(1, MAX_EFFECTIVE_COPROCESSORS_PER_CPU.get()));
    }

    public static boolean adaptiveCraftingExecutionBudget() {
        return throttleCraftingExecution() && ADAPTIVE_CRAFTING_EXECUTION_BUDGET.get();
    }

    public static int getTargetCraftingExecutionMillis() {
        return Math.min(50, Math.max(1, TARGET_CRAFTING_EXECUTION_MILLIS.get()));
    }

    public static int getMinimumAdaptiveCoprocessorsPerCpu() {
        int hardCap = getMaxEffectiveCoprocessorsPerCpu();
        return Math.min(hardCap, Math.max(1, MINIMUM_ADAPTIVE_COPROCESSORS_PER_CPU.get()));
    }

    public static boolean sharedCraftingExecutionBudget() {
        return throttleCraftingExecution() && SHARED_CRAFTING_EXECUTION_BUDGET.get();
    }

    public static int getSharedCraftingExecutionMillisPerGrid() {
        return Math.min(45, Math.max(1, SHARED_CRAFTING_EXECUTION_MILLIS_PER_GRID.get()));
    }

    public static int getMinimumSharedOperationsPerCpu() {
        return Math.min(65536, Math.max(1, MINIMUM_SHARED_OPERATIONS_PER_CPU.get()));
    }

    public static boolean logCraftingExecutionThrottling() {
        return enableOptimizer() && LOG_CRAFTING_EXECUTION_THROTTLING.get();
    }

    public static boolean throttleNeoEcoAeExecution() {
        return throttleCraftingExecution() && THROTTLE_NEO_ECO_AE_EXECUTION.get();
    }

    public static boolean enableAppliedECompatibility() {
        return enableOptimizer() && ENABLE_APPLIED_E_COMPATIBILITY.get();
    }

    public static boolean forceAe2PlannerForAppliedEPatterns() {
        return enableAppliedECompatibility() && FORCE_AE2_PLANNER_FOR_APPLIED_E_PATTERNS.get();
    }

    public static boolean treatAppliedEProviderAsDynamic() {
        return enableAppliedECompatibility() && TREAT_APPLIED_E_PROVIDER_AS_DYNAMIC.get();
    }

    public static boolean enableGridTickBudget() {
        return enableOptimizer() && ENABLE_GRID_TICK_BUDGET.get();
    }

    public static boolean deferHeavyGridTickables() {
        return enableGridTickBudget() && DEFER_HEAVY_GRID_TICKABLES.get();
    }

    public static int getGridTickBudgetMillisPerServerTick() {
        return Math.min(45, Math.max(1, GRID_TICK_BUDGET_MILLIS_PER_SERVER_TICK.get()));
    }

    public static int getGridTickMinimumIntervalTicks() {
        return Math.min(200, Math.max(1, GRID_TICK_MINIMUM_INTERVAL_TICKS.get()));
    }

    public static int getSlowGridTickableMicros() {
        return Math.min(1_000_000, Math.max(1, SLOW_GRID_TICKABLE_MICROS.get()));
    }

    public static int getSlowGridTickableBackoffTicks() {
        return Math.min(200, Math.max(0, SLOW_GRID_TICKABLE_BACKOFF_TICKS.get()));
    }

    public static boolean backoffIdleGridTickables() {
        return deferHeavyGridTickables() && BACKOFF_IDLE_GRID_TICKABLES.get();
    }

    public static int getIdleGridTickableBackoffAfterFailures() {
        return Math.min(1000, Math.max(1, IDLE_GRID_TICKABLE_BACKOFF_AFTER_FAILURES.get()));
    }

    public static int getIdleGridTickableBackoffTicks() {
        return Math.min(200, Math.max(1, IDLE_GRID_TICKABLE_BACKOFF_TICKS.get()));
    }

    public static List<String> getHeavyGridTickableClassHints() {
        return HEAVY_GRID_TICKABLE_CLASS_HINTS.get().stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(String::toLowerCase)
                .toList();
    }

    public static boolean limitIoBusOperationsPerTick() {
        return enableGridTickBudget() && LIMIT_IO_BUS_OPERATIONS_PER_TICK.get();
    }

    public static int getMaxIoBusOperationsPerTick() {
        return Math.min(MAX_SAFE_EFFECTIVE_COPROCESSORS, Math.max(1, MAX_IO_BUS_OPERATIONS_PER_TICK.get()));
    }

    public static boolean throttleExportBusCraftRequests() {
        return enableGridTickBudget() && THROTTLE_EXPORT_BUS_CRAFT_REQUESTS.get();
    }

    public static int getExportBusCraftFailureCooldownTicks() {
        return Math.min(20 * 60, Math.max(1, EXPORT_BUS_CRAFT_FAILURE_COOLDOWN_TICKS.get()));
    }

    public static int getExportBusCraftThrottleCacheSize() {
        return Math.min(1_000_000, Math.max(1, EXPORT_BUS_CRAFT_THROTTLE_CACHE_SIZE.get()));
    }

    public static boolean logGridTickBudget() {
        return enableOptimizer() && LOG_GRID_TICK_BUDGET.get();
    }

    public static boolean cacheAdjacentCapabilityLookups() {
        return enableOptimizer() && CACHE_ADJACENT_CAPABILITY_LOOKUPS.get();
    }

    public static boolean cacheAdjacentCapabilitiesAcrossTicks() {
        return cacheAdjacentCapabilityLookups() && CACHE_ADJACENT_CAPABILITIES_ACROSS_TICKS.get();
    }

    public static boolean cacheNegativeBusTransferSimulations() {
        return enableOptimizer() && CACHE_NEGATIVE_BUS_TRANSFER_SIMULATIONS.get();
    }

    public static boolean pruneInvalidCraftingCandidates() {
        return enableOptimizer() && PRUNE_INVALID_CRAFTING_CANDIDATES.get();
    }

    public static boolean memoizeCraftingCalculationQueries() {
        return enableOptimizer() && MEMOIZE_CRAFTING_CALCULATION_QUERIES.get();
    }

    public static boolean coalesceCraftingProviderRefreshes() {
        return enableOptimizer() && COALESCE_CRAFTING_PROVIDER_REFRESHES.get();
    }

    public static boolean trackProviderPatternGenerations() {
        return enableOptimizer() && TRACK_PROVIDER_PATTERN_GENERATIONS.get();
    }

    public static boolean incrementalIoPortProcessing() {
        return enableOptimizer() && INCREMENTAL_IO_PORT_PROCESSING.get();
    }

    public static int getIoPortCellSlotsPerTick() {
        return Math.min(6, Math.max(1, IO_PORT_CELL_SLOTS_PER_TICK.get()));
    }

    public static boolean cacheImportBusLastSuccessfulSlot() {
        return enableOptimizer() && CACHE_IMPORT_BUS_LAST_SUCCESSFUL_SLOT.get();
    }

    public static boolean cacheExportBusCandidateKeys() {
        return enableOptimizer() && CACHE_EXPORT_BUS_CANDIDATE_KEYS.get();
    }

    public static boolean coalesceClientTerminalViewUpdates() {
        return enableOptimizer() && COALESCE_CLIENT_TERMINAL_VIEW_UPDATES.get();
    }

    public static boolean asyncTerminalSearchSort() {
        return enableOptimizer() && ASYNC_TERMINAL_SEARCH_SORT.get();
    }

    public static int getAsyncTerminalMinimumEntries() {
        return Math.min(1_000_000, Math.max(128, ASYNC_TERMINAL_MINIMUM_ENTRIES.get()));
    }

    public static boolean fixStuckAe2ScrollbarRepeat() {
        return enableOptimizer() && FIX_STUCK_AE2_SCROLLBAR_REPEAT.get();
    }

    public static boolean cacheCircuitCutterRecipes() {
        return enableOptimizer() && CACHE_CIRCUIT_CUTTER_RECIPES.get();
    }

    public static boolean cacheCircuitCutterNegativeResults() {
        return cacheCircuitCutterRecipes() && CACHE_CIRCUIT_CUTTER_NEGATIVE_RESULTS.get();
    }

    public static int getCircuitCutterRecipeCacheSize() {
        return Math.min(262144, Math.max(16, CIRCUIT_CUTTER_RECIPE_CACHE_SIZE.get()));
    }

    public static boolean enableAddonMachineOptimizations() {
        return enableOptimizer() && ENABLE_ADDON_MACHINE_OPTIMIZATIONS.get();
    }

    public static boolean cacheReactionChamberRecipe() {
        return enableAddonMachineOptimizations() && CACHE_REACTION_CHAMBER_RECIPE.get();
    }

    public static boolean cacheAe2OverclockReflection() {
        return enableAddonMachineOptimizations() && CACHE_AE2_OVERCLOCK_REFLECTION.get();
    }

    public static boolean useAe2OverclockMethodHandles() {
        return cacheAe2OverclockReflection() && USE_AE2_OVERCLOCK_METHOD_HANDLES.get();
    }

    public static boolean cacheAe2OverclockUpgradeCounts() {
        return enableAddonMachineOptimizations() && CACHE_AE2_OVERCLOCK_UPGRADE_COUNTS.get();
    }

    public static boolean cacheAssemblerMatrixThreadCounts() {
        return enableAddonMachineOptimizations() && CACHE_ASSEMBLER_MATRIX_THREAD_COUNTS.get();
    }

    public static boolean cacheAssemblerMatrixBusyCount() {
        return enableAddonMachineOptimizations() && CACHE_ASSEMBLER_MATRIX_BUSY_COUNT.get();
    }

    public static boolean coalesceAssemblerMatrixStatusUpdates() {
        return enableAddonMachineOptimizations() && COALESCE_ASSEMBLER_MATRIX_STATUS_UPDATES.get();
    }

    public static boolean cacheAssemblerMatrixRouting() {
        return enableAddonMachineOptimizations() && CACHE_ASSEMBLER_MATRIX_ROUTING.get();
    }

    public static boolean throttleStorageWatcherUpdates() {
        return enableOptimizer() && THROTTLE_STORAGE_WATCHER_UPDATES.get();
    }

    public static int getStorageWatcherUpdateIntervalTicks() {
        return Math.min(40, Math.max(1, STORAGE_WATCHER_UPDATE_INTERVAL_TICKS.get()));
    }

    public static boolean throttleTerminalInventorySnapshots() {
        return enableOptimizer() && THROTTLE_TERMINAL_INVENTORY_SNAPSHOTS.get();
    }

    public static int getTerminalInventorySnapshotIntervalTicks() {
        return Math.min(40, Math.max(1, TERMINAL_INVENTORY_SNAPSHOT_INTERVAL_TICKS.get()));
    }

    public static boolean cacheTerminalCraftables() {
        return enableOptimizer() && CACHE_TERMINAL_CRAFTABLES.get();
    }

    public static int getTerminalCraftableCacheTicks() {
        return Math.min(40, Math.max(1, TERMINAL_CRAFTABLE_CACHE_TICKS.get()));
    }

    public static boolean flushImmediatelyOnScreenOpen() {
        return enableOptimizer() && FLUSH_IMMEDIATELY_ON_SCREEN_OPEN.get();
    }

    public static boolean flushImmediatelyOnCellChange() {
        return enableOptimizer() && FLUSH_IMMEDIATELY_ON_CELL_CHANGE.get();
    }

    public static boolean flushImmediatelyOnNetworkTopologyChange() {
        return enableOptimizer() && FLUSH_IMMEDIATELY_ON_NETWORK_TOPOLOGY_CHANGE.get();
    }

    public static int getMaximumBufferedChanges() {
        return Math.min(1_000_000, Math.max(1, MAXIMUM_BUFFERED_CHANGES.get()));
    }

    public static boolean enableDeepAe2RewriteFlags() {
        return enableOptimizer() && ENABLE_DEEP_AE2_REWRITE_FLAGS.get();
    }

    public static boolean deepPatternSelectionByAvailability() {
        return enableDeepAe2RewriteFlags() && DEEP_PATTERN_SELECTION_BY_AVAILABILITY.get();
    }

    public static int getDeepPatternSelectionMaximumCandidates() {
        return Math.min(4096, Math.max(2, DEEP_PATTERN_SELECTION_MAXIMUM_CANDIDATES.get()));
    }

    public static boolean deepNetworkForceUpdateCoalescing() {
        return enableDeepAe2RewriteFlags() && DEEP_NETWORK_FORCE_UPDATE_COALESCING.get();
    }

    public static int getDeepNetworkUpdateIntervalTicks() {
        return Math.min(40, Math.max(1, DEEP_NETWORK_UPDATE_INTERVAL_TICKS.get()));
    }

    public static boolean deepVisibleTerminalRangeSync() {
        return enableDeepAe2RewriteFlags() && DEEP_VISIBLE_TERMINAL_RANGE_SYNC.get();
    }

    public static int getDeepTerminalRangeEntriesPerTick() {
        return Math.min(32767, Math.max(64, DEEP_TERMINAL_RANGE_ENTRIES_PER_TICK.get()));
    }

    public static boolean deepP2PTopologyChangeOnlyRecheck() {
        return enableDeepAe2RewriteFlags() && DEEP_P2P_TOPOLOGY_CHANGE_ONLY_RECHECK.get();
    }

    public static int getDeepP2PDuplicateWindowTicks() {
        return Math.min(20, Math.max(1, DEEP_P2P_DUPLICATE_WINDOW_TICKS.get()));
    }

    public static boolean deepBusSearchRewrite() {
        return enableDeepAe2RewriteFlags() && DEEP_BUS_SEARCH_REWRITE.get();
    }

    public static int getDeepBusFuzzyCacheTicks() {
        return Math.min(20, Math.max(1, DEEP_BUS_FUZZY_CACHE_TICKS.get()));
    }

    public static int getDeepBusFuzzyCacheSize() {
        return Math.min(262144, Math.max(16, DEEP_BUS_FUZZY_CACHE_SIZE.get()));
    }

    public static boolean deepFluidPatternRework() {
        return enableDeepAe2RewriteFlags() && DEEP_FLUID_PATTERN_REWORK.get();
    }

    public static boolean logDeepAe2RewriteFlags() {
        return enableOptimizer() && LOG_DEEP_AE2_REWRITE_FLAGS.get();
    }

    public static boolean enableRecipeIntentBridge() {
        return enableOptimizer() && ENABLE_RECIPE_INTENT_BRIDGE.get();
    }

    public static boolean capturePatternProviderRecipeIntents() {
        return enableRecipeIntentBridge() && CAPTURE_PATTERN_PROVIDER_RECIPE_INTENTS.get();
    }

    public static int getRecipeIntentTtlTicks() {
        return Math.min(20 * 30, Math.max(1, RECIPE_INTENT_TTL_TICKS.get()));
    }

    public static int getMaximumRecipeIntentEntries() {
        return Math.min(1_048_576, Math.max(16, MAXIMUM_RECIPE_INTENT_ENTRIES.get()));
    }

    public static boolean enablePatternMicroBatching() {
        return false;
    }

    public static boolean patternMicroBatchingRequested() {
        return ENABLE_PATTERN_MICRO_BATCHING.get();
    }

    public static int getMaxPatternExecutionsPerMicroBatch() {
        return Math.min(1_048_576, Math.max(2, MAX_PATTERN_EXECUTIONS_PER_MICRO_BATCH.get()));
    }

    public static boolean requireSinglePatternProviderTarget() {
        return REQUIRE_SINGLE_PATTERN_PROVIDER_TARGET.get();
    }

    public static List<String> getPatternMicroBatchTargetNamespaces() {
        return PATTERN_MICRO_BATCH_TARGET_NAMESPACES.get().stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    public static boolean enableTransactionalPatternBatching() {
        return enableOptimizer() && ENABLE_TRANSACTIONAL_PATTERN_BATCHING.get();
    }

    public static int getMaxTransactionalPatternBatchExecutions() {
        return Math.min(1_048_576, Math.max(2, MAX_TRANSACTIONAL_PATTERN_BATCH_EXECUTIONS.get()));
    }

    public static boolean enableSequentialPatternProviderBatchAdapter() {
        return enableTransactionalPatternBatching() && ENABLE_SEQUENTIAL_PATTERN_PROVIDER_BATCH_ADAPTER.get();
    }

    public static int getMaxSequentialProviderExecutionsPerCall() {
        return Math.min(65_536, Math.max(1, MAX_SEQUENTIAL_PROVIDER_EXECUTIONS_PER_CALL.get()));
    }

    public static boolean enableInstantPatternDispatch() {
        return enableOptimizer() && ENABLE_INSTANT_PATTERN_DISPATCH.get();
    }

    public static int getInstantPatternDispatchTimeBudgetMillis() {
        return Math.min(45, Math.max(1, INSTANT_PATTERN_DISPATCH_TIME_BUDGET_MILLIS.get()));
    }

    public static int getInstantPatternDispatchProbeOperations() {
        return Math.min(65_536, Math.max(1, INSTANT_PATTERN_DISPATCH_PROBE_OPERATIONS.get()));
    }

    public static int getInstantPatternDispatchMaximumWaveOperations() {
        return Math.min(1_048_576, Math.max(1, INSTANT_PATTERN_DISPATCH_MAXIMUM_WAVE_OPERATIONS.get()));
    }

    public static int getMaxInstantPatternDispatchTransactions() {
        return Math.min(65_536, Math.max(1, MAX_INSTANT_PATTERN_DISPATCH_TRANSACTIONS.get()));
    }

    public static boolean requireSingleTransactionalBatchTarget() {
        return REQUIRE_SINGLE_TRANSACTIONAL_BATCH_TARGET.get();
    }

    public static List<String> getTransactionalBatchTargetNamespaces() {
        return TRANSACTIONAL_BATCH_TARGET_NAMESPACES.get().stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    public static boolean enableGtceuRecipeIntentFastPath() {
        return enableRecipeIntentBridge() && ENABLE_GTCEU_RECIPE_INTENT_FAST_PATH.get();
    }

    public static int getGtceuRecipeIntentMaximumCandidates() {
        return Math.min(1024, Math.max(1, GTCEU_RECIPE_INTENT_MAXIMUM_CANDIDATES.get()));
    }

    public static int getGtceuRecipeIntentIndexCacheSize() {
        return Math.min(1024, Math.max(1, GTCEU_RECIPE_INTENT_INDEX_CACHE_SIZE.get()));
    }

    public static int getGtceuRecipeIntentSearchRadius() {
        return Math.min(64, Math.max(0, GTCEU_RECIPE_INTENT_SEARCH_RADIUS.get()));
    }

    public static int getGtceuRecipeIntentNearbyMaximumEntries() {
        return Math.min(4096, Math.max(1, GTCEU_RECIPE_INTENT_NEARBY_MAXIMUM_ENTRIES.get()));
    }

    public static boolean logGtceuRecipeIntentFastPath() {
        return enableGtceuRecipeIntentFastPath() && LOG_GTCEU_RECIPE_INTENT_FAST_PATH.get();
    }

    public static boolean enableMekanismRecipeIntentFastPath() {
        return enableRecipeIntentBridge() && ENABLE_MEKANISM_RECIPE_INTENT_FAST_PATH.get();
    }

    public static int getMekanismRecipeIntentMaximumCandidates() {
        return Math.min(1024, Math.max(1, MEKANISM_RECIPE_INTENT_MAXIMUM_CANDIDATES.get()));
    }

    public static int getMekanismRecipeIntentIndexCacheSize() {
        return Math.min(1024, Math.max(1, MEKANISM_RECIPE_INTENT_INDEX_CACHE_SIZE.get()));
    }

    public static boolean cacheResolvedRecipeIntents() {
        return enableRecipeIntentBridge() && CACHE_RESOLVED_RECIPE_INTENTS.get();
    }

    public static int getResolvedRecipeIntentCacheSize() {
        return Math.min(1_048_576, Math.max(16, RESOLVED_RECIPE_INTENT_CACHE_SIZE.get()));
    }

    public static boolean logMekanismRecipeIntentFastPath() {
        return enableMekanismRecipeIntentFastPath() && LOG_MEKANISM_RECIPE_INTENT_FAST_PATH.get();
    }

    public static boolean enableCreateRecipeIntentFastPath() {
        return enableRecipeIntentBridge() && ENABLE_CREATE_RECIPE_INTENT_FAST_PATH.get();
    }

    public static boolean logCapturedRecipeIntents() {
        return enableRecipeIntentBridge() && LOG_CAPTURED_RECIPE_INTENTS.get();
    }

    public static boolean logRecipeIntentRegistryEvictions() {
        return enableOptimizer() && LOG_RECIPE_INTENT_REGISTRY_EVICTIONS.get();
    }

    public static boolean logSlowCraftCalculations() {
        return enableOptimizer() && LOG_SLOW_CRAFT_CALCULATIONS.get();
    }

    public static int getSlowCraftCalculationMillis() {
        return Math.min(300_000, Math.max(1, SLOW_CRAFT_CALCULATION_MILLIS.get()));
    }

    public static boolean logCacheStatistics() {
        return enableOptimizer() && LOG_CACHE_STATISTICS.get();
    }

    public static boolean enableExperimentalCraftingEngine() {
        return enableOptimizer() && ENABLE_EXPERIMENTAL_CRAFTING_ENGINE.get();
    }

    public static boolean enableAqeBigCraftingProfile() {
        return enableOptimizer()
                && ENABLE_AQE_BIG_CRAFTING_PROFILE.get()
                && ModList.get().isLoaded("advanced_ae")
                && ModList.get().isLoaded("advanced_quantum_engineering");
    }

    public static boolean enableCraftingEngineShadowMode() {
        return enableCompiledCraftingGraph() && ENABLE_CRAFTING_ENGINE_SHADOW_MODE.get();
    }

    public static boolean logCraftingEngineShadowMismatches() {
        return enableCraftingEngineShadowMode() && LOG_CRAFTING_ENGINE_SHADOW_MISMATCHES.get();
    }

    public static int getCraftingEngineShadowMaximumPatterns() {
        return Math.min(1_048_576, Math.max(1, CRAFTING_ENGINE_SHADOW_MAXIMUM_PATTERNS.get()));
    }

    public static int getAuthoritativeMinimumShadowMatches() {
        return Math.min(
                MAXIMUM_AUTHORITATIVE_SHADOW_MATCHES,
                Math.max(0, AUTHORITATIVE_MINIMUM_SHADOW_MATCHES.get()));
    }

    public static boolean requireAqeBigPlanShadowQualification() {
        return REQUIRE_AQE_BIG_PLAN_SHADOW_QUALIFICATION.get();
    }

    public static boolean enableCompiledCraftingGraph() {
        return ENABLE_COMPILED_CRAFTING_GRAPH.get()
                && (enableExperimentalCraftingEngine() || enableAqeBigCraftingProfile());
    }

    public static boolean enableAuthoritativeCompiledPlanner() {
        return enableCompiledCraftingGraph() && ENABLE_AUTHORITATIVE_COMPILED_PLANNER.get();
    }

    public static boolean enableCheckedAe2CraftingArithmetic() {
        return ENABLE_CHECKED_AE2_CRAFTING_ARITHMETIC.get()
                && (enableExperimentalCraftingEngine() || enableAqeBigCraftingProfile());
    }

    public static boolean enableTransactionalBatchingV2() {
        return enableExperimentalCraftingEngine()
                && ENABLE_TRANSACTIONAL_BATCHING_V2.get()
                && PERSIST_BATCH_TRANSACTION_JOURNAL.get();
    }

    public static boolean enableGtceuNativeBatching() {
        return enableTransactionalBatchingV2() && ENABLE_GTCEU_NATIVE_BATCHING.get();
    }

    public static boolean enableMekanismNativeBatching() {
        return enableTransactionalBatchingV2() && ENABLE_MEKANISM_NATIVE_BATCHING.get();
    }

    public static boolean enableFairCraftingJobScheduler() {
        return enableExperimentalCraftingEngine() && ENABLE_FAIR_CRAFTING_JOB_SCHEDULER.get();
    }

    public static int getFairSchedulerOperationsPerTick() {
        return Math.min(1_048_576, Math.max(1, FAIR_SCHEDULER_OPERATIONS_PER_TICK.get()));
    }

    public static int getFairSchedulerQuantum() {
        return Math.min(65_536, Math.max(1, FAIR_SCHEDULER_QUANTUM.get()));
    }

    public static int getFairSchedulerTimeBudgetMillis() {
        return Math.min(45, Math.max(1, FAIR_SCHEDULER_TIME_BUDGET_MILLIS.get()));
    }

    public static boolean persistBatchTransactionJournal() {
        return PERSIST_BATCH_TRANSACTION_JOURNAL.get();
    }

    public static int getBatchTransactionJournalMaximumEntries() {
        return Math.min(16_384, Math.max(16, BATCH_TRANSACTION_JOURNAL_MAXIMUM_ENTRIES.get()));
    }

    public static int getBatchTransactionReconciliationIntervalTicks() {
        return Math.min(20 * 60, Math.max(1, BATCH_TRANSACTION_RECONCILIATION_INTERVAL_TICKS.get()));
    }

    public static int getNativeBatchMaximumExecutions() {
        return Math.min(1_048_576, Math.max(1, NATIVE_BATCH_MAXIMUM_EXECUTIONS.get()));
    }

    public static boolean enableBigIntegerCraftingBackend() {
        return ENABLE_BIG_INTEGER_CRAFTING_BACKEND.get();
    }

    public static boolean enableAtomicBigCapacityPlans() {
        return enableCompiledCraftingGraph()
                && enableBigIntegerCraftingBackend()
                && ENABLE_ATOMIC_BIG_CAPACITY_PLANS.get();
    }

    public static boolean enableBigIntegerGameplayExecution() {
        return enableCompiledCraftingGraph()
                && enableBigIntegerCraftingBackend()
                && ENABLE_BIG_INTEGER_GAMEPLAY_EXECUTION.get();
    }

    public static int getBigIntegerMaximumBits() {
        return Math.min(BigCountMath.HARD_MAXIMUM_BITS, Math.max(64, BIG_INTEGER_MAXIMUM_BITS.get()));
    }

    public static int getBigIntegerExecutionWindow() {
        return Math.min(1_048_576, Math.max(1, BIG_INTEGER_EXECUTION_WINDOW.get()));
    }

    public static int getBigIntegerMaximumWindowCalculationsPerTick() {
        return Math.min(
                64,
                Math.max(1, BIG_INTEGER_MAXIMUM_WINDOW_CALCULATIONS_PER_TICK.get()));
    }

    public static int getBigIntegerRetryBackoffTicks() {
        return Math.min(20 * 60, Math.max(1, BIG_INTEGER_RETRY_BACKOFF_TICKS.get()));
    }

    public static int getBigIntegerStatusPageEntries() {
        return Math.min(16_384, Math.max(16, BIG_INTEGER_STATUS_PAGE_ENTRIES.get()));
    }

    public static long getBigIntegerRuntimeCountBudgetBytes() {
        return Math.multiplyExact(
                (long) Math.min(4096, Math.max(32, BIG_INTEGER_RUNTIME_COUNT_BUDGET_MIB.get())),
                1024L * 1024L);
    }
}
