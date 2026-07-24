package com.syaru.ae2craftingoptimizer.client;

import appeng.menu.me.crafting.CraftAmountMenu;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.craftingamount.LongCraftAmountMenuBridge;
import com.syaru.ae2craftingoptimizer.craftingamount.LongCraftAmountRules;
import net.minecraft.client.Minecraft;

/**
 * 戻る操作で再作成されたCraftAmountMenuへ、サーバーのlong初期値を同期する。
 */
public final class LongCraftAmountClientHandler {
    private LongCraftAmountClientHandler() {
    }

    public static void accept(int containerId, long amount) {
        // 機能OFFまたはint範囲の値は本家Menu同期だけに任せる。
        if (!ACOConfig.enableLongRootCraftAmounts()
                || !LongCraftAmountRules.isValidExtendedRequest(amount)) {
            return;
        }
        var player = Minecraft.getInstance().player;
        // ログアウト中、別画面、古いcontainer IDへの同期は現在の画面へ適用しない。
        if (player == null
                || !(player.containerMenu instanceof CraftAmountMenu menu)
                || menu.containerId != containerId
                || !(menu instanceof LongCraftAmountMenuBridge bridge)) {
            return;
        }
        bridge.aco$setClientInitialAmount(amount);
    }
}
