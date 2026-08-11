package com.syaru.ae2craftingoptimizer.api.big;

import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.engine.BigCraftingKeyCodec;
import com.syaru.ae2craftingoptimizer.engine.OverflowPromotingCraftingPlanner;
import com.syaru.ae2craftingoptimizer.engine.Ae2CraftingPlanSidecars;
import com.syaru.ae2craftingoptimizer.engine.BigIntegerCraftingPlan;
import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.network.BigCraftingNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/** Stable opt-in boundary for CPU add-ons that own a BigInteger crafting host. */
public final class BigCraftingEngineApi {
    /** 既存AQEホストAPIの契約番号。既存アドオンの互換性を維持する。 */
    public static final int API_VERSION = 3;
    /** アドオン向け正確なBigInteger会計APIの契約番号。 */
    public static final int AMOUNT_LEDGER_API_VERSION = 1;
    /** AE2計算プロファイル照会APIの契約番号。 */
    public static final int CALCULATION_PROFILE_API_VERSION = 1;

    private BigCraftingEngineApi() {
    }

    public static boolean isEnabled() {
        return ACOConfig.enableBigIntegerCraftingBackend();
    }

    /**
     * ACOが厳密なBigInteger計算境界を所有しているかを返す。
     * 対応CPUアドオンが同じ計算を二重実行しないための能力照会だけを行う。
     */
    public static boolean isCalculationProfileActive() {
        return ACOConfig.enableBigCraftingProfile()
                && ACOConfig.enableCompiledCraftingGraph()
                && ACOConfig.enableBigIntegerCraftingBackend();
    }

    /**
     * ACOが生成した計画の正確なBigInteger側データを取得する。
     * 通常のAE2計画やlongへ飽和しただけの計画は返さない。
     */
    public static Optional<BigIntegerCraftingPlanView> inspectBigIntegerPlan(
            ICraftingPlan plan) {
        if (!isCalculationProfileActive() || plan == null) {
            return Optional.empty();
        }
        BigIntegerCraftingPlan bigPlan = Ae2CraftingPlanSidecars.bigInteger(plan).orElse(null);
        if (bigPlan == null) {
            return Optional.empty();
        }
        return Optional.of(new BigIntegerCraftingPlanView(
                bigPlan.finalOutput(),
                bigPlan.exactBytes(),
                bigPlan.simulation(),
                bigPlan.exactPatternTimes(),
                bigPlan.exactPlan().usedInventory(),
                bigPlan.exactPlan().emitted(),
                bigPlan.exactPlan().missing()));
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
            if (BigCraftingNetwork.fitsPacket(player, page)) {
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
