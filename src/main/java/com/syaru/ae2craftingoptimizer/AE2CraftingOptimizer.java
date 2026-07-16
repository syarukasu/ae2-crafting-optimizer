package com.syaru.ae2craftingoptimizer;

import com.mojang.logging.LogUtils;
import com.syaru.ae2craftingoptimizer.command.ACOIntentCommands;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.gtceu.GTCEuRecipeIntentFastPath;
import com.syaru.ae2craftingoptimizer.intent.RecipeIntentRegistry;
import com.syaru.ae2craftingoptimizer.mekanism.MekanismRecipeIntentFastPath;
import com.syaru.ae2craftingoptimizer.optimization.BusFuzzySearchCache;
import com.syaru.ae2craftingoptimizer.optimization.BusTransferSimulationCache;
import com.syaru.ae2craftingoptimizer.optimization.Ae2OverclockUpgradeCountCache;
import com.syaru.ae2craftingoptimizer.optimization.AssemblerMatrixBusyCountCache;
import com.syaru.ae2craftingoptimizer.optimization.CircuitCutterRecipeCache;
import com.syaru.ae2craftingoptimizer.optimization.CraftingExecutionBudget;
import com.syaru.ae2craftingoptimizer.optimization.P2PNotificationDeduplicator;
import com.syaru.ae2craftingoptimizer.optimization.OptimizationMetrics;
import com.syaru.ae2craftingoptimizer.optimization.ServerTickClock;
import com.syaru.ae2craftingoptimizer.optimization.ProviderPatternGenerationTracker;
import com.syaru.ae2craftingoptimizer.optimization.MethodHandleInvocationCache;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(AE2CraftingOptimizer.MODID)
public final class AE2CraftingOptimizer {
    public static final String MODID = "ae2_crafting_optimizer";
    public static final String MOD_NAME = "AE2 Crafting Optimizer";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AE2CraftingOptimizer() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ACOConfig.register();
        modBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(this::onDatapackSync);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("{} initialized", MOD_NAME);
    }

    private void onServerStarted(ServerStartedEvent event) {
        ServerTickClock.reset();
        Ae2OverclockUpgradeCountCache.clear();
        AssemblerMatrixBusyCountCache.clear();
        MethodHandleInvocationCache.clear();
        LOGGER.info("ACO active: {}", ACOConfig.enableOptimizer());
        LOGGER.info("ACO two-stage missing preview: {}, cache: {} entries / {}s TTL",
                ACOConfig.twoStageMissingPreview(),
                ACOConfig.getMissingPreviewCacheSize(),
                ACOConfig.getMissingPreviewCacheTtlSeconds());
        LOGGER.info("ACO preliminary missing preview minimum request amount: {}",
                ACOConfig.getMinimumRequestedAmountForPreview());
        LOGGER.info("ACO active crafting calculation deduplication: {}, window {} ticks",
                ACOConfig.deduplicateActiveCraftingCalculations(),
                ACOConfig.getActiveCalculationDeduplicationWindowTicks());
        LOGGER.info("ACO completed crafting plan cache: {}, successful plans: {}, max {} entries, TTL {} ticks",
                ACOConfig.cacheCompletedCraftingPlans(),
                ACOConfig.cacheSuccessfulCompletedCraftingPlans(),
                ACOConfig.getCompletedCraftingPlanCacheSize(),
                ACOConfig.getCompletedCraftingPlanCacheTtlTicks());
        LOGGER.info("ACO deterministic missing fast-fail: {}, minimum request {}, depth {}, nodes {}",
                ACOConfig.fastFailMissingCrafts(),
                ACOConfig.getMinimumRequestedAmountForFastFail(),
                ACOConfig.getDeterministicPreflightMaxDepth(),
                ACOConfig.getDeterministicPreflightMaxNodes());
        LOGGER.info("ACO pattern lookup cache: {}, max {} entries",
                ACOConfig.cachePatternLookups(),
                ACOConfig.getPatternLookupCacheSize());
        LOGGER.info("ACO craftable-set cache: {}, max {} entries",
                ACOConfig.cacheCraftableSets(),
                ACOConfig.getCraftableSetCacheSize());
        LOGGER.info("ACO storage watcher throttle: {} every {} ticks",
                ACOConfig.throttleStorageWatcherUpdates(),
                ACOConfig.getStorageWatcherUpdateIntervalTicks());
        LOGGER.info("ACO terminal snapshots: inventory throttle {} every {} ticks, craftables cache {} for {} ticks",
                ACOConfig.throttleTerminalInventorySnapshots(),
                ACOConfig.getTerminalInventorySnapshotIntervalTicks(),
                ACOConfig.cacheTerminalCraftables(),
                ACOConfig.getTerminalCraftableCacheTicks());
        LOGGER.info("ACO crafting execution throttle: {}, max {} effective co-processors per CPU",
                ACOConfig.throttleCraftingExecution(),
                ACOConfig.getMaxEffectiveCoprocessorsPerCpu());
        LOGGER.info("ACO adaptive execution budget: {}, target {} ms, minimum {} effective co-processors per CPU",
                ACOConfig.adaptiveCraftingExecutionBudget(),
                ACOConfig.getTargetCraftingExecutionMillis(),
                ACOConfig.getMinimumAdaptiveCoprocessorsPerCpu());
        LOGGER.info("ACO shared crafting execution budget: {}, target {} ms per ME grid/tick, minimum {} operation(s) per active CPU",
                ACOConfig.sharedCraftingExecutionBudget(),
                ACOConfig.getSharedCraftingExecutionMillisPerGrid(),
                ACOConfig.getMinimumSharedOperationsPerCpu());
        LOGGER.info("ACO grid tick budget: {}, defer {}, budget {} ms/tick, slow threshold {} us, backoff {} ticks",
                ACOConfig.enableGridTickBudget(),
                ACOConfig.deferHeavyGridTickables(),
                ACOConfig.getGridTickBudgetMillisPerServerTick(),
                ACOConfig.getSlowGridTickableMicros(),
                ACOConfig.getSlowGridTickableBackoffTicks());
        LOGGER.info("ACO idle grid tickable backoff: {}, after {} idle returns, backoff {} ticks",
                ACOConfig.backoffIdleGridTickables(),
                ACOConfig.getIdleGridTickableBackoffAfterFailures(),
                ACOConfig.getIdleGridTickableBackoffTicks());
        LOGGER.info("ACO IO bus operation cap: {}, max {} operations/tick",
                ACOConfig.limitIoBusOperationsPerTick(),
                ACOConfig.getMaxIoBusOperationsPerTick());
        LOGGER.info("ACO export-bus-style craft request throttle: {}, cooldown {} ticks, max {} entries",
                ACOConfig.throttleExportBusCraftRequests(),
                ACOConfig.getExportBusCraftFailureCooldownTicks(),
                ACOConfig.getExportBusCraftThrottleCacheSize());
        LOGGER.info(
                "ACO UEL optimizations: capability cache {}, negative bus simulation cache {}, candidate pruning {}, provider refresh coalescing {}, client terminal coalescing {}",
                ACOConfig.cacheAdjacentCapabilityLookups(),
                ACOConfig.cacheNegativeBusTransferSimulations(),
                ACOConfig.pruneInvalidCraftingCandidates(),
                ACOConfig.coalesceCraftingProviderRefreshes(),
                ACOConfig.coalesceClientTerminalViewUpdates());
        LOGGER.info("ACO ExtendedAE Circuit Cutter recipe cache: {}, max {} entries",
                ACOConfig.cacheCircuitCutterRecipes(),
                ACOConfig.getCircuitCutterRecipeCacheSize());
        LOGGER.info("ACO recipe intent bridge: {}, capture Pattern Provider intents: {}, TTL {} ticks, max {} entries",
                ACOConfig.enableRecipeIntentBridge(),
                ACOConfig.capturePatternProviderRecipeIntents(),
                ACOConfig.getRecipeIntentTtlTicks(),
                ACOConfig.getMaximumRecipeIntentEntries());
        LOGGER.info("ACO pattern micro-batching: {}, max {} execution(s)/push, single target required {}, target namespaces {}",
                ACOConfig.enablePatternMicroBatching(),
                ACOConfig.getMaxPatternExecutionsPerMicroBatch(),
                ACOConfig.requireSinglePatternProviderTarget(),
                ACOConfig.getPatternMicroBatchTargetNamespaces());
        LOGGER.info("ACO recipe intent fast paths - GTCEu: {}, Mekanism: {}, Create: {}",
                ACOConfig.enableGtceuRecipeIntentFastPath(),
                ACOConfig.enableMekanismRecipeIntentFastPath(),
                ACOConfig.enableCreateRecipeIntentFastPath());
        LOGGER.info("ACO GTCEu recipe intent fast path candidates: max {}, index cache {} recipe type(s), multiblock radius {}, nearby intents {}",
                ACOConfig.getGtceuRecipeIntentMaximumCandidates(),
                ACOConfig.getGtceuRecipeIntentIndexCacheSize(),
                ACOConfig.getGtceuRecipeIntentSearchRadius(),
                ACOConfig.getGtceuRecipeIntentNearbyMaximumEntries());
        LOGGER.info("ACO Mekanism recipe intent fast path candidates: max {}, index cache {} recipe type(s)",
                ACOConfig.getMekanismRecipeIntentMaximumCandidates(),
                ACOConfig.getMekanismRecipeIntentIndexCacheSize());
        LOGGER.info("ACO resolved recipe intent cache: {}, max {} entries per integration",
                ACOConfig.cacheResolvedRecipeIntents(),
                ACOConfig.getResolvedRecipeIntentCacheSize());
        LOGGER.info(
                "ACO add-on machine optimizations: master {}, reaction recipe {}, AE2 Overclock reflection {}, upgrade counts {}, matrix threads {}, busy count {}, status coalescing {}",
                ACOConfig.enableAddonMachineOptimizations(),
                ACOConfig.cacheReactionChamberRecipe(),
                ACOConfig.cacheAe2OverclockReflection(),
                ACOConfig.cacheAe2OverclockUpgradeCounts(),
                ACOConfig.cacheAssemblerMatrixThreadCounts(),
                ACOConfig.cacheAssemblerMatrixBusyCount(),
                ACOConfig.coalesceAssemblerMatrixStatusUpdates());
        logDeepRewriteFlags();
        LOGGER.info("ACO grid tickable hints: {}", ACOConfig.getHeavyGridTickableClassHints());
        LOGGER.info("ACO heavy process hints: {}", ACOConfig.getHeavyProcessHints());
    }

    private void logDeepRewriteFlags() {
        if (!ACOConfig.logDeepAe2RewriteFlags()) {
            return;
        }
        LOGGER.info(
                "ACO deep AE2 rewrite flags: master {}, patternSelection {}, networkForceUpdate {}, visibleTerminalRange {}, p2pTopology {}, busSearch {}, fluidPatternRework {}",
                ACOConfig.enableDeepAe2RewriteFlags(),
                ACOConfig.deepPatternSelectionByAvailability(),
                ACOConfig.deepNetworkForceUpdateCoalescing(),
                ACOConfig.deepVisibleTerminalRangeSync(),
                ACOConfig.deepP2PTopologyChangeOnlyRecheck(),
                ACOConfig.deepBusSearchRewrite(),
                ACOConfig.deepFluidPatternRework());
        LOGGER.info(
                "ACO deep limits: patterns {}, storage interval {} ticks, terminal range {}, P2P window {} tick(s), bus fuzzy cache {} entries / {} ticks",
                ACOConfig.getDeepPatternSelectionMaximumCandidates(),
                ACOConfig.getDeepNetworkUpdateIntervalTicks(),
                ACOConfig.getDeepTerminalRangeEntriesPerTick(),
                ACOConfig.getDeepP2PDuplicateWindowTicks(),
                ACOConfig.getDeepBusFuzzyCacheSize(),
                ACOConfig.getDeepBusFuzzyCacheTicks());
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        ACOIntentCommands.register(event.getDispatcher());
    }

    private void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            ServerTickClock.advance();
            return;
        }
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        RecipeIntentRegistry.cleanupExpired(event.getServer().overworld().getGameTime());
    }

    private void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) {
            return;
        }
        RecipeIntentRegistry.clear("server data reload");
        GTCEuRecipeIntentFastPath.clearIndexes("server data reload");
        MekanismRecipeIntentFastPath.clearIndexes("server data reload");
        CircuitCutterRecipeCache.clear();
        ProviderPatternGenerationTracker.clear();
    }

    private void onServerStopping(ServerStoppingEvent event) {
        if (ACOConfig.logCacheStatistics()) {
            for (String line : OptimizationMetrics.summaryLines()) {
                LOGGER.info("ACO statistics: {}", line);
            }
        }
        RecipeIntentRegistry.clear("server stopping");
        GTCEuRecipeIntentFastPath.clearIndexes("server stopping");
        MekanismRecipeIntentFastPath.clearIndexes("server stopping");
        CraftingExecutionBudget.clearAdaptiveState("server stopping");
        Ae2OverclockUpgradeCountCache.clear();
        AssemblerMatrixBusyCountCache.clear();
        MethodHandleInvocationCache.clear();
        ServerTickClock.reset();
        BusFuzzySearchCache.clear();
        BusTransferSimulationCache.clear();
        CircuitCutterRecipeCache.clear();
        ProviderPatternGenerationTracker.clear();
        P2PNotificationDeduplicator.clear();
        OptimizationMetrics.reset();
    }
}
