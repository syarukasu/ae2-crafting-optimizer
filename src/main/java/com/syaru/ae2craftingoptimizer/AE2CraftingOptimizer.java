package com.syaru.ae2craftingoptimizer;

import com.mojang.logging.LogUtils;
import com.syaru.ae2craftingoptimizer.api.batch.PatternBatchApi;
import com.syaru.ae2craftingoptimizer.api.batch.v2.PatternBatchV2Api;
import com.syaru.ae2craftingoptimizer.command.ACOIntentCommands;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.lifecycle.ACOServerLifecycle;
import com.syaru.ae2craftingoptimizer.network.BigCraftingNetwork;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
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

    public AE2CraftingOptimizer(IEventBus modBus, ModContainer container) {
        ACOConfig.register(container);
        PatternBatchApi.registerBuiltIns();
        PatternBatchV2Api.registerBuiltIns();
        modBus.addListener(BigCraftingNetwork::register);
        modBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        ACOServerLifecycle.register();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("{} initialized", MOD_NAME);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        ACOIntentCommands.register(
                event.getDispatcher(),
                event.getBuildContext());
    }
}
