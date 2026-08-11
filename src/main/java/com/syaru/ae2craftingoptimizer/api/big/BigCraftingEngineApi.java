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
    /** Existing AQE host API contract. Keep this stable when adding optional API surfaces. */
    public static final int API_VERSION = 3;
    /** Version of the exact amount-ledger surface added for crafting add-ons. */
    public static final int AMOUNT_LEDGER_API_VERSION = 1;
    /** Version of the optional AE2 calculation-profile query used by CPU add-ons. */
    public static final int CALCULATION_PROFILE_API_VERSION = 1;

    private BigCraftingEngineApi() {
    }

    public static boolean isEnabled() {
        return ACOConfig.enableBigIntegerCraftingBackend();
    }

    /**
     * Returns whether ACO currently owns the strict AE2 calculation boundary.
     *
     * <p>An add-on may use this query to avoid applying its own calculation
     * shortcut over ACO's planner. This is deliberately a capability query;
     * it does not expose or mutate a crafting plan.</p>
     */
    public static boolean isCalculationProfileActive() {
        return ACOConfig.enableBigCraftingProfile()
                && ACOConfig.enableCompiledCraftingGraph()
                && ACOConfig.enableBigIntegerCraftingBackend();
    }

    /**
     * Returns the exact plan sidecar for an ACO-created plan.
     *
     * <p>The returned view is empty for ordinary AE2 plans. This identity check
     * prevents an add-on from treating a saturated or unrelated plan as exact.</p>
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

    /**
     * Creates an exact amount ledger for add-on CPUs that must keep output counts
     * beyond the long range without changing AE2's own stack API.
     */
    public static <K> BigIntegerAmountLedger<K> createAmountLedger(
            BigCraftingKeyCodec<K> keyCodec) {
        if (!isEnabled()) {
            throw new IllegalStateException(
                    "ACO BigInteger crafting backend is disabled by server config");
        }
        return new BigIntegerAmountLedger<>(
                Objects.requireNonNull(keyCodec, "keyCodec"),
                ACOConfig.getBigIntegerMaximumBits());
    }

    /** Restores an add-on amount ledger using the current server-side bit limit. */
    public static <K> BigIntegerAmountLedger<K> loadAmountLedger(
            CompoundTag saved,
            BigCraftingKeyCodec<K> keyCodec) {
        BigIntegerAmountLedger<K> ledger = createAmountLedger(keyCodec);
        ledger.load(Objects.requireNonNull(saved, "saved"));
        return ledger;
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
