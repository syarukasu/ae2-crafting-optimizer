package com.syaru.ae2craftingoptimizer.lifecycle;

import com.syaru.ae2craftingoptimizer.api.big.BigCraftingHostRegistry;
import com.syaru.ae2craftingoptimizer.api.big.BigCraftingStatusInbox;
import com.syaru.ae2craftingoptimizer.batch.PatternTaskFingerprint;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.engine.Ae2CompiledCraftingGraphCache;
import com.syaru.ae2craftingoptimizer.engine.Ae2ImmutablePlanningGraphCache;
import com.syaru.ae2craftingoptimizer.engine.Ae2CraftingShadowValidator;
import com.syaru.ae2craftingoptimizer.engine.RecipeGenerationTracker;
import com.syaru.ae2craftingoptimizer.gtceu.GTCEuRecipeIntentFastPath;
import com.syaru.ae2craftingoptimizer.integration.ExperimentalCompatibilityValidator;
import com.syaru.ae2craftingoptimizer.integration.Ae2BigCraftingExecutionManager;
import com.syaru.ae2craftingoptimizer.intent.RecipeIntentRegistry;
import com.syaru.ae2craftingoptimizer.optimization.Ae2OverclockUpgradeCountCache;
import com.syaru.ae2craftingoptimizer.optimization.AssemblerMatrixBusyCountCache;
import com.syaru.ae2craftingoptimizer.optimization.CircuitCutterRecipeCache;
import com.syaru.ae2craftingoptimizer.optimization.CraftingExecutionBudget;
import com.syaru.ae2craftingoptimizer.optimization.CraftingCalculationDeduplicator;
import com.syaru.ae2craftingoptimizer.optimization.MethodHandleInvocationCache;
import com.syaru.ae2craftingoptimizer.optimization.TransactionalBatchTargetGuard;
import com.syaru.ae2craftingoptimizer.optimization.OptimizationMetrics;
import com.syaru.ae2craftingoptimizer.optimization.OptimizationFeatureGate;
import com.syaru.ae2craftingoptimizer.optimization.ProviderPatternGenerationTracker;
import com.syaru.ae2craftingoptimizer.optimization.ServerTickClock;
import com.syaru.ae2craftingoptimizer.optimization.TransactionalExactPatternCache;
import com.syaru.ae2craftingoptimizer.scheduler.PatternProviderRoutingCache;
import com.syaru.ae2craftingoptimizer.transaction.BatchTransactionRecovery;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * サーバーの開始・tick・データ再読込・停止に伴うACO状態を一元管理する。
 */
public final class ACOServerLifecycle {
    private ACOServerLifecycle() {
    }

    /** Forge EVENT_BUSへCommon側イベントを一度だけ配線する。 */
    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(
                ACOServerLifecycle::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(
                ACOServerLifecycle::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(
                ACOServerLifecycle::onDatapackSync);
        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.HIGHEST,
                ACOServerLifecycle::onServerStopping);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        OptimizationFeatureGate.resetDiagnostics();
        ExperimentalCompatibilityValidator.validateEnabledFeatures();
        ServerTickClock.reset();
        Ae2OverclockUpgradeCountCache.clear();
        AssemblerMatrixBusyCountCache.clear();
        MethodHandleInvocationCache.clear();
        ACOStartupReport.logActiveConfiguration();
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        // STARTでは共有tick番号だけを進め、重い処理を実行しない。
        if (event.phase == TickEvent.Phase.START) {
            ServerTickClock.advance();
            return;
        }
        // 将来Forgeへ別phaseが追加されてもEND以外では会計を進めない。
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        long gameTime = event.getServer().overworld().getGameTime();
        RecipeIntentRegistry.cleanupExpired(gameTime);
        BatchTransactionRecovery.tick(event.getServer(), gameTime);
        Ae2BigCraftingExecutionManager.tick(event.getServer());
    }

    private static void onDatapackSync(OnDatapackSyncEvent event) {
        // 個別プレイヤー同期はレシピ世代を変えないため、全体キャッシュを捨てない。
        if (event.getPlayer() != null) {
            return;
        }
        /*
         * Issue #167: workerが旧recipe世代を現行と判定できないよう、cache掃除より先に
         * revisionを公開する。掃除中に完了した旧計画も結果適用前の世代検証で破棄される。
         */
        RecipeGenerationTracker.invalidate();
        clearReloadSensitiveState("server data reload");
        BigCraftingStatusInbox.clear();
        BatchTransactionRecovery.clearRuntimeState();
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        // 診断を要求された時だけ停止直前の集計値を出力する。
        if (ACOConfig.logCacheStatistics()) {
            // 集計項目を一行ずつ出し、巨大な単一Log entryを作らない。
            for (String line : OptimizationMetrics.summaryLines()) {
                com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer.LOGGER.info(
                        "ACO statistics: {}",
                        line);
            }
        }
        clearReloadSensitiveState("server stopping");
        CraftingExecutionBudget.clearAdaptiveState("server stopping");
        Ae2OverclockUpgradeCountCache.clear();
        AssemblerMatrixBusyCountCache.clear();
        MethodHandleInvocationCache.clear();
        ServerTickClock.reset();
        OptimizationMetrics.reset();
        OptimizationFeatureGate.resetDiagnostics();
        Ae2CraftingShadowValidator.resetDiagnostics();
        BigCraftingStatusInbox.clear();
        Ae2BigCraftingExecutionManager.clear();
        BigCraftingHostRegistry.clear();
    }

    private static void clearReloadSensitiveState(String reason) {
        // lifecycle境界では索引だけを破棄し、計算本体やcaller所有Futureをcancelしない。
        CraftingCalculationDeduplicator.clear(reason);
        RecipeIntentRegistry.clear(reason);
        GTCEuRecipeIntentFastPath.clearIndexes(reason);
        CircuitCutterRecipeCache.clear();
        ProviderPatternGenerationTracker.clear();
        Ae2CompiledCraftingGraphCache.clear();
        Ae2ImmutablePlanningGraphCache.clear();
        PatternTaskFingerprint.clear();
        PatternProviderRoutingCache.clear();
        TransactionalExactPatternCache.clear();
        TransactionalBatchTargetGuard.clear();
    }
}
