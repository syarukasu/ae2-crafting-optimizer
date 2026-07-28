package com.syaru.ae2craftingoptimizer.menu;

import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.api.big.BigCraftingHostRuntime;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkHooks;

/** BigInteger親Job状態MenuをServerから開く。 */
public final class BigCraftingStatusMenus {
    private BigCraftingStatusMenus() {
    }

    public static void open(
            ServerPlayer player,
            BigCraftingHostRuntime<AEKey> host,
            UUID jobId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(jobId, "jobId");
        NetworkHooks.openScreen(
                player,
                new SimpleMenuProvider(
                        (containerId, inventory, ignored) ->
                                BigCraftingStatusMenu.server(
                                        containerId,
                                        inventory,
                                        host,
                                        jobId),
                        Component.translatable(
                                "menu.ae2_crafting_optimizer.big_crafting_status")),
                buffer -> {
                    buffer.writeUUID(host.runtimeId());
                    buffer.writeUUID(jobId);
                });
        /*
         * Open Screen packetの直後に初回Snapshotを送る。
         * Jobが次のServer tickで完了してもClientは開始値を一度受信できる。
         */
        if (player.containerMenu
                instanceof BigCraftingStatusMenu menu) {
            menu.broadcastChanges();
        }
    }
}
