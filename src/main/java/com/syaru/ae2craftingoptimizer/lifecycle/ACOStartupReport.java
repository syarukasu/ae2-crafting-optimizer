package com.syaru.ae2craftingoptimizer.lifecycle;

import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.api.batch.PatternBatchApi;
import com.syaru.ae2craftingoptimizer.api.batch.v2.BatchCpuAccountingMode;
import com.syaru.ae2craftingoptimizer.api.batch.v2.PatternBatchV2Api;
import com.syaru.ae2craftingoptimizer.api.big.BigCraftingEngineApi;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.integration.AppliedECompatibility;
import com.syaru.ae2craftingoptimizer.network.BigCraftingNetwork;
import net.minecraftforge.fml.ModList;

/** 起動時に有効機能と安全上限を一度だけ報告する。 */
final class ACOStartupReport {
    /** byteからMiBへ換算する定数。 */
    private static final long BYTES_PER_MEBIBYTE = 1024L * 1024L;

    private ACOStartupReport() {
    }

    static void logActiveConfiguration() {
        AE2CraftingOptimizer.LOGGER.info(
                "ACO active: {}",
                ACOConfig.enableOptimizer());
        AE2CraftingOptimizer.LOGGER.info(
                "ACO two-stage missing preview: {}, cache: {} entries / {}s TTL",
                ACOConfig.twoStageMissingPreview(),
                ACOConfig.getMissingPreviewCacheSize(),
                ACOConfig.getMissingPreviewCacheTtlSeconds());
        AE2CraftingOptimizer.LOGGER.info(
                "ACO preliminary missing preview minimum request amount: {}",
                ACOConfig.getMinimumRequestedAmountForPreview());
        AE2CraftingOptimizer.LOGGER.info(
                "ACO active crafting calculation deduplication: {}, window {} ticks",
                ACOConfig.deduplicateActiveCraftingCalculations(),
                ACOConfig.getActiveCalculationDeduplicationWindowTicks());
        AE2CraftingOptimizer.LOGGER.info(
                "ACO completed crafting plan cache: {}, successful plans: {}, max {} entries, TTL {} ticks",
                ACOConfig.cacheCompletedCraftingPlans(),
                ACOConfig.cacheSuccessfulCompletedCraftingPlans(),
                ACOConfig.getCompletedCraftingPlanCacheSize(),
                ACOConfig.getCompletedCraftingPlanCacheTtlTicks());
        AE2CraftingOptimizer.LOGGER.info(
                "ACO deterministic missing fast-fail: {}, minimum request {}, depth {}, nodes {}",
                ACOConfig.fastFailMissingCrafts(),
                ACOConfig.getMinimumRequestedAmountForFastFail(),
                ACOConfig.getDeterministicPreflightMaxDepth(),
                ACOConfig.getDeterministicPreflightMaxNodes());
        AE2CraftingOptimizer.LOGGER.info(
                "ACO pattern lookup cache: {}, max {} entries",
                ACOConfig.cachePatternLookups(),
                ACOConfig.getPatternLookupCacheSize());
        AE2CraftingOptimizer.LOGGER.info(
                "ACO craftable-set cache: {}, max {} entries",
                ACOConfig.cacheCraftableSets(),
                ACOConfig.getCraftableSetCacheSize());
        AE2CraftingOptimizer.LOGGER.info(
                "ACO storage watcher throttle: {} every {} ticks",
                ACOConfig.throttleStorageWatcherUpdates(),
                ACOConfig.getStorageWatcherUpdateIntervalTicks());
        AE2CraftingOptimizer.LOGGER.info(
                "ACO terminal snapshots: inventory throttle {} every {} ticks, craftables cache {} for {} ticks",
                ACOConfig.throttleTerminalInventorySnapshots(),
                ACOConfig.getTerminalInventorySnapshotIntervalTicks(),
                ACOConfig.cacheTerminalCraftables(),
                ACOConfig.getTerminalCraftableCacheTicks());
        AE2CraftingOptimizer.LOGGER.info(
                "ACO crafting execution throttle: {}, max {} effective co-processors per CPU",
                ACOConfig.throttleCraftingExecution(),
                ACOConfig.getMaxEffectiveCoprocessorsPerCpu());
        AE2CraftingOptimizer.LOGGER.info(
                "ACO adaptive execution budget: {}, target {} ms, minimum {} effective co-processors per CPU",
                ACOConfig.adaptiveCraftingExecutionBudget(),
                ACOConfig.getTargetCraftingExecutionMillis(),
                ACOConfig.getMinimumAdaptiveCoprocessorsPerCpu());
        AE2CraftingOptimizer.LOGGER.info(
                "ACO shared crafting execution budget: {}, target {} ms per ME grid/tick, minimum {} operation(s) per active CPU",
                ACOConfig.sharedCraftingExecutionBudget(),
                ACOConfig.getSharedCraftingExecutionMillisPerGrid(),
                ACOConfig.getMinimumSharedOperationsPerCpu());
        ModList.get().getModContainerById("neoecoae").ifPresent(container ->
                AE2CraftingOptimizer.LOGGER.info(
                        "ACO Neo ECO AE integration: detected {}, execution budget {}",
                        container.getModInfo().getVersion(),
                        ACOConfig.throttleNeoEcoAeExecution()));
        AppliedECompatibility.logDetectedVersion();
        AE2CraftingOptimizer.LOGGER.info(
                "ACO grid tick budget: {}, defer {}, budget {} ms/tick, slow threshold {} us, backoff {} ticks",
                ACOConfig.enableGridTickBudget(),
                ACOConfig.deferHeavyGridTickables(),
                ACOConfig.getGridTickBudgetMillisPerServerTick(),
                ACOConfig.getSlowGridTickableMicros(),
                ACOConfig.getSlowGridTickableBackoffTicks());
        AE2CraftingOptimizer.LOGGER.info(
                "ACO idle grid tickable backoff: {}, after {} idle returns, backoff {} ticks",
                ACOConfig.backoffIdleGridTickables(),
                ACOConfig.getIdleGridTickableBackoffAfterFailures(),
                ACOConfig.getIdleGridTickableBackoffTicks());
        AE2CraftingOptimizer.LOGGER.info(
                "ACO IO bus operation cap: {}, max {} operations/tick",
                ACOConfig.limitIoBusOperationsPerTick(),
                ACOConfig.getMaxIoBusOperationsPerTick());
        AE2CraftingOptimizer.LOGGER.info(
                "ACO export-bus-style craft request throttle: {}, cooldown {} ticks, max {} entries",
                ACOConfig.throttleExportBusCraftRequests(),
                ACOConfig.getExportBusCraftFailureCooldownTicks(),
                ACOConfig.getExportBusCraftThrottleCacheSize());
        AE2CraftingOptimizer.LOGGER.info(
                "ACO UEL optimizations: capability cache {}, negative bus simulation cache {}, candidate pruning {}, provider refresh coalescing {}, client terminal coalescing {}, scrollbar release safety {}",
                ACOConfig.cacheAdjacentCapabilityLookups(),
                ACOConfig.cacheNegativeBusTransferSimulations(),
                ACOConfig.pruneInvalidCraftingCandidates(),
                ACOConfig.coalesceCraftingProviderRefreshes(),
                ACOConfig.coalesceClientTerminalViewUpdates(),
                ACOConfig.fixStuckAe2ScrollbarRepeat());
        AE2CraftingOptimizer.LOGGER.info(
                "ACO ExtendedAE Circuit Cutter recipe cache: {}, max {} entries",
                ACOConfig.cacheCircuitCutterRecipes(),
                ACOConfig.getCircuitCutterRecipeCacheSize());
        AE2CraftingOptimizer.LOGGER.info(
                "ACO recipe intent bridge: {}, capture Pattern Provider intents: {}, TTL {} ticks, max {} entries",
                ACOConfig.enableRecipeIntentBridge(),
                ACOConfig.capturePatternProviderRecipeIntents(),
                ACOConfig.getRecipeIntentTtlTicks(),
                ACOConfig.getMaximumRecipeIntentEntries());
        AE2CraftingOptimizer.LOGGER.info(
                "ACO pattern micro-batching: compatibility-disabled (configured: {})",
                ACOConfig.patternMicroBatchingRequested());
        // 危険な旧Configが残っていても、無視した事実を明示する。
        if (ACOConfig.patternMicroBatchingRequested()) {
            AE2CraftingOptimizer.LOGGER.warn(
                    "ACO ignored enablePatternMicroBatching=true. Aggregate processing-pattern pushes can desynchronize AE2 task and waiting-output accounting; AE2's original execution path remains active.");
        }
        AE2CraftingOptimizer.LOGGER.info(
                "ACO sequential Instant dispatch: {} ({} ms/CPU/tick, probe {} operation(s), max {} operation(s)/measured wave; tick total remains bounded by maxPatterns and the shared grid budget)",
                ACOConfig.enableInstantPatternDispatch(),
                ACOConfig.getInstantPatternDispatchTimeBudgetMillis(),
                ACOConfig.getInstantPatternDispatchProbeOperations(),
                ACOConfig.getInstantPatternDispatchMaximumWaveOperations());
        AE2CraftingOptimizer.LOGGER.info(
                "ACO legacy transactional pattern batching: compatibility-disabled (configured {}, max {} prepared execution(s), sequential adapter {}, max {} push(es)/transaction, targets {}, adapters {})",
                ACOConfig.enableTransactionalPatternBatching(),
                ACOConfig.getMaxTransactionalPatternBatchExecutions(),
                ACOConfig.enableSequentialPatternProviderBatchAdapter(),
                ACOConfig.getMaxSequentialProviderExecutionsPerCall(),
                ACOConfig.getTransactionalBatchTargetNamespaces(),
                PatternBatchApi.registeredAdapterIds());
        AE2CraftingOptimizer.LOGGER.info(
                "ACO recipe intent fast paths - GTCEu: {}, Mekanism: {}, Create: {}",
                ACOConfig.enableGtceuRecipeIntentFastPath(),
                ACOConfig.enableMekanismRecipeIntentFastPath(),
                ACOConfig.enableCreateRecipeIntentFastPath());
        AE2CraftingOptimizer.LOGGER.info(
                "ACO GTCEu recipe intent fast path candidates: max {}, index cache {} recipe type(s), multiblock radius {}, nearby intents {}",
                ACOConfig.getGtceuRecipeIntentMaximumCandidates(),
                ACOConfig.getGtceuRecipeIntentIndexCacheSize(),
                ACOConfig.getGtceuRecipeIntentSearchRadius(),
                ACOConfig.getGtceuRecipeIntentNearbyMaximumEntries());
        AE2CraftingOptimizer.LOGGER.info(
                "ACO Mekanism recipe intent fast path candidates: max {}, index cache {} recipe type(s)",
                ACOConfig.getMekanismRecipeIntentMaximumCandidates(),
                ACOConfig.getMekanismRecipeIntentIndexCacheSize());
        AE2CraftingOptimizer.LOGGER.info(
                "ACO resolved recipe intent cache: {}, max {} entries per integration",
                ACOConfig.cacheResolvedRecipeIntents(),
                ACOConfig.getResolvedRecipeIntentCacheSize());
        AE2CraftingOptimizer.LOGGER.info(
                "ACO add-on machine optimizations: master {}, reaction recipe {}, AE2 Overclock runtime reflection {}, upgrade counts {}, matrix threads {}, busy count {}, status coalescing {}",
                ACOConfig.enableAddonMachineOptimizations(),
                ACOConfig.cacheReactionChamberRecipe(),
                ACOConfig.cacheAe2OverclockReflection(),
                ACOConfig.cacheAe2OverclockUpgradeCounts(),
                ACOConfig.cacheAssemblerMatrixThreadCounts(),
                ACOConfig.cacheAssemblerMatrixBusyCount(),
                ACOConfig.coalesceAssemblerMatrixStatusUpdates());
        logDeepRewriteFlags();
        AE2CraftingOptimizer.LOGGER.info(
                "ACO experimental crafting engine: {} (AQE profile {}, shadow {}, compiled graph {}, transaction V2 {}, GT native {}, Mekanism native {}, fair scheduler {}, persistent journal {})",
                ACOConfig.enableExperimentalCraftingEngine(),
                ACOConfig.enableAqeBigCraftingProfile(),
                ACOConfig.enableCraftingEngineShadowMode(),
                ACOConfig.enableCompiledCraftingGraph(),
                ACOConfig.enableTransactionalBatchingV2(),
                ACOConfig.enableGtceuNativeBatching(),
                ACOConfig.enableMekanismNativeBatching(),
                ACOConfig.enableFairCraftingJobScheduler(),
                ACOConfig.persistBatchTransactionJournal());
        logTransactionalBatchV2Status();
        AE2CraftingOptimizer.LOGGER.info(
                "ACO long root craft amount input: {} (existing AE2 int path remains authoritative through {})",
                ACOConfig.enableLongRootCraftAmounts(),
                Integer.MAX_VALUE);
        AE2CraftingOptimizer.LOGGER.info(
                "ACO BigInteger backend: {} (atomic plans {}, gameplay execution {}, API {}, protocol {}, max {} bits, execution window {}, status page {}, count budget {} MiB)",
                ACOConfig.enableBigIntegerCraftingBackend(),
                ACOConfig.enableAtomicBigCapacityPlans(),
                ACOConfig.enableBigIntegerGameplayExecution(),
                BigCraftingEngineApi.API_VERSION,
                BigCraftingNetwork.PROTOCOL,
                ACOConfig.getBigIntegerMaximumBits(),
                ACOConfig.getBigIntegerExecutionWindow(),
                ACOConfig.getBigIntegerStatusPageEntries(),
                ACOConfig.getBigIntegerRuntimeCountBudgetBytes()
                        / BYTES_PER_MEBIBYTE);
        AE2CraftingOptimizer.LOGGER.info(
                "ACO grid tickable hints: {}",
                ACOConfig.getHeavyGridTickableClassHints());
        AE2CraftingOptimizer.LOGGER.info(
                "ACO heavy process hints: {}",
                ACOConfig.getHeavyProcessHints());
    }

