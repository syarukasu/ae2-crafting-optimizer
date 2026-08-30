package com.syaru.ae2craftingoptimizer;

import com.mojang.logging.LogUtils;
import com.syaru.ae2craftingoptimizer.api.batch.v2.PatternBatchV2Api;
import com.syaru.ae2craftingoptimizer.api.contract.ExactCountLimits;
import com.syaru.ae2craftingoptimizer.api.contract.IntegrationCapabilities;
import com.syaru.ae2craftingoptimizer.api.contract.IntegrationCapabilitiesRegistry;
import com.syaru.ae2craftingoptimizer.command.ACOIntentCommands;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.lifecycle.ACOServerLifecycle;
import com.syaru.ae2craftingoptimizer.network.BigCraftingNetwork;
import com.syaru.ae2craftingoptimizer.optimization.CraftingCalculationDeduplicator;
import com.syaru.ae2craftingoptimizer.optimization.PlanningConfigurationRevisionTracker;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * ACOのForgeエントリーポイント。
 *
 * <p>ここでは登録とイベント配線だけを行う。サーバー状態の初期化・破棄は
 * {@link ACOServerLifecycle} が、起動時の設定報告は専用Reportが担当する。
 */
@Mod(AE2CraftingOptimizer.MODID)
public final class AE2CraftingOptimizer {
    public static final String MODID = "ae2_crafting_optimizer";
    public static final String MOD_NAME = "AE2 Crafting Optimizer";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AE2CraftingOptimizer() {
        BigCraftingNetwork.register();
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ACOConfig.register();
        PatternBatchV2Api.registerBuiltIns();
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::onConfigEvent);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        ACOServerLifecycle.register();
    }

    private void onConfigEvent(ModConfigEvent event) {
        // 同じmod event busへ届く他modのConfigでは、ACO計算revisionを進めない。
        if (!MODID.equals(event.getConfig().getModId())) {
            return;
        }
        PlanningConfigurationRevisionTracker.invalidate();
        /*
         * Issue #167: Config変更前のFutureを新設定の要求へ共有しない。
         * clearは索引だけを破棄し、既存subscriberや計算本体をcancelしない。
         */
        CraftingCalculationDeduplicator.clear(
                "ACO configuration " + event.getClass().getSimpleName());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        IntegrationCapabilities capabilities = IntegrationCapabilities.forAco(ExactCountLimits.defaults());
        IntegrationCapabilitiesRegistry.initializeOnce(capabilities);
        LOGGER.info("{} initialized", MOD_NAME);
        LOGGER.info(
                "ACO exact-count contract: {} count bits, {} canonical bytes, {} payload keys, {} encoded bytes",
                capabilities.exactCountLimits().maximumCountBits(),
                capabilities.exactCountLimits().maximumCanonicalBytes(),
                capabilities.exactCountLimits().maximumKeysPerPayload(),
                capabilities.exactCountLimits().maximumEncodedPayloadBytes());
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        ACOIntentCommands.register(
                event.getDispatcher(),
                event.getBuildContext());
    }
}
