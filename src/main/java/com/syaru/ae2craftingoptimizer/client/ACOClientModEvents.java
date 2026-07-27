package com.syaru.ae2craftingoptimizer.client;

import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.registry.ACOMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.common.Mod;

/** Dedicated ServerからClient GUIクラスを分離するMOD Bus登録。 */
@Mod.EventBusSubscriber(
        modid = AE2CraftingOptimizer.MODID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class ACOClientModEvents {
    private ACOClientModEvents() {
    }

    @SubscribeEvent
    public static void registerMenuScreens(
            FMLClientSetupEvent event) {
        /*
         * MenuScreensのMapはClient setup threadでだけ変更する。
         * Dedicated ServerはDist.CLIENT Subscriber自体を読み込まない。
         */
        event.enqueueWork(() -> MenuScreens.register(
                ACOMenus.BIG_CRAFTING_STATUS.get(),
                BigCraftingStatusScreen::new));
    }
}
