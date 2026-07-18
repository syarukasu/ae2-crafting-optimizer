package com.syaru.ae2craftingoptimizer.api.big;

import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.engine.BigCraftingKeyCodec;
import com.syaru.ae2craftingoptimizer.engine.OverflowPromotingCraftingPlanner;
import java.math.BigInteger;
import java.util.Objects;
import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.network.BigCraftingNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/** Stable opt-in boundary for CPU add-ons that own a BigInteger crafting host. */
public final class BigCraftingEngineApi {
    public static final int API_VERSION = 3;

    private BigCraftingEngineApi() {
    }

    public static boolean isEnabled() {
        return ACOConfig.enableBigIntegerCraftingBackend();
    }

    public static <K> BigCraftingRuntime<K> create(
            BigInteger capacity,
            BigCraftingKeyCodec<K> keyCodec) {
        if (!isEnabled()) {
            throw new IllegalStateException(
                    "ACO BigInteger crafting backend is disabled by server config");
        }
        return new BigCraftingRuntime<>(
                capacity,
                Objects.requireNonNull(keyCodec, "keyCodec"),
                ACOConfig.getBigIntegerMaximumBits(),
                ACOConfig.getBigIntegerExecutionWindow(),
                ACOConfig.getBigIntegerStatusPageEntries(),
                ACOConfig.getBigIntegerRuntimeCountBudgetBytes());
    }

    /** Creates a shared-capacity host for an explicitly integrated crafting CPU add-on. */
    public static <K> BigCraftingHostRuntime<K> createHost(
            BigInteger capacity,
            BigCraftingKeyCodec<K> keyCodec) {
        if (!isEnabled()) {
            throw new IllegalStateException(
                    "ACO BigInteger crafting backend is disabled by server config");
        }
        return new BigCraftingHostRuntime<>(
                capacity,
                Objects.requireNonNull(keyCodec, "keyCodec"),
                ACOConfig.getBigIntegerMaximumBits(),
                ACOConfig.getBigIntegerExecutionWindow(),
                ACOConfig.getBigIntegerStatusPageEntries(),
                ACOConfig.getBigIntegerRuntimeCountBudgetBytes());
    }

    /** Creates a planner whose intermediate arithmetic obeys the server bit limit. */
    public static <K> OverflowPromotingCraftingPlanner<K> createPlanner() {
        if (!isEnabled()) {
            throw new IllegalStateException(
                    "ACO BigInteger crafting backend is disabled by server config");
        }
        return new OverflowPromotingCraftingPlanner<>(ACOConfig.getBigIntegerMaximumBits());
    }

    /** Restores a host runtime using the current server-side safety limits. */
    public static <K> BigCraftingRuntime<K> load(
            CompoundTag saved,
            BigCraftingKeyCodec<K> keyCodec) {
        if (!isEnabled()) {
            throw new IllegalStateException(
                    "ACO BigInteger crafting backend is disabled by server config");
        }
        return BigCraftingRuntime.load(
                Objects.requireNonNull(saved, "saved"),
                Objects.requireNonNull(keyCodec, "keyCodec"),
                ACOConfig.getBigIntegerMaximumBits(),
                ACOConfig.getBigIntegerExecutionWindow(),
                ACOConfig.getBigIntegerStatusPageEntries(),
                ACOConfig.getBigIntegerRuntimeCountBudgetBytes());
    }

    /** Restores an add-on CPU host while treating its current structure capacity as authoritative. */
    public static <K> BigCraftingHostRuntime<K> loadHost(
            CompoundTag saved,
            BigInteger currentCapacity,
            BigCraftingKeyCodec<K> keyCodec) {
        if (!isEnabled()) {
            throw new IllegalStateException(
                    "ACO BigInteger crafting backend is disabled by server config");
        }
        return BigCraftingHostRuntime.load(
                Objects.requireNonNull(saved, "saved"),
                Objects.requireNonNull(currentCapacity, "currentCapacity"),
                Objects.requireNonNull(keyCodec, "keyCodec"),
                ACOConfig.getBigIntegerMaximumBits(),
                ACOConfig.getBigIntegerExecutionWindow(),
                ACOConfig.getBigIntegerStatusPageEntries(),
                ACOConfig.getBigIntegerRuntimeCountBudgetBytes());
    }

    public static void sendStatusPage(
            ServerPlayer player,
            BigCraftingRuntime<AEKey> runtime,
            int offset,
            int requestedPageSize) {
        if (!isEnabled()) {
            throw new IllegalStateException("ACO BigInteger crafting backend is disabled by server config");
        }
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(runtime, "runtime");
        int pageSize = Math.min(requestedPageSize, runtime.maximumStatusPageEntries());
        while (pageSize > 0) {
            var page = runtime.statusPage(offset, pageSize);
            if (BigCraftingNetwork.fitsPacket(page)) {
                BigCraftingNetwork.send(player, page);
                return;
            }
            if (page.jobs().size() <= 1) {
                throw new IllegalStateException(
                        "one BigInteger crafting status entry exceeds the 1 MiB packet safety bound");
            }
            pageSize = Math.max(1, page.jobs().size() / 2);
        }
        throw new IllegalArgumentException("status page size must be positive");
    }
}
