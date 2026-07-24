package com.syaru.ae2craftingoptimizer.network;

import appeng.api.stacks.AEKey;
import appeng.menu.me.crafting.CraftAmountMenu;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.api.big.AeKeyBigCraftingPacketCodec;
import com.syaru.ae2craftingoptimizer.api.big.BigCraftingStatusInbox;
import com.syaru.ae2craftingoptimizer.api.big.BigCraftingStatusPage;
import com.syaru.ae2craftingoptimizer.api.big.BigCraftingStatusPageCodec;
import com.syaru.ae2craftingoptimizer.client.LongCraftAmountClientHandler;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.craftingamount.LongCraftAmountMenuBridge;
import com.syaru.ae2craftingoptimizer.craftingamount.LongCraftAmountRules;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import io.netty.buffer.Unpooled;

/**
 * BigInteger Host状態とlongルート注文を運ぶProtocol Version付き通信Channel。
 * AE2本来のPacket IDやCodecは変更せず、ACO同士だけがこの別Channelを使用する。
 */
public final class BigCraftingNetwork {
    /** Statusだけだったprotocol 2へlong量の双方向Messageを追加したため、互換番号を更新する。 */
    public static final String PROTOCOL = "3";
    /** 既存BigInteger Status PageのMessage ID。 */
    private static final int STATUS_PAGE_MESSAGE_ID = 0;
    /** Integer.MAX_VALUE超過量をサーバーへ送るMessage ID。 */
    private static final int LONG_CRAFT_AMOUNT_REQUEST_MESSAGE_ID = 1;
    /** 戻る画面へlong初期値を復元するMessage ID。 */
    private static final int LONG_CRAFT_AMOUNT_STATE_MESSAGE_ID = 2;
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(AE2CraftingOptimizer.MODID, "big_crafting"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private BigCraftingNetwork() {
    }

    public static void register() {
        // Forge lifecycleから重複して呼ばれてもMessage IDを二重登録しない。
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        CHANNEL.registerMessage(
                STATUS_PAGE_MESSAGE_ID,
                StatusPageMessage.class,
                StatusPageMessage::encode,
                StatusPageMessage::decode,
                StatusPageMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                LONG_CRAFT_AMOUNT_REQUEST_MESSAGE_ID,
                LongCraftAmountRequestMessage.class,
                LongCraftAmountRequestMessage::encode,
                LongCraftAmountRequestMessage::decode,
                LongCraftAmountRequestMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(
                LONG_CRAFT_AMOUNT_STATE_MESSAGE_ID,
                LongCraftAmountStateMessage.class,
                LongCraftAmountStateMessage::encode,
                LongCraftAmountStateMessage::decode,
                LongCraftAmountStateMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void send(ServerPlayer player, BigCraftingStatusPage<AEKey> page) {
        // BigInteger backendを無効化した環境では、Statusだけが残る状態を許可しない。
        if (!ACOConfig.enableBigIntegerCraftingBackend()) {
            throw new IllegalStateException("ACO BigInteger crafting backend is disabled");
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new StatusPageMessage(page));
    }

    public static boolean fitsPacket(BigCraftingStatusPage<AEKey> page) {
        FriendlyByteBuf probe = new FriendlyByteBuf(Unpooled.buffer());
        try {
            codec(ACOConfig.getBigIntegerMaximumBits(), ACOConfig.getBigIntegerStatusPageEntries())
                    .write(probe, page);
            return true;
        } catch (BigCraftingStatusPageCodec.PacketTooLargeException tooLarge) {
            return false;
        } finally {
            probe.release();
        }
    }

    public static void sendLongCraftAmount(
            int containerId,
            long amount,
            boolean subtractStoredAmount,
            boolean autoStart) {
        // Client呼出側の不具合でint範囲を追加経路へ流さず、本家Packetとの二重送信を防ぐ。
        if (!ACOConfig.enableLongRootCraftAmounts()
                || !LongCraftAmountRules.isValidExtendedRequest(amount)) {
            throw new IllegalArgumentException(
                    "ACO long craft amount request must exceed Integer.MAX_VALUE");
        }
        CHANNEL.sendToServer(new LongCraftAmountRequestMessage(
                containerId,
                amount,
                subtractStoredAmount,
                autoStart));
    }

    public static void sendLongCraftAmountState(
            ServerPlayer player,
            int containerId,
            long amount) {
        // Server側でも不正な初期値を送信せず、Clientの量画面を本家値のままにする。
        if (!ACOConfig.enableLongRootCraftAmounts()
                || !LongCraftAmountRules.isValidExtendedRequest(amount)) {
            throw new IllegalArgumentException(
                    "ACO long craft amount state must exceed Integer.MAX_VALUE");
        }
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new LongCraftAmountStateMessage(containerId, amount));
    }

    private record StatusPageMessage(BigCraftingStatusPage<AEKey> page) {
        private StatusPageMessage {
            java.util.Objects.requireNonNull(page, "page");
        }

        private static void encode(StatusPageMessage message, FriendlyByteBuf buffer) {
            codec(ACOConfig.getBigIntegerMaximumBits(), ACOConfig.getBigIntegerStatusPageEntries())
                    .write(buffer, message.page());
        }

        private static StatusPageMessage decode(FriendlyByteBuf buffer) {
            return new StatusPageMessage(codec(
                    ACOConfig.getBigIntegerMaximumBits(),
                    ACOConfig.getBigIntegerStatusPageEntries()).read(buffer));
        }

        private static void handle(
                StatusPageMessage message,
                Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> BigCraftingStatusInbox.accept(message.page()));
            context.setPacketHandled(true);
        }

        private static BigCraftingStatusPageCodec<AEKey> codec(int bits, int entries) {
            return BigCraftingNetwork.codec(bits, entries);
        }
    }

    /**
     * AE2のConfirmAutoCraftPacket(int, ...)を変更せず、long量だけを運ぶC2S Message。
     */
    private record LongCraftAmountRequestMessage(
            int containerId,
            long amount,
            boolean subtractStoredAmount,
            boolean autoStart) {
        private static void encode(
                LongCraftAmountRequestMessage message,
                FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.containerId());
            buffer.writeLong(message.amount());
            buffer.writeBoolean(message.subtractStoredAmount());
            buffer.writeBoolean(message.autoStart());
        }

        private static LongCraftAmountRequestMessage decode(FriendlyByteBuf buffer) {
            return new LongCraftAmountRequestMessage(
                    buffer.readVarInt(),
                    buffer.readLong(),
                    buffer.readBoolean(),
                    buffer.readBoolean());
        }

        private static void handle(
                LongCraftAmountRequestMessage message,
                Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            ServerPlayer sender = context.getSender();
            context.enqueueWork(() -> {
                // ログアウト済み送信者、無効設定、int範囲または負数はMenuへ渡さない。
                if (sender == null
                        || !ACOConfig.enableLongRootCraftAmounts()
                        || !LongCraftAmountRules.isValidExtendedRequest(message.amount())) {
                    return;
                }
                /*
                 * 現在開いている量Menuとcontainer IDが一致した時だけ処理する。
                 * これにより、戻る・閉じる後に届いた古いPacketを別画面へ適用しない。
                 */
                if (!(sender.containerMenu instanceof CraftAmountMenu menu)
                        || menu.containerId != message.containerId()
                        || !(menu instanceof LongCraftAmountMenuBridge bridge)) {
                    return;
                }
                bridge.aco$confirmLong(
                        message.amount(),
                        message.subtractStoredAmount(),
                        message.autoStart());
            });
            context.setPacketHandled(true);
        }
    }

    /**
     * CraftConfirmMenuから戻ったClient量画面へ、ItemStackに載らないlong値を同期する。
     */
    private record LongCraftAmountStateMessage(int containerId, long amount) {
        private static void encode(
                LongCraftAmountStateMessage message,
                FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.containerId());
            buffer.writeLong(message.amount());
        }

        private static LongCraftAmountStateMessage decode(FriendlyByteBuf buffer) {
            return new LongCraftAmountStateMessage(
                    buffer.readVarInt(),
                    buffer.readLong());
        }

        private static void handle(
                LongCraftAmountStateMessage message,
                Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT,
                    () -> () -> LongCraftAmountClientHandler.accept(
                            message.containerId(),
                            message.amount())));
            context.setPacketHandled(true);
        }
    }

    private static BigCraftingStatusPageCodec<AEKey> codec(int bits, int entries) {
        return new BigCraftingStatusPageCodec<>(AeKeyBigCraftingPacketCodec.INSTANCE, bits, entries);
    }
}
