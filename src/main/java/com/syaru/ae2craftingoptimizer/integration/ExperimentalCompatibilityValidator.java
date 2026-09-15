package com.syaru.ae2craftingoptimizer.integration;

import com.syaru.ae2craftingoptimizer.access.CheckedCraftingArithmeticHookAccess;
import com.syaru.ae2craftingoptimizer.access.CraftingClusterHostTransactionAccess;
import com.syaru.ae2craftingoptimizer.access.CraftingClusterRecoveryAccess;
import com.syaru.ae2craftingoptimizer.access.CraftingJobTransactionAccess;
import com.syaru.ae2craftingoptimizer.access.CraftingLogicTransactionAccess;
import com.syaru.ae2craftingoptimizer.access.CraftingOwnerTransactionAccess;
import com.syaru.ae2craftingoptimizer.access.CraftingServiceCalculationHookAccess;
import com.syaru.ae2craftingoptimizer.access.CraftingTaskProgressAccess;
import com.syaru.ae2craftingoptimizer.access.ExactBigIntegerInventoryHookAccess;
import com.syaru.ae2craftingoptimizer.access.ExactCraftingInventoryAccess;
import com.syaru.ae2craftingoptimizer.access.NetworkStorageMountsAccess;
import com.syaru.ae2craftingoptimizer.access.PatternProviderTargetAccess;
import com.syaru.ae2craftingoptimizer.api.batch.v2.BatchSourceReceiptStore;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.util.ArrayList;
import java.util.List;
import net.neoforged.fml.ModList;

/**
 * 有効化された実験Mixinの対象クラス、Accessor、内部契約を起動時に監査する。
 * 対応外バージョンで処理を推測して続行せず、原因を列挙してFail-fastする。
 */
public final class ExperimentalCompatibilityValidator {
    static final String SUPPORTED_AE2_VERSION = "19.2.17";

    private ExperimentalCompatibilityValidator() {
    }

    public static void validateEnabledFeatures() {
        List<String> failures = new ArrayList<>();
        // compiled plannerを有効化した場合だけ、対象AE2版の内部契約を監査する。
        boolean strictCraftingProfile = ACOConfig.enableCompiledCraftingGraph();
        if (strictCraftingProfile) {
            requireSupportedAe2Version(failures);
        }
        // 計算共有と完了Cacheは同じCraftingService Mixinを使用する。
        if (ACOConfig.deduplicateActiveCraftingCalculations()
                || ACOConfig.cacheCompletedCraftingPlans()) {
            require(failures, "appeng.me.service.CraftingService",
                    CraftingServiceCalculationHookAccess.class);
        }
        // long境界検査は四つのAE2計算段階がそろった場合だけ安全に有効化できる。
        if (ACOConfig.enableCheckedAe2CraftingArithmetic()) {
            require(failures, "appeng.crafting.CraftingCalculation",
                    CheckedCraftingArithmeticHookAccess.class);
            require(failures, "appeng.crafting.CraftingTreeNode",
                    CheckedCraftingArithmeticHookAccess.class);
            require(failures, "appeng.crafting.CraftingTreeProcess",
                    CheckedCraftingArithmeticHookAccess.class);
            require(failures, "appeng.crafting.inv.CraftingSimulationState",
                    CheckedCraftingArithmeticHookAccess.class);
        }
        // BigInteger在庫はPlanner専用mount列挙と一時KeyCounterのSidecar破棄だけを監査する。
        if (ACOConfig.enableExactBigIntegerInventorySnapshots()) {
            require(failures, "appeng.me.storage.NetworkStorage",
                    NetworkStorageMountsAccess.class);
            require(failures, "appeng.api.stacks.KeyCounter",
                    ExactBigIntegerInventoryHookAccess.class);
        }
        if (ACOConfig.enableTransactionalBatchingV2()) {
            require(failures, "appeng.crafting.execution.CraftingCpuLogic",
                    CraftingLogicTransactionAccess.class, BatchSourceReceiptStore.class);
            require(failures, "appeng.crafting.execution.ExecutingCraftingJob",
                    CraftingJobTransactionAccess.class);
            require(failures, "appeng.crafting.execution.ExecutingCraftingJob$TaskProgress",
                    CraftingTaskProgressAccess.class);
            require(failures, "appeng.me.cluster.implementations.CraftingCPUCluster",
                    CraftingOwnerTransactionAccess.class, CraftingClusterRecoveryAccess.class);
            require(failures, "appeng.blockentity.crafting.CraftingBlockEntity",
                    CraftingClusterHostTransactionAccess.class);
            require(failures, "appeng.helpers.patternprovider.PatternProviderLogic",
                    PatternProviderTargetAccess.class);
        }
        // 必須変換が一つでも欠ける場合は、部分適用による状態破損を避けて明示的に失敗する。
        if (!failures.isEmpty()) {
            throw new IllegalStateException(
                    "ACO experimental integration audit failed. Disable the affected experimental switch "
                            + "or install the exact supported dependency versions. Missing transformations: "
                            + String.join("; ", failures));
        }
    }

    private static void requireSupportedAe2Version(List<String> failures) {
        String installed = ModList.get().getModContainerById("ae2")
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse(null);
        // 内部Mixinを使うcompiled plannerだけは、検証済みAE2版へ限定する。
        if (!SUPPORTED_AE2_VERSION.equals(installed)) {
            failures.add("ae2 version must be " + SUPPORTED_AE2_VERSION
                    + " for the experimental engine (installed " + installed + ")");
        }
    }

    @SafeVarargs
    private static void require(
            List<String> failures,
            String className,
            Class<?>... requiredInterfaces) {
        try {
            Class<?> target = Class.forName(
                    className,
                    false,
                    ExperimentalCompatibilityValidator.class.getClassLoader());
            for (Class<?> required : requiredInterfaces) {
                if (!required.isAssignableFrom(target)) {
                    failures.add(className + " does not implement " + required.getName());
                }
            }
        } catch (LinkageError | ClassNotFoundException failure) {
            failures.add(className + " is unavailable (" + failure.getClass().getSimpleName() + ")");
        }
    }
}
