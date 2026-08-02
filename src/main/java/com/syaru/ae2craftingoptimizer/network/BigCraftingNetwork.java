package com.syaru.ae2craftingoptimizer.network;

import appeng.api.stacks.AEKey;
import appeng.menu.me.crafting.CraftAmountMenu;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.api.big.AeKeyBigCraftingPacketCodec;
import com.syaru.ae2craftingoptimizer.api.big.BigCraftingStatusInbox;
import com.syaru.ae2craftingoptimizer.api.big.BigCraftingStatusPage;
import com.syaru.ae2craftingoptimizer.api.big.BigCraftingStatusPageCodec;
import com.syaru.ae2craftingoptimizer.client.BigCraftingPlanClientStore;
import com.syaru.ae2craftingoptimizer.client.LongCraftAmountClientHandler;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.craftingamount.LongCraftAmountMenuBridge;
import com.syaru.ae2craftingoptimizer.craftingamount.LongCraftAmountRules;
import com.syaru.ae2craftingoptimizer.engine.BigCraftingPlanSummary;
import com.syaru.ae2craftingoptimizer.engine.BigIntegerBufferCodec;
import io.netty.buffer.Unpooled;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * BigInteger Host状態とlongルート注文を運ぶ、ACO専用のNeoForge Payload群。
 * AE2本来のPayload IDやCodecは変更せず、ACO同士だけがこの追加Protocolを使用する。
 */
