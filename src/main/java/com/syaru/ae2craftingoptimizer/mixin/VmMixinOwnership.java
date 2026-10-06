package com.syaru.ae2craftingoptimizer.mixin;

import java.util.Set;

/** #218: the required bundled VM is the only calculator; retain native dispatch. */
final class VmMixinOwnership {
    private static final Set<String> EXCLUDED = Set.of(
            "CraftingCalculationDiagnosticsMixin", "CraftingCalculationCheckedMathMixin",
            "CraftingCalculationMemoLifecycleMixin", "CraftingCpuHelperCalculationMemoMixin",
            "CraftingSimulationIngredientSearchMixin", "KeyCounterCalculationIndexMixin",
            "CraftingPatternTaggedValidationMixin", "CraftingProviderRefreshCoalescingMixin",
            "CraftingServiceCalculationDeduplicationMixin", "CraftingTreeCalculationMemoMixin",
            "CraftingTreeNodeCheckedMathMixin", "CraftingTreeProcessCheckedMathMixin",
            "CraftingSimulationStateCheckedMathMixin", "CraftingCpuLogicExecutionBudgetMixin");

    private VmMixinOwnership() { }

    static boolean allows(String name) {
        return !EXCLUDED.contains(name.substring(name.lastIndexOf('.') + 1));
    }
}
