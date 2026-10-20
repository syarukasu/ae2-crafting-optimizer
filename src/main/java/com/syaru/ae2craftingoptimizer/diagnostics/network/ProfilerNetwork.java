package com.syaru.ae2craftingoptimizer.diagnostics.network;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.diagnostics.ProfilerBridge;
import com.syaru.ae2craftingoptimizer.diagnostics.ProfilerSnapshot;
import com.syaru.ae2craftingoptimizer.diagnostics.client.ProfilerScreen;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Issue #221: optional read-only channel, independent of BigInteger protocol 4. */
public final class ProfilerNetwork {
    public static final String PROTOCOL = "1";
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final ProfilerRequestLimiter<ServerPlayer> LIMITER = new ProfilerRequestLimiter<>();
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(AE2CraftingOptimizer.MODID, "profiler"),
            () -> PROTOCOL, ProfilerNetwork::acceptsVersion, ProfilerNetwork::acceptsVersion);

    private ProfilerNetwork() {
    }

    public static boolean acceptsVersion(String version) {
        return PROTOCOL.equals(version) || NetworkRegistry.ABSENT.equals(version)
                || NetworkRegistry.ACCEPTVANILLA.equals(version);
    }

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        CHANNEL.registerMessage(0, RefreshRequest.class, RefreshRequest::encode, RefreshRequest::decode,
                RefreshRequest::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, SnapshotMessage.class, SnapshotMessage::encode, SnapshotMessage::decode,
                SnapshotMessage::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static boolean isRemotePresent(Connection connection) {
        return CHANNEL.isRemotePresent(connection);
    }

    public static void requestRefresh() {
        CHANNEL.sendToServer(new RefreshRequest());
    }

    public static int open(CommandSourceStack source) throws CommandSyntaxException {
        if (!source.hasPermission(2)) {
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        if (!isRemotePresent(player.connection.connection)) {
            source.sendFailure(Component.literal("ACO profiler GUI unavailable on this client; use /aco profiler recent"));
            return 0;
        }
        if (!sendSnapshot(player, true)) {
            source.sendFailure(Component.literal("ACO profiler: wait one second before refreshing"));
            return 0;
        }
        return 1;
    }

    private static boolean sendSnapshot(ServerPlayer player, boolean open) {
        // Recheck every request, including a formerly authorized player's still-open screen.
        if (!LIMITER.tryAcquire(player, player.createCommandSourceStack().hasPermission(2), System.nanoTime())
                || !isRemotePresent(player.connection.connection)) {
            return false;
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SnapshotMessage(open, ProfilerBridge.snapshot().encodeJson()));
        return true;
    }

    private record RefreshRequest() {
        private static void encode(RefreshRequest message, FriendlyByteBuf buffer) {
        }

        private static RefreshRequest decode(FriendlyByteBuf buffer) {
            if (buffer.isReadable()) {
                throw new IllegalArgumentException("Unexpected profiler request payload");
            }
            return new RefreshRequest();
        }

        private static void handle(RefreshRequest message, Supplier<NetworkEvent.Context> supplier) {
            var context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player != null) {
                    sendSnapshot(player, false);
                }
            });
            context.setPacketHandled(true);
        }
    }

    private record SnapshotMessage(boolean open, byte[] json) {
        private static void encode(SnapshotMessage message, FriendlyByteBuf buffer) {
            if (message.json.length > ProfilerSnapshot.MAX_JSON_BYTES) {
                throw new IllegalArgumentException("Profiler packet exceeds cap");
            }
            buffer.writeBoolean(message.open);
            buffer.writeByteArray(message.json);
        }

        private static SnapshotMessage decode(FriendlyByteBuf buffer) {
            boolean open = buffer.readBoolean();
            byte[] json = buffer.readByteArray(ProfilerSnapshot.MAX_JSON_BYTES);
            if (buffer.isReadable()) {
                throw new IllegalArgumentException("Unexpected profiler response payload");
            }
            return new SnapshotMessage(open, json);
        }

        private static void handle(SnapshotMessage message, Supplier<NetworkEvent.Context> supplier) {
            var context = supplier.get();
            // Issue #140/#221: client classes resolve only inside the physical-client dispatch.
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ProfilerScreen.accept(message.open, message.json)));
            context.setPacketHandled(true);
        }
    }
}
