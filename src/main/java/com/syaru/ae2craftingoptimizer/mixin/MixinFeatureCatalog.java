package com.syaru.ae2craftingoptimizer.mixin;

import com.syaru.ae2craftingoptimizer.optimization.OptimizationFeature;
import java.util.Map;
import java.util.Optional;

/**
 * MixinとIssue #129の機能契約を結ぶ明示台帳。
 *
 * <p>未登録Mixinは通常AE2へ影響し得る責務が監査されていないため、
 * {@link AcoMixinPlugin}がfail-closedで適用を拒否する。
 */
public final class MixinFeatureCatalog {
    private static final Map<String, OptimizationFeature> FEATURES = Map.ofEntries(
            entry("AdvancedAeCraftingCpuLogicExecutionBudgetMixin", OptimizationFeature.CRAFTING_EXECUTION_BUDGET),
            entry("AdvancedAeExactCraftingLogicMixin", OptimizationFeature.EXACT_VECTOR_CRAFTING),
            entry("AdvancedAeCraftingCpuLogicBatchSourceReceiptMixin", OptimizationFeature.TRANSACTIONAL_BATCHING),
            entry("AdvancedAeCraftingCpuLogicTransactionalBatchV2Mixin", OptimizationFeature.TRANSACTIONAL_BATCHING),
            entry("AdvancedAeCraftingCpuTransactionAccessMixin", OptimizationFeature.TRANSACTIONAL_BATCHING),
            entry("AdvancedAeCraftingCpuClusterRecoveryMixin", OptimizationFeature.TRANSACTIONAL_BATCHING),
            entry("AdvancedAeCraftingCpuAccessorMixin", OptimizationFeature.ADVANCED_AE_BIG_PROFILE),
            entry("AdvancedAeCraftingClusterBigWindowMixin", OptimizationFeature.BIG_INTEGER_GAMEPLAY_EXECUTION),
            entry("AdvancedAeBigCapacityPlanSubmissionMixin", OptimizationFeature.ATOMIC_BIG_CAPACITY_PLANS),
            entry("AdvancedAeCraftingCpuLogicBigChildMixin", OptimizationFeature.BIG_INTEGER_GAMEPLAY_EXECUTION),
            entry("AdvancedAeCraftingBlockEntityTransactionAccessMixin", OptimizationFeature.TRANSACTIONAL_BATCHING),
            entry("AdvancedAeExecutingCraftingJobTransactionAccessMixin", OptimizationFeature.TRANSACTIONAL_BATCHING),
            entry("AdvancedAeTaskProgressTransactionAccessMixin", OptimizationFeature.TRANSACTIONAL_BATCHING),
            entry("AdvancedAePatternProviderIntentCaptureMixin", OptimizationFeature.RECIPE_INTENT_BRIDGE),
            entry("AdvancedAeReactionChamberRecipeCacheMixin", OptimizationFeature.ADDON_MACHINE_CACHE),
            entry("Ae2OverclockParallelRuntimeCacheMixin", OptimizationFeature.ADDON_MACHINE_CACHE),
            entry("Ae2OverclockRuntimeCacheMixin", OptimizationFeature.ADDON_MACHINE_CACHE),
            entry("CraftingCalculationDiagnosticsMixin", OptimizationFeature.CRAFTING_QUERY_MEMOIZATION),
            entry("CraftingCalculationCheckedMathMixin", OptimizationFeature.CHECKED_CRAFTING_ARITHMETIC),
            entry("CraftingCalculationMemoLifecycleMixin", OptimizationFeature.CRAFTING_QUERY_MEMOIZATION),
            entry("Ae2BigCapacityPlanSubmissionMixin", OptimizationFeature.ATOMIC_BIG_CAPACITY_PLANS),
            entry("Ae2ExactCraftingLogicMixin", OptimizationFeature.EXACT_VECTOR_CRAFTING),
            entry("CraftAmountMenuLongAmountMixin", OptimizationFeature.LONG_ROOT_AMOUNTS),
            entry("CraftConfirmMenuLongAmountMixin", OptimizationFeature.LONG_ROOT_AMOUNTS),
            entry("CraftingPlanSummaryWidePlanMixin", OptimizationFeature.BIG_INTEGER_BACKEND),
            entry("CraftingCpuHelperFluidFastPathMixin", OptimizationFeature.FLUID_PATTERN_FAST_PATH),
            entry("CraftingCpuLogicExecutionBudgetMixin", OptimizationFeature.CRAFTING_EXECUTION_BUDGET),
            entry("CraftingCpuLogicBatchSourceReceiptMixin", OptimizationFeature.TRANSACTIONAL_BATCHING),
            entry("CraftingCpuLogicTransactionalBatchV2Mixin", OptimizationFeature.TRANSACTIONAL_BATCHING),
            entry("CraftingCpuClusterTransactionAccessMixin", OptimizationFeature.TRANSACTIONAL_BATCHING),
            entry("CraftingBlockEntityTransactionAccessMixin", OptimizationFeature.TRANSACTIONAL_BATCHING),
            entry("CraftingProviderRefreshCoalescingMixin", OptimizationFeature.PROVIDER_REFRESH_COALESCING),
            entry("CraftingServiceCalculationDeduplicationMixin", OptimizationFeature.ACTIVE_CALCULATION_DEDUPLICATION),
            entry("CraftingServiceInvalidationMixin", OptimizationFeature.PROVIDER_GENERATION_TRACKING),
            entry("CraftingServicePatternLookupCacheMixin", OptimizationFeature.PATTERN_LOOKUP_CACHE),
            entry("CraftingTreeCandidatePruningMixin", OptimizationFeature.CANDIDATE_PRUNING),
            entry("CraftingTreeCalculationMemoMixin", OptimizationFeature.CRAFTING_QUERY_MEMOIZATION),
            entry("CraftingTreeNodeCheckedMathMixin", OptimizationFeature.CHECKED_CRAFTING_ARITHMETIC),
            entry("CraftingTreeProcessCheckedMathMixin", OptimizationFeature.CHECKED_CRAFTING_ARITHMETIC),
            entry("CraftingSimulationStateCheckedMathMixin", OptimizationFeature.CHECKED_CRAFTING_ARITHMETIC),
            entry("DelegatingMEInventoryAccessor", OptimizationFeature.EXACT_INVENTORY_SNAPSHOT),
            entry("ExtendedAeAssemblerMatrixClusterCacheMixin", OptimizationFeature.ADDON_MACHINE_CACHE),
            entry("ExtendedAeAssemblerMatrixCrafterCacheMixin", OptimizationFeature.ADDON_MACHINE_CACHE),
            entry("ExtendedAeCircuitCutterRecipeCacheMixin", OptimizationFeature.CIRCUIT_CUTTER_RECIPE_CACHE),
            entry("ExtendedAePlusAssemblerMatrixBusyCaptureMixin", OptimizationFeature.ADDON_MACHINE_CACHE),
            entry("ExecutingCraftingJobTransactionAccessMixin", OptimizationFeature.TRANSACTIONAL_BATCHING),
            entry("GenericStackInvGenerationMixin", OptimizationFeature.PROVIDER_GENERATION_TRACKING),
            entry("GTCEuRecipeLogicIntentFastPathMixin", OptimizationFeature.RECIPE_INTENT_BRIDGE),
            entry("KeyCounterBigIntegerSidecarLifecycleMixin", OptimizationFeature.EXACT_INVENTORY_SNAPSHOT),
            entry("ListCraftingInventoryExactCountsMixin", OptimizationFeature.EXACT_INVENTORY_SNAPSHOT),
            entry("MekanismRecipeIntentFastPathMixin", OptimizationFeature.RECIPE_INTENT_BRIDGE),
            entry("MultiCraftingTrackerCraftRequestThrottleMixin", OptimizationFeature.EXPORT_CRAFT_REQUEST_BACKOFF),
            entry("NeoEco20_3CraftingCpuExecutionBudgetMixin", OptimizationFeature.CRAFTING_EXECUTION_BUDGET),
            entry("NeoEco20_4CraftingCpuExecutionBudgetMixin", OptimizationFeature.CRAFTING_EXECUTION_BUDGET),
            entry("NetworkCraftingSimulationStateAccessor", OptimizationFeature.EXACT_INVENTORY_SNAPSHOT),
            entry("NetworkStorageMountsAccessor", OptimizationFeature.EXACT_INVENTORY_SNAPSHOT),
            entry("PatternProviderLogicIntentCaptureMixin", OptimizationFeature.RECIPE_INTENT_BRIDGE),
            entry("PatternProviderLogicNativeBatchReceiptMixin", OptimizationFeature.NATIVE_MACHINE_BATCH),
            entry("AdvancedAePatternProviderLogicNativeBatchReceiptMixin", OptimizationFeature.NATIVE_MACHINE_BATCH),
            entry("TaskProgressTransactionAccessMixin", OptimizationFeature.TRANSACTIONAL_BATCHING),
            entry("ExtendedAePlusBigIntegerCellInventoryAccessor", OptimizationFeature.EXACT_INVENTORY_SNAPSHOT),
            entry("ExtendedAePlusBigIntegerCellConsistencyMixin", OptimizationFeature.EXACT_INVENTORY_SNAPSHOT),
            entry("ExtendedAePlusInfinityDataStorageConsistencyMixin", OptimizationFeature.EXACT_INVENTORY_SNAPSHOT),
            entry("MekanismCachedRecipeAccessor", OptimizationFeature.RECIPE_INTENT_BRIDGE),
            entry("P2PServiceTopologyDeduplicationMixin", OptimizationFeature.P2P_TOPOLOGY_DEDUPLICATION),
            entry("Ae2ScrollbarReleaseSafetyMixin", OptimizationFeature.SCROLLBAR_RELEASE_SAFETY),
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
