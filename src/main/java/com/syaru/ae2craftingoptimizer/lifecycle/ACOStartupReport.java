package com.syaru.ae2craftingoptimizer.lifecycle;

import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.api.batch.v2.PatternBatchV2Api;
import com.syaru.ae2craftingoptimizer.api.big.BigCraftingEngineApi;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.integration.AppliedECompatibility;
import com.syaru.ae2craftingoptimizer.integration.MixinTransformationReport;
import com.syaru.ae2craftingoptimizer.network.BigCraftingNetwork;

/** 起動時にACOの現行中核機能と安全上限を一度だけ報告する。 */
final class ACOStartupReport {
    /** byteからMiBへ換算する二進単位。 */
    private static final long BYTES_PER_MEBIBYTE = 1024L * 1024L;

    private ACOStartupReport() {
    }

    static void logActiveConfiguration() {
        MixinTransformationReport.log();
        AE2CraftingOptimizer.LOGGER.info("ACO active: {}", ACOConfig.enableOptimizer());
        AE2CraftingOptimizer.LOGGER.info(
                "ACO planning core: deduplicate {}, completed cache {}, calculation-local memo {}, compiled graph {}, shadow {}",
                ACOConfig.deduplicateActiveCraftingCalculations(),
                ACOConfig.cacheCompletedCraftingPlans(),
                ACOConfig.memoizeCraftingCalculationQueries(),
                ACOConfig.enableCompiledCraftingGraph(),
                ACOConfig.enableCraftingEngineShadowMode());
        AE2CraftingOptimizer.LOGGER.info(
                "ACO execution budget: throttle {}, adaptive {}, shared-grid {}, instant dispatch {}, V2 adapters {}",
                ACOConfig.throttleCraftingExecution(),
                ACOConfig.adaptiveCraftingExecutionBudget(),
                ACOConfig.sharedCraftingExecutionBudget(),
                ACOConfig.enableInstantPatternDispatch(),
                PatternBatchV2Api.registeredAdapterIds());
        AE2CraftingOptimizer.LOGGER.info(
                "ACO recipe intent: bridge {}, GTCEu {}, Mekanism {}",
                ACOConfig.enableRecipeIntentBridge(),
                ACOConfig.enableGtceuRecipeIntentFastPath(),
                ACOConfig.enableMekanismRecipeIntentFastPath());
        AE2CraftingOptimizer.LOGGER.info(
                "ACO exact-count backend: enabled {}, atomic standard-AE2 submission {}, standard-AE2 execution {}, API {}, protocol {}, max {} bits, window {}, runtime budget {} MiB",
                ACOConfig.enableBigIntegerCraftingBackend(),
                ACOConfig.enableAtomicBigCapacityPlans(),
                ACOConfig.enableBigIntegerGameplayExecution(),
                BigCraftingEngineApi.API_VERSION,
                BigCraftingNetwork.PROTOCOL,
                ACOConfig.getBigIntegerMaximumBits(),
                ACOConfig.getBigIntegerExecutionWindow(),
                ACOConfig.getBigIntegerRuntimeCountBudgetBytes() / BYTES_PER_MEBIBYTE);
        AE2CraftingOptimizer.LOGGER.info(
                "ACO ownership boundary: standard AE2 exact execution only; external CPU execution remains add-on owned");
        AppliedECompatibility.logDetectedVersion();
    }
}