    private static void logTransactionalBatchV2Status() {
        var adapterIds =
                PatternBatchV2Api.registeredAdapterIds();
        var singlePhysicalOperationAdapters =
                adapterIds.stream()
                        .filter(id ->
                                PatternBatchV2Api.adapter(id)
                                        .map(adapter ->
                                                adapter.cpuAccountingMode()
                                                        == BatchCpuAccountingMode
                                                                .SINGLE_PHYSICAL_OPERATION)
                                        .orElse(false))
                        .toList();
        AE2CraftingOptimizer.LOGGER.info(
                "ACO transactional V2 adapters: {} (single physical operation: {})",
                adapterIds,
                singlePhysicalOperationAdapters);
        /*
         * Aggregate Adapter登録済みなのにV2が無効なら、標準経路へ戻って
         * 物理Thread数が見かけの成果物スループットになるため明示する。
         */
        if (!ACOConfig.enableTransactionalBatchingV2()
                && !singlePhysicalOperationAdapters.isEmpty()) {
            AE2CraftingOptimizer.LOGGER.warn(
                    "ACO transaction V2 is disabled while aggregate worker adapters {} are registered. "
                            + "Their long logical execution coefficient is inactive and crafting falls back to the parent mod path.",
                    singlePhysicalOperationAdapters);
        }
    }

