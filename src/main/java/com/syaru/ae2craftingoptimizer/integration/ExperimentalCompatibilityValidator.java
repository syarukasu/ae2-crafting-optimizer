package com.syaru.ae2craftingoptimizer.integration;

import com.syaru.ae2craftingoptimizer.access.CraftingClusterHostTransactionAccess;
import com.syaru.ae2craftingoptimizer.access.CraftingClusterRecoveryAccess;
import com.syaru.ae2craftingoptimizer.access.CraftingJobTransactionAccess;
import com.syaru.ae2craftingoptimizer.access.CraftingLogicTransactionAccess;
import com.syaru.ae2craftingoptimizer.access.CraftingOwnerTransactionAccess;
import com.syaru.ae2craftingoptimizer.access.CraftingTaskProgressAccess;
import com.syaru.ae2craftingoptimizer.access.PatternProviderTransactionAccess;
import com.syaru.ae2craftingoptimizer.api.batch.v2.BatchSourceReceiptStore;
import com.syaru.ae2craftingoptimizer.api.batch.v2.NativeBatchReceiptStore;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.mixin.MekanismCachedRecipeAccessor;
import com.syaru.ae2craftingoptimizer.scheduler.FairSchedulerStateStore;
import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.fml.ModList;

/**
 * 有効化された実験Mixinの対象クラス、Accessor、内部契約を起動時に監査する。
 * 対応外バージョンで処理を推測して続行せず、原因を列挙してFail-fastする。
 */
public final class ExperimentalCompatibilityValidator {
    private ExperimentalCompatibilityValidator() {
    }

    public static void validateEnabledFeatures() {
        if (!ACOConfig.enableExperimentalCraftingEngine()) {
            return;
        }
        List<String> failures = new ArrayList<>();
        requireExactVersion(failures, "ae2", "15.4.10");
        if ((ACOConfig.enableTransactionalBatchingV2()
                        || ACOConfig.enableFairCraftingJobScheduler())
                && ModList.get().isLoaded("advanced_ae")) {
            requireExactVersion(failures, "advanced_ae", "1.3.5-1.20.1");
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
                    MekanismCachedRecipeAccessor.class);
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

    private static void requireExactVersion(
            List<String> failures,
            String modId,
            String expectedVersion) {
        String installed = ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse(null);
        if (!expectedVersion.equals(installed)) {
            failures.add(modId + " version must be exactly " + expectedVersion
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
