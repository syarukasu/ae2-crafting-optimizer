package com.syaru.ae2craftingoptimizer.menu;

import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.api.big.BigCraftingHostRuntime;
import com.syaru.ae2craftingoptimizer.integration.OptionalAqeBigCraftingExecution;
import com.syaru.ae2craftingoptimizer.network.BigCraftingNetwork;
import com.syaru.ae2craftingoptimizer.registry.ACOMenus;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * BigInteger親Jobを表示・取消する、Slotを持たない状態Menu。
 *
 * <p>表示同期は既存のbounded Status Pageだけを使い、Job会計や進捗を変更しない。</p>
 */
public final class BigCraftingStatusMenu
        extends AbstractContainerMenu {
    public static final int CANCEL_BUTTON_ID = 0;

    private final UUID runtimeId;
    private final UUID jobId;
    @Nullable
    private final BigCraftingHostRuntime<AEKey> serverHost;
    @Nullable
    private final ServerPlayer serverPlayer;
    private long lastStatusSyncTick = Long.MIN_VALUE;

    /** ForgeがClient側でOpen Screen payloadを復号するConstructor。 */
    public BigCraftingStatusMenu(
            int containerId,
            Inventory inventory,
            FriendlyByteBuf buffer) {
        this(
                containerId,
                inventory,
                null,
                Objects.requireNonNull(buffer, "buffer").readUUID(),
                buffer.readUUID());
    }

    private BigCraftingStatusMenu(
            int containerId,
            Inventory inventory,
            @Nullable BigCraftingHostRuntime<AEKey> serverHost,
            UUID runtimeId,
            UUID jobId) {
        super(ACOMenus.BIG_CRAFTING_STATUS.get(), containerId);
        this.runtimeId = Objects.requireNonNull(runtimeId, "runtimeId");
        this.jobId = Objects.requireNonNull(jobId, "jobId");
        this.serverHost = serverHost;
        this.serverPlayer = inventory.player instanceof ServerPlayer player
                ? player
                : null;
    }

    public static BigCraftingStatusMenu server(
            int containerId,
            Inventory inventory,
            BigCraftingHostRuntime<AEKey> host,
            UUID jobId) {
        Objects.requireNonNull(host, "host");
        return new BigCraftingStatusMenu(
                containerId,
                inventory,
                host,
                host.runtimeId(),
                jobId);
    }

    public UUID runtimeId() {
        return runtimeId;
    }

    public UUID jobId() {
        return jobId;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        // Client側MenuまたはPlayer切断後はServer Snapshotを生成しない。
        if (serverHost == null || serverPlayer == null) {
            return;
        }
        long gameTime = serverPlayer.serverLevel().getGameTime();
        // 同じServer tick中の再呼出しでは、同一Status Packetを重複送信しない。
        if (gameTime == lastStatusSyncTick) {
            return;
        }
        lastStatusSyncTick = gameTime;
        BigCraftingNetwork.send(
                serverPlayer,
                serverHost.statusPage(jobId));
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        // Cancel以外のButton IDやClient側予測では、Job所有権へ触れない。
        if (buttonId != CANCEL_BUTTON_ID
                || player.level().isClientSide
                || serverHost == null) {
            return false;
        }
        boolean cancelled = OptionalAqeBigCraftingExecution.cancel(
                serverHost,
                jobId);
        broadcastChanges();
        return cancelled;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        // 状態画面にはInventory Slotがない。
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        // Hostの一時的な構造解除でも状態画面を落とさず、Server Snapshotを表示する。
        return !player.isRemoved();
    }
}
