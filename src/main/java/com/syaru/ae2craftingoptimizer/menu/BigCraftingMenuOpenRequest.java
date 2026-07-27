package com.syaru.ae2craftingoptimizer.menu;

import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.api.big.BigCraftingHostRuntime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * Advanced AEのsubmitJob呼出しからCraftConfirmMenuのRETURNまでだけ生存する、
 * BigInteger状態画面のServer Threadローカル引継ぎ。
 */
public final class BigCraftingMenuOpenRequest {
    private static final ThreadLocal<Request> CURRENT = new ThreadLocal<>();

    private BigCraftingMenuOpenRequest() {
    }

    public static void record(
            ServerPlayer player,
            BigCraftingHostRuntime<AEKey> host,
            UUID jobId) {
        CURRENT.set(new Request(
                Objects.requireNonNull(player, "player").getUUID(),
                Objects.requireNonNull(host, "host"),
                Objects.requireNonNull(jobId, "jobId")));
    }

    public static Optional<Request> consume(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        Request request = CURRENT.get();
        CURRENT.remove();
        // 別Playerの同一Server Thread処理へ古い要求を渡さない。
        if (request == null
                || !request.playerId().equals(player.getUUID())) {
            return Optional.empty();
        }
        return Optional.of(request);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public record Request(
            UUID playerId,
            BigCraftingHostRuntime<AEKey> host,
            UUID jobId) {
        public Request {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(host, "host");
            Objects.requireNonNull(jobId, "jobId");
        }
    }
}
