package com.syaru.ae2craftingoptimizer.mixin;

import com.syaru.ae2craftingoptimizer.optimization.OptimizationFeature;
import java.util.Map;
import java.util.Optional;

/**
 * MixinとIssue #129の機能契約を結ぶ明示台帳。
 *
 * <p>未登録Mixinは通常AE2へ影響し得る責務が監査されていないため、
 * 各Mixin Config Pluginがfail-closedで適用を拒否する。</p>
 */
public final class MixinFeatureCatalog {
    private static final Map<String, OptimizationFeature> FEATURES = Map.ofEntries(
            entry("AbstractMonitorPartDisplaySaturationMixin", OptimizationFeature.EXACT_INVENTORY_SNAPSHOT),
            entry("AdvancedAePatternProviderIntentCaptureMixin", OptimizationFeature.RECIPE_INTENT_BRIDGE),
            entry("AdvancedAePatternProviderLogicTargetAccessMixin", OptimizationFeature.TRANSACTIONAL_BATCHING),
            entry("AdvancedAeReactionChamberRecipeCacheMixin", OptimizationFeature.ADDON_MACHINE_CACHE),
            entry("Ae2OverclockParallelRuntimeCacheMixin", OptimizationFeature.ADDON_MACHINE_CACHE),
            entry("Ae2OverclockRuntimeCacheMixin", OptimizationFeature.ADDON_MACHINE_CACHE),
            entry("Ae2BigCapacityPlanSubmissionMixin", OptimizationFeature.ATOMIC_BIG_CAPACITY_PLANS),
            entry("Ae2ExactCraftingLogicMixin", OptimizationFeature.EXACT_VECTOR_CRAFTING),
            entry("CraftAmountMenuLongAmountMixin", OptimizationFeature.LONG_ROOT_AMOUNTS),
            entry("CraftConfirmMenuLongAmountMixin", OptimizationFeature.LONG_ROOT_AMOUNTS),
            entry("CraftingBlockEntityTransactionAccessMixin", OptimizationFeature.TRANSACTIONAL_BATCHING),
            entry("CraftingCalculationCheckedMathMixin", OptimizationFeature.CHECKED_CRAFTING_ARITHMETIC),
            entry("CraftingCalculationDiagnosticsMixin", OptimizationFeature.CRAFTING_QUERY_MEMOIZATION),
            entry("CraftingCalculationMemoLifecycleMixin", OptimizationFeature.CRAFTING_QUERY_MEMOIZATION),
            entry("CraftingCpuHelperCalculationMemoMixin", OptimizationFeature.CRAFTING_QUERY_MEMOIZATION),
            entry("CraftingCpuClusterTransactionAccessMixin", OptimizationFeature.TRANSACTIONAL_BATCHING),
            entry("CraftingCpuLogicBatchSourceReceiptMixin", OptimizationFeature.TRANSACTIONAL_BATCHING),
            entry("CraftingCpuLogicExecutionBudgetMixin", OptimizationFeature.CRAFTING_EXECUTION_BUDGET),
            entry("CraftingCpuLogicTransactionalBatchV2Mixin", OptimizationFeature.TRANSACTIONAL_BATCHING),
            entry("CraftingPlanSummaryWidePlanMixin", OptimizationFeature.BIG_INTEGER_BACKEND),
            entry("CraftingProviderRefreshCoalescingMixin", OptimizationFeature.PROVIDER_REFRESH_COALESCING),
            entry("CraftingServiceCalculationDeduplicationMixin", OptimizationFeature.ACTIVE_CALCULATION_DEDUPLICATION),
            entry("CraftingServiceInvalidationMixin", OptimizationFeature.PROVIDER_GENERATION_TRACKING),
            entry("CraftingSimulationStateCheckedMathMixin", OptimizationFeature.CHECKED_CRAFTING_ARITHMETIC),
            entry("CraftingTreeCalculationMemoMixin", OptimizationFeature.CRAFTING_QUERY_MEMOIZATION),
            entry("CraftingTreeNodeCheckedMathMixin", OptimizationFeature.CHECKED_CRAFTING_ARITHMETIC),
            entry("CraftingTreeProcessCheckedMathMixin", OptimizationFeature.CHECKED_CRAFTING_ARITHMETIC),
            entry("DelegatingMEInventoryAccessor", OptimizationFeature.EXACT_INVENTORY_SNAPSHOT),
            entry("ExtendedAeAssemblerMatrixClusterCacheMixin", OptimizationFeature.ADDON_MACHINE_CACHE),
            entry("ExtendedAeAssemblerMatrixCrafterCacheMixin", OptimizationFeature.ADDON_MACHINE_CACHE),
            entry("ExtendedAeCircuitCutterRecipeCacheMixin", OptimizationFeature.CIRCUIT_CUTTER_RECIPE_CACHE),
            entry("ExtendedAePlusAssemblerMatrixBusyCaptureMixin", OptimizationFeature.ADDON_MACHINE_CACHE),
            entry("ExtendedAePlusBigIntegerCellInventoryAccessor", OptimizationFeature.EXACT_INVENTORY_SNAPSHOT),
            entry("ExtendedAePlusBigIntegerCellConsistencyMixin", OptimizationFeature.EXACT_INVENTORY_SNAPSHOT),
            entry("ExtendedAePlusInfinityDataStorageConsistencyMixin", OptimizationFeature.EXACT_INVENTORY_SNAPSHOT),
            entry("ExecutingCraftingJobTransactionAccessMixin", OptimizationFeature.TRANSACTIONAL_BATCHING),
            entry("GTCEuRecipeLogicIntentFastPathMixin", OptimizationFeature.RECIPE_INTENT_BRIDGE),
            entry("KeyCounterBigIntegerSidecarLifecycleMixin", OptimizationFeature.EXACT_INVENTORY_SNAPSHOT),
            entry("ListCraftingInventoryExactCountsMixin", OptimizationFeature.EXACT_INVENTORY_SNAPSHOT),
            entry("MEStorageMenuDisplaySaturationMixin", OptimizationFeature.EXACT_INVENTORY_SNAPSHOT),
            entry("NeoEcoCraftingCpuExecutionBudgetMixin", OptimizationFeature.CRAFTING_EXECUTION_BUDGET),
            entry("NetworkCraftingSimulationStateAccessor", OptimizationFeature.EXACT_INVENTORY_SNAPSHOT),
            entry("NetworkStorageCraftingPlanGenerationMixin", OptimizationFeature.PLANNING_REVISION_TRACKING),
            entry("NetworkStorageMountsAccessor", OptimizationFeature.EXACT_INVENTORY_SNAPSHOT),
            entry("PatternProviderLogicIntentCaptureMixin", OptimizationFeature.RECIPE_INTENT_BRIDGE),
            entry("PatternProviderLogicTargetAccessMixin", OptimizationFeature.TRANSACTIONAL_BATCHING),
            entry("StorageServiceCraftingPlanGenerationMixin", OptimizationFeature.PLANNING_REVISION_TRACKING),
            entry("TaskProgressTransactionAccessMixin", OptimizationFeature.TRANSACTIONAL_BATCHING),
            entry("CraftAmountScreenLongAmountMixin", OptimizationFeature.LONG_ROOT_AMOUNTS),
            entry("CraftConfirmScreenBigIntegerMixin", OptimizationFeature.BIG_INTEGER_BACKEND),
            entry("CraftConfirmTableRendererBigIntegerMixin", OptimizationFeature.BIG_INTEGER_BACKEND),
            entry("NumberEntryWidgetAccessor", OptimizationFeature.LONG_ROOT_AMOUNTS));

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