    private static void logDeepRewriteFlags() {
        // ログOFF時は実験的な細分化Configを列挙しない。
        if (!ACOConfig.logDeepAe2RewriteFlags()) {
            return;
        }
        AE2CraftingOptimizer.LOGGER.info(
                "ACO deep AE2 rewrite flags: master {}, patternSelection {}, networkForceUpdate {}, visibleTerminalRange {}, p2pTopology {}, busSearch {}, fluidPatternRework {}",
                ACOConfig.enableDeepAe2RewriteFlags(),
                ACOConfig.deepPatternSelectionByAvailability(),
                ACOConfig.deepNetworkForceUpdateCoalescing(),
                ACOConfig.deepVisibleTerminalRangeSync(),
                ACOConfig.deepP2PTopologyChangeOnlyRecheck(),
                ACOConfig.deepBusSearchRewrite(),
                ACOConfig.deepFluidPatternRework());
        AE2CraftingOptimizer.LOGGER.info(
                "ACO deep limits: patterns {}, storage interval {} ticks, terminal range {}, P2P window {} tick(s), bus fuzzy cache {} entries / {} ticks",
                ACOConfig.getDeepPatternSelectionMaximumCandidates(),
                ACOConfig.getDeepNetworkUpdateIntervalTicks(),
                ACOConfig.getDeepTerminalRangeEntriesPerTick(),
                ACOConfig.getDeepP2PDuplicateWindowTicks(),
                ACOConfig.getDeepBusFuzzyCacheSize(),
                ACOConfig.getDeepBusFuzzyCacheTicks());
    }
}
