package com.syaru.ae2craftingoptimizer.integration;

import com.syaru.ae2craftingoptimizer.access.AdvancedAeExactCraftingJobAccess;
import com.syaru.ae2craftingoptimizer.access.AdvancedAeExactCraftingLogicAccess;
import com.syaru.ae2craftingoptimizer.access.BigCapacityPlanBoundaryAccess;
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
import com.syaru.ae2craftingoptimizer.access.MekanismCachedRecipeAccess;
import com.syaru.ae2craftingoptimizer.access.NetworkStorageMountsAccess;
import com.syaru.ae2craftingoptimizer.access.PatternProviderTransactionAccess;
import com.syaru.ae2craftingoptimizer.api.batch.v2.BatchSourceReceiptStore;
import com.syaru.ae2craftingoptimizer.api.batch.v2.NativeBatchReceiptStore;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.scheduler.FairSchedulerStateStore;
import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.fml.ModList;

/**
 * 有効化された実験Mixinの対象クラス、Accessor、内部契約を起動時に監査する。
 * 対応外バージョンで処理を推測して続行せず、原因を列挙してFail-fastする。
 */
public final class ExperimentalCompatibilityValidator {
    private static final String SUPPORTED_ADVANCED_AE_VERSION_PREFIX = "1.3.";
    private static final String SUPPORTED_ADVANCED_AE_VERSION_SUFFIX = "-1.20.1";

    private ExperimentalCompatibilityValidator() {
    }

    public static void validateEnabledFeatures() {
        List<String> failures = new ArrayList<>();
        /*
         * AQEまたは登録済み外部コンシューマがBigInteger計算を使用する。
         * 片方だけを見て早期returnすると、共通計算境界の欠落を見逃すため共通判定を使う。
         */
        boolean strictCraftingProfile = ACOConfig.enableExperimentalCraftingEngine()
                || ACOConfig.enableBigCraftingProfile();
        if (strictCraftingProfile) {
            requireSupportedAe2Version(failures);
        }
        // 計算共有・完了Cache・事前不足判定は同じCraftingService Mixinを使用する。
        if (ACOConfig.deduplicateActiveCraftingCalculations()
                || ACOConfig.cacheCompletedCraftingPlans()
                || ACOConfig.fastFailMissingCrafts()) {
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
        if ((ACOConfig.enableTransactionalBatchingV2()
                        || ACOConfig.enableFairCraftingJobScheduler()
                        || ACOConfig.enableAtomicBigCapacityPlans()
                        || ACOConfig.enableBigIntegerGameplayExecution())
                && ModList.get().isLoaded("advanced_ae")) {
            requireSupportedVersionSeries(
                    failures,
                    "advanced_ae",
                    SUPPORTED_ADVANCED_AE_VERSION_PREFIX,
                    SUPPORTED_ADVANCED_AE_VERSION_SUFFIX);
        }
        /*
         * Advanced AEのBigInteger提出境界はAtomic long計画とExact計画の双方が使用する。
         * どちらか一方でも有効なら、Cluster Mixinが実在することを監査する。
         */
        if ((ACOConfig.enableAtomicBigCapacityPlans()
                        || ACOConfig.enableBigIntegerGameplayExecution())
                && ModList.get().isLoaded("advanced_ae")) {
            require(failures, "net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPUCluster",
                    BigCapacityPlanBoundaryAccess.class);
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
                    NativeBatchReceiptStore.class, PatternProviderTransactionAccess.class);

            if (ModList.get().isLoaded("advanced_ae")) {
                require(failures, "net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic",
                        CraftingLogicTransactionAccess.class, BatchSourceReceiptStore.class);
                require(failures, "net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob",
                        CraftingJobTransactionAccess.class);
                require(failures, "net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob$TaskProgress",
                        CraftingTaskProgressAccess.class);
                require(failures, "net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU",
                        CraftingOwnerTransactionAccess.class);
                require(failures, "net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPUCluster",
                        CraftingClusterRecoveryAccess.class);
                require(failures, "net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic",
                        NativeBatchReceiptStore.class, PatternProviderTransactionAccess.class);
            }
        }
        /*
         * BigInteger Jobは別CPUを作らず、Advanced AEの実Jobカウンタを直接拡張する。
         * 三つの正本Mixinが一つでも欠ける状態ではlongへ縮退せず、起動時に明示的に拒否する。
         */
        if (ACOConfig.enableBigIntegerGameplayExecution()
                && ModList.get().isLoaded("advanced_ae")) {
            require(failures, "net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic",
                    AdvancedAeExactCraftingLogicAccess.class);
            require(failures, "net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob",
                    AdvancedAeExactCraftingJobAccess.class);
            require(failures, "net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob$TaskProgress",
                    CraftingTaskProgressAccess.class);
            require(failures, "appeng.crafting.inv.ListCraftingInventory",
                    ExactCraftingInventoryAccess.class);
        }
        if (ACOConfig.enableFairCraftingJobScheduler()) {
            require(failures, "appeng.crafting.execution.CraftingCpuLogic",
                    FairSchedulerStateStore.class);
            if (ModList.get().isLoaded("advanced_ae")) {
                require(failures, "net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic",
                        FairSchedulerStateStore.class);
            }
        }
        if (ACOConfig.enableMekanismNativeBatching()) {
            if (!OptionalNativeBatchIntegrations.mekanismRegistered()) {
                failures.add("Mekanism native adapter was not registered for the exact supported versions");
            }
            require(failures, "mekanism.api.recipes.cache.CachedRecipe",
                    MekanismCachedRecipeAccess.class);
        }
        if (ACOConfig.enableGtceuNativeBatching()
                && !OptionalNativeBatchIntegrations.gtceuRegistered()) {
            failures.add("GTCEu native adapter was not registered for the exact supported version");
        }
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
        if (!Ae2UelmCompatibility.isSupportedAe2Version(installed)) {
            failures.add("ae2 version must be "
                    + Ae2UelmCompatibility.UPSTREAM_VERSION + " or "
                    + Ae2UelmCompatibility.UELM_VERSION
                    + " for the experimental engine (installed " + installed + ")");
        }
    }

    private static void requireSupportedVersionSeries(
            List<String> failures,
            String modId,
            String versionPrefix,
            String versionSuffix) {
        String installed = ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse(null);
        // 実行中のバージョンが、監査済みの同一Minecraft系列かを接頭辞と接尾辞で確認する。
        boolean supported = installed != null
                && installed.startsWith(versionPrefix)
                && installed.endsWith(versionSuffix);
        if (!supported) {
            failures.add(modId + " version must match " + versionPrefix + "*" + versionSuffix
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
