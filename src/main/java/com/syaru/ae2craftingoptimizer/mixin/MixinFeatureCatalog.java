package com.syaru.ae2craftingoptimizer.mixin;

import com.syaru.ae2craftingoptimizer.optimization.OptimizationFeature;
import java.util.Map;
import java.util.Optional;

/** NeoForge core MixinとIssue #129の機能契約を結ぶ明示台帳。 */
public final class MixinFeatureCatalog {
    private static final Map<String, OptimizationFeature> FEATURES = Map.ofEntries(
            entry("Ae2BigCapacityPlanSubmissionMixin", OptimizationFeature.ATOMIC_BIG_CAPACITY_PLANS),
            entry("Ae2ExactCraftingLogicMixin", OptimizationFeature.EXACT_VECTOR_CRAFTING),
            entry("CraftAmountMenuLongAmountMixin", OptimizationFeature.LONG_ROOT_AMOUNTS),
            entry("CraftConfirmMenuLongAmountMixin", OptimizationFeature.LONG_ROOT_AMOUNTS),
            entry("CraftingBlockEntityTransactionAccessMixin", OptimizationFeature.TRANSACTIONAL_BATCHING),
            entry("CraftingCalculationCheckedMathMixin", OptimizationFeature.CHECKED_CRAFTING_ARITHMETIC),
            entry("CraftingCalculationDiagnosticsMixin", OptimizationFeature.CRAFTING_QUERY_MEMOIZATION),
            entry("CraftingCpuClusterTransactionAccessMixin", OptimizationFeature.TRANSACTIONAL_BATCHING),
            entry("CraftingCpuLogicBatchSourceReceiptMixin", OptimizationFeature.TRANSACTIONAL_BATCHING),
            entry("CraftingCpuLogicTransactionalBatchV2Mixin", OptimizationFeature.TRANSACTIONAL_BATCHING),
            entry("CraftingPlanSummaryWidePlanMixin", OptimizationFeature.BIG_INTEGER_BACKEND),
            entry("CraftingSimulationStateCheckedMathMixin", OptimizationFeature.CHECKED_CRAFTING_ARITHMETIC),
            entry("CraftingTreeNodeCheckedMathMixin", OptimizationFeature.CHECKED_CRAFTING_ARITHMETIC),
            entry("CraftingTreeProcessCheckedMathMixin", OptimizationFeature.CHECKED_CRAFTING_ARITHMETIC),
            entry("DelegatingMEInventoryAccessor", OptimizationFeature.EXACT_INVENTORY_SNAPSHOT),
            entry("ExecutingCraftingJobTransactionAccessMixin", OptimizationFeature.TRANSACTIONAL_BATCHING),
            entry("GenericStackInvGenerationMixin", OptimizationFeature.PROVIDER_GENERATION_TRACKING),
            entry("KeyCounterBigIntegerSidecarLifecycleMixin", OptimizationFeature.EXACT_INVENTORY_SNAPSHOT),
            entry("ListCraftingInventoryExactCountsMixin", OptimizationFeature.EXACT_INVENTORY_SNAPSHOT),
            entry("MEStorageMenuDisplaySaturationMixin", OptimizationFeature.EXACT_INVENTORY_SNAPSHOT),
            entry("NetworkCraftingSimulationStateAccessor", OptimizationFeature.EXACT_INVENTORY_SNAPSHOT),
            entry("NetworkStorageMountsAccessor", OptimizationFeature.EXACT_INVENTORY_SNAPSHOT),
            entry("PatternProviderLogicNativeBatchReceiptMixin", OptimizationFeature.NATIVE_MACHINE_BATCH),
            entry("TaskProgressTransactionAccessMixin", OptimizationFeature.TRANSACTIONAL_BATCHING),
            entry("CraftAmountScreenLongAmountMixin", OptimizationFeature.LONG_ROOT_AMOUNTS),
            entry("CraftConfirmScreenBigIntegerMixin", OptimizationFeature.BIG_INTEGER_BACKEND),
            entry("CraftConfirmTableRendererBigIntegerMixin", OptimizationFeature.BIG_INTEGER_BACKEND),
            entry("NumberEntryWidgetAccessor", OptimizationFeature.LONG_ROOT_AMOUNTS),
            entry("CraftingCalculationMemoLifecycleMixin", OptimizationFeature.CRAFTING_QUERY_MEMOIZATION),
            entry("CraftingCpuHelperFluidFastPathMixin", OptimizationFeature.FLUID_PATTERN_FAST_PATH),
            entry("CraftingCpuLogicExecutionBudgetMixin", OptimizationFeature.CRAFTING_EXECUTION_BUDGET),
            entry("CraftingProviderRefreshCoalescingMixin", OptimizationFeature.PROVIDER_REFRESH_COALESCING),
            entry("CraftingServiceCalculationDeduplicationMixin", OptimizationFeature.ACTIVE_CALCULATION_DEDUPLICATION),
            entry("CraftingServiceInvalidationMixin", OptimizationFeature.PROVIDER_GENERATION_TRACKING),
            entry("CraftingServicePatternLookupCacheMixin", OptimizationFeature.PATTERN_LOOKUP_CACHE),
            entry("CraftingTreeCandidatePruningMixin", OptimizationFeature.CANDIDATE_PRUNING),
            entry("CraftingTreeCalculationMemoMixin", OptimizationFeature.CRAFTING_QUERY_MEMOIZATION),
            entry("ExportBusCandidateCacheMixin", OptimizationFeature.EXPORT_CANDIDATE_CACHE),
            entry("IOPortSlotWindowMixin", OptimizationFeature.INCREMENTAL_IO_PORT),
            entry("MultiCraftingTrackerCraftRequestThrottleMixin", OptimizationFeature.EXPORT_CRAFT_REQUEST_BACKOFF),
            entry("PatternProviderLogicIntentCaptureMixin", OptimizationFeature.RECIPE_INTENT_BRIDGE),
            entry("P2PServiceTopologyDeduplicationMixin", OptimizationFeature.P2P_TOPOLOGY_DEDUPLICATION),
            entry("StorageImportScanOrderMixin", OptimizationFeature.IMPORT_LAST_SLOT_CACHE),
            entry("Ae2ScrollbarReleaseSafetyMixin", OptimizationFeature.SCROLLBAR_RELEASE_SAFETY),
            entry("ClientRepoUpdateCoalescingMixin", OptimizationFeature.TERMINAL_ASYNC_SEARCH));

    private MixinFeatureCatalog() {
    }

    public static Optional<OptimizationFeature> featureFor(String simpleMixinName) {
        return Optional.ofNullable(FEATURES.get(simpleMixinName));
    }

    public static boolean contains(String simpleMixinName) {
        return FEATURES.containsKey(simpleMixinName);
    }

    public static Map<String, OptimizationFeature> snapshot() {
        return Map.copyOf(FEATURES);
    }

    private static Map.Entry<String, OptimizationFeature> entry(
            String mixinName,
            OptimizationFeature feature) {
        return Map.entry(mixinName, feature);
    }
}