public final class BigCraftingNetwork {
    /** Craft確認画面のBigInteger Summaryを含むACO通信互換番号。 */
    public static final String PROTOCOL = "4";
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private BigCraftingNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        // Mod busが再入しても同じPayload IDを二重登録しない。
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }

        var registrar = event.registrar(AE2CraftingOptimizer.MODID).versioned(PROTOCOL);
        registrar.playToClient(
                StatusPageMessage.TYPE,
                StatusPageMessage.STREAM_CODEC,
                StatusPageMessage::handle);
        registrar.playToServer(
                LongCraftAmountRequestMessage.TYPE,
                LongCraftAmountRequestMessage.STREAM_CODEC,
                LongCraftAmountRequestMessage::handle);
        registrar.playToClient(
                LongCraftAmountStateMessage.TYPE,
                LongCraftAmountStateMessage.STREAM_CODEC,
                LongCraftAmountStateMessage::handle);
        registrar.playToClient(
                ExactCraftingPlanSummaryMessage.TYPE,
                ExactCraftingPlanSummaryMessage.STREAM_CODEC,
                ExactCraftingPlanSummaryMessage::handle);
    }

    public static void send(ServerPlayer player, BigCraftingStatusPage<AEKey> page) {
        // Backend無効時に表示だけ同期すると、Clientが実在しないJobを表示するため拒否する。
        if (!ACOConfig.enableBigIntegerCraftingBackend()) {
            throw new IllegalStateException("ACO BigInteger crafting backend is disabled");
        }
        PacketDistributor.sendToPlayer(player, new StatusPageMessage(page));
    }

    public static boolean fitsPacket(ServerPlayer player, BigCraftingStatusPage<AEKey> page) {
        RegistryFriendlyByteBuf probe = new RegistryFriendlyByteBuf(
                Unpooled.buffer(),
                player.registryAccess(),
                ConnectionType.NEOFORGE);
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
        // int範囲はAE2本来のPayloadへ任せ、二重送信を防ぐ。
        if (!ACOConfig.enableLongRootCraftAmounts()
                || !LongCraftAmountRules.isValidExtendedRequest(amount)) {
            throw new IllegalArgumentException(
                    "ACO long craft amount request must exceed Integer.MAX_VALUE");
        }
        PacketDistributor.sendToServer(new LongCraftAmountRequestMessage(
                containerId,
                amount,
                subtractStoredAmount,
                autoStart));
    }

    public static void sendLongCraftAmountState(
            ServerPlayer player,
            int containerId,
            long amount) {
        // Server側でもint範囲や負数を追加Payloadへ載せない。
        if (!ACOConfig.enableLongRootCraftAmounts()
                || !LongCraftAmountRules.isValidExtendedRequest(amount)) {
            throw new IllegalArgumentException(
                    "ACO long craft amount state must exceed Integer.MAX_VALUE");
        }
        PacketDistributor.sendToPlayer(
                player,
                new LongCraftAmountStateMessage(containerId, amount));
    }

    public static void sendExactCraftingPlanSummary(
            ServerPlayer player,
            int containerId,
            BigCraftingPlanSummary summary) {
        Map<AEKey, BigCraftingPlanSummary.Entry> entries =
                summary == null ? Map.of() : summary.exactDisplayEntries();
        int maximumEntries = ACOConfig.getBigIntegerStatusPageEntries();
        if (entries.size() > maximumEntries) {
            throw new IllegalArgumentException(
                    "Craft confirmation has " + entries.size()
                            + " exact rows, above configured packet cap " + maximumEntries);
        }
        PacketDistributor.sendToPlayer(
                player,
                summary == null
                        ? ExactCraftingPlanSummaryMessage.clear(containerId)
                        : ExactCraftingPlanSummaryMessage.present(
                                containerId,
                                summary.usedBytes(),
                                entries));
    }

    private record StatusPageMessage(BigCraftingStatusPage<AEKey> page)
            implements CustomPacketPayload {
        private static final Type<StatusPageMessage> TYPE = BigCraftingNetwork.type("status_page");
        private static final StreamCodec<RegistryFriendlyByteBuf, StatusPageMessage> STREAM_CODEC =
                StreamCodec.ofMember(StatusPageMessage::write, StatusPageMessage::decode);

        private StatusPageMessage {
            java.util.Objects.requireNonNull(page, "page");
        }

        @Override
        public Type<StatusPageMessage> type() {
            return TYPE;
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            codec(ACOConfig.getBigIntegerMaximumBits(), ACOConfig.getBigIntegerStatusPageEntries())
                    .write(buffer, page);
        }

        private static StatusPageMessage decode(RegistryFriendlyByteBuf buffer) {
            return new StatusPageMessage(codec(
                    ACOConfig.getBigIntegerMaximumBits(),
                    ACOConfig.getBigIntegerStatusPageEntries()).read(buffer));
        }

        private static void handle(StatusPageMessage message, IPayloadContext context) {
            BigCraftingStatusInbox.accept(message.page());
        }
    }

    /** AE2のint注文Payloadを変えず、long量だけを運ぶC2S Payload。 */
    private record LongCraftAmountRequestMessage(
            int containerId,
            long amount,
            boolean subtractStoredAmount,
            boolean autoStart) implements CustomPacketPayload {
        private static final Type<LongCraftAmountRequestMessage> TYPE =
                BigCraftingNetwork.type("long_craft_amount_request");
        private static final StreamCodec<RegistryFriendlyByteBuf, LongCraftAmountRequestMessage> STREAM_CODEC =
                StreamCodec.ofMember(
                        LongCraftAmountRequestMessage::write,
                        LongCraftAmountRequestMessage::decode);

        @Override
        public Type<LongCraftAmountRequestMessage> type() {
            return TYPE;
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(containerId);
            buffer.writeLong(amount);
            buffer.writeBoolean(subtractStoredAmount);
            buffer.writeBoolean(autoStart);
        }

        private static LongCraftAmountRequestMessage decode(RegistryFriendlyByteBuf buffer) {
            return new LongCraftAmountRequestMessage(
                    buffer.readVarInt(),
                    buffer.readLong(),
                    buffer.readBoolean(),
                    buffer.readBoolean());
        }

        private static void handle(LongCraftAmountRequestMessage message, IPayloadContext context) {
            if (!(context.player() instanceof ServerPlayer sender)) {
                return;
            }
            // 無効設定、int範囲または負数はMenuへ渡さない。
            if (!ACOConfig.enableLongRootCraftAmounts()
                    || !LongCraftAmountRules.isValidExtendedRequest(message.amount())) {
                return;
            }
            // 古い画面から遅れて届いたPayloadを別Menuへ適用しない。
            if (!(sender.containerMenu instanceof CraftAmountMenu menu)
                    || menu.containerId != message.containerId()
                    || !(menu instanceof LongCraftAmountMenuBridge bridge)) {
                return;
            }
            bridge.aco$confirmLong(
                    message.amount(),
                    message.subtractStoredAmount(),
                    message.autoStart());
        }
    }

    /** Craft確認画面から戻った量画面へ、ItemStackに載らないlong値を同期する。 */
    private record LongCraftAmountStateMessage(int containerId, long amount)
            implements CustomPacketPayload {
        private static final Type<LongCraftAmountStateMessage> TYPE =
                BigCraftingNetwork.type("long_craft_amount_state");
        private static final StreamCodec<RegistryFriendlyByteBuf, LongCraftAmountStateMessage> STREAM_CODEC =
                StreamCodec.ofMember(
                        LongCraftAmountStateMessage::write,
                        LongCraftAmountStateMessage::decode);

        @Override
        public Type<LongCraftAmountStateMessage> type() {
            return TYPE;
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(containerId);
            buffer.writeLong(amount);
        }

        private static LongCraftAmountStateMessage decode(RegistryFriendlyByteBuf buffer) {
            return new LongCraftAmountStateMessage(
                    buffer.readVarInt(),
                    buffer.readLong());
        }

        private static void handle(LongCraftAmountStateMessage message, IPayloadContext context) {
            LongCraftAmountClientHandler.accept(message.containerId(), message.amount());
        }
    }

    /** AE2本来の確認Payloadへ、正確なBigInteger容量と行数量だけを補う。 */
    private record ExactCraftingPlanSummaryMessage(
            int containerId,
            boolean present,
            BigInteger usedBytes,
            Map<AEKey, BigCraftingPlanSummary.Entry> entries)
            implements CustomPacketPayload {
        private static final Type<ExactCraftingPlanSummaryMessage> TYPE =
                BigCraftingNetwork.type("exact_crafting_plan_summary");
        private static final StreamCodec<RegistryFriendlyByteBuf, ExactCraftingPlanSummaryMessage> STREAM_CODEC =
                StreamCodec.ofMember(
                        ExactCraftingPlanSummaryMessage::write,
                        ExactCraftingPlanSummaryMessage::decode);

        private ExactCraftingPlanSummaryMessage {
            if (containerId < 0) {
                throw new IllegalArgumentException("containerId must be non-negative");
            }
            if (!present) {
                usedBytes = BigInteger.ZERO;
                entries = Map.of();
            } else {
                java.util.Objects.requireNonNull(usedBytes, "usedBytes");
                entries = Map.copyOf(new LinkedHashMap<>(
                        java.util.Objects.requireNonNull(entries, "entries")));
            }
        }

        @Override
        public Type<ExactCraftingPlanSummaryMessage> type() {
            return TYPE;
        }

        private static ExactCraftingPlanSummaryMessage present(
                int containerId,
                BigInteger usedBytes,
                Map<AEKey, BigCraftingPlanSummary.Entry> entries) {
            return new ExactCraftingPlanSummaryMessage(
                    containerId,
                    true,
                    usedBytes,
                    entries);
        }

        private static ExactCraftingPlanSummaryMessage clear(int containerId) {
            return new ExactCraftingPlanSummaryMessage(
                    containerId,
                    false,
                    BigInteger.ZERO,
                    Map.of());
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            int maximumBits = ACOConfig.getBigIntegerMaximumBits();
            buffer.writeVarInt(containerId);
            buffer.writeBoolean(present);
            if (!present) {
                return;
            }

            BigIntegerBufferCodec.writeNonNegative(buffer, usedBytes, maximumBits);
            buffer.writeVarInt(entries.size());
            entries.forEach((key, entry) -> {
                AeKeyBigCraftingPacketCodec.INSTANCE.write(buffer, key);
                BigIntegerBufferCodec.writeNonNegative(buffer, entry.stored(), maximumBits);
                BigIntegerBufferCodec.writeNonNegative(buffer, entry.missing(), maximumBits);
                BigIntegerBufferCodec.writeNonNegative(buffer, entry.craft(), maximumBits);
            });
        }

        private static ExactCraftingPlanSummaryMessage decode(RegistryFriendlyByteBuf buffer) {
            int containerId = buffer.readVarInt();
            boolean present = buffer.readBoolean();
            if (!present) {
                return clear(containerId);
            }

            int maximumBits = ACOConfig.getBigIntegerMaximumBits();
            BigInteger usedBytes = BigIntegerBufferCodec.readNonNegative(buffer, maximumBits);
            int entryCount = buffer.readVarInt();
            int maximumEntries = ACOConfig.getBigIntegerStatusPageEntries();
            if (entryCount < 0 || entryCount > maximumEntries) {
                throw new IllegalArgumentException(
                        "invalid exact craft summary entry count " + entryCount);
            }

            Map<AEKey, BigCraftingPlanSummary.Entry> entries = new LinkedHashMap<>(entryCount);
            for (int index = 0; index < entryCount; index++) {
                AEKey key = AeKeyBigCraftingPacketCodec.INSTANCE.read(buffer);
                if (key == null) {
                    throw new IllegalArgumentException(
                            "unknown AEKey in exact craft summary");
                }
                BigCraftingPlanSummary.Entry entry = new BigCraftingPlanSummary.Entry(
                        BigIntegerBufferCodec.readNonNegative(buffer, maximumBits),
                        BigIntegerBufferCodec.readNonNegative(buffer, maximumBits),
                        BigIntegerBufferCodec.readNonNegative(buffer, maximumBits));
                if (entries.put(key, entry) != null) {
                    throw new IllegalArgumentException(
                            "duplicate AEKey in exact craft summary: " + key.getId());
                }
            }
            return present(containerId, usedBytes, entries);
        }

        private static void handle(
                ExactCraftingPlanSummaryMessage message,
                IPayloadContext context) {
            if (message.present()) {
                BigCraftingPlanClientStore.accept(
                        message.containerId(),
                        message.usedBytes(),
                        message.entries());
                return;
            }
            BigCraftingPlanClientStore.clear(message.containerId());
        }
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                AE2CraftingOptimizer.MODID,
                path));
    }

    private static BigCraftingStatusPageCodec<AEKey> codec(int bits, int entries) {
        return new BigCraftingStatusPageCodec<>(
                AeKeyBigCraftingPacketCodec.INSTANCE,
                bits,
                entries);
    }
}
