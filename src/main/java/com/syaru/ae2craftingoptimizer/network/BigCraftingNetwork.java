package com.syaru.ae2craftingoptimizer.network;

import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.api.big.AeKeyBigCraftingPacketCodec;
import com.syaru.ae2craftingoptimizer.api.big.BigCraftingStatusInbox;
import com.syaru.ae2craftingoptimizer.api.big.BigCraftingStatusPage;
import com.syaru.ae2craftingoptimizer.api.big.BigCraftingStatusPageCodec;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import io.netty.buffer.Unpooled;

/**
 * BigInteger対応Host専用のProtocol Version付き通信Channel。
 * 通常AE2の通信には介入せず、容量・進捗を上限付きPageへ分割して対応クライアントだけへ送る。
 */
public final class BigCraftingNetwork {
    public static final String PROTOCOL = "2";
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(AE2CraftingOptimizer.MODID, "big_crafting"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private BigCraftingNetwork() {
    }

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        CHANNEL.registerMessage(
                0,
                StatusPageMessage.class,
                StatusPageMessage::encode,
                StatusPageMessage::decode,
                StatusPageMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void send(ServerPlayer player, BigCraftingStatusPage<AEKey> page) {
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

    private static BigCraftingStatusPageCodec<AEKey> codec(int bits, int entries) {
        return new BigCraftingStatusPageCodec<>(AeKeyBigCraftingPacketCodec.INSTANCE, bits, entries);
    }
}
