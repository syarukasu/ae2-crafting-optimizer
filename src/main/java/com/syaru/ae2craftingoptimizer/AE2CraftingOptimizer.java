package com.syaru.ae2craftingoptimizer;

import com.mojang.logging.LogUtils;
import com.syaru.ae2craftingoptimizer.command.ACOIntentCommands;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.gtceu.GTCEuRecipeIntentFastPath;
import com.syaru.ae2craftingoptimizer.intent.RecipeIntentRegistry;
import com.syaru.ae2craftingoptimizer.mekanism.MekanismRecipeIntentFastPath;
import com.syaru.ae2craftingoptimizer.optimization.BusFuzzySearchCache;
import com.syaru.ae2craftingoptimizer.optimization.P2PNotificationDeduplicator;
import net.minecraftforge.common.MinecraftForge;
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
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("{} initialized", MOD_NAME);
    }

    private void onServerStarted(ServerStartedEvent event) {
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
        LOGGER.info("ACO recipe intent bridge: {}, capture Pattern Provider intents: {}, TTL {} ticks, max {} entries",
                ACOConfig.enableRecipeIntentBridge(),
                ACOConfig.capturePatternProviderRecipeIntents(),
                ACOConfig.getRecipeIntentTtlTicks(),
                ACOConfig.getMaximumRecipeIntentEntries());
        LOGGER.info("ACO recipe intent fast paths - GTCEu: {}, Mekanism: {}, Create: {}",
                ACOConfig.enableGtceuRecipeIntentFastPath(),
                ACOConfig.enableMekanismRecipeIntentFastPath(),
                ACOConfig.enableCreateRecipeIntentFastPath());
        LOGGER.info("ACO GTCEu recipe intent fast path candidates: max {}, index cache {} recipe type(s)",
                ACOConfig.getGtceuRecipeIntentMaximumCandidates(),
                ACOConfig.getGtceuRecipeIntentIndexCacheSize());
        LOGGER.info("ACO Mekanism recipe intent fast path candidates: max {}, index cache {} recipe type(s)",
                ACOConfig.getMekanismRecipeIntentMaximumCandidates(),
                ACOConfig.getMekanismRecipeIntentIndexCacheSize());
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
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        RecipeIntentRegistry.cleanupExpired(event.getServer().overworld().getGameTime());
    }

    private void onServerStopping(ServerStoppingEvent event) {
        RecipeIntentRegistry.clear("server stopping");
        GTCEuRecipeIntentFastPath.clearIndexes("server stopping");
        MekanismRecipeIntentFastPath.clearIndexes("server stopping");
        BusFuzzySearchCache.clear();
        P2PNotificationDeduplicator.clear();
    }
}
