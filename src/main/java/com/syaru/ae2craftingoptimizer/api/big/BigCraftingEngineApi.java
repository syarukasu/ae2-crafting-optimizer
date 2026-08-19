package com.syaru.ae2craftingoptimizer.api.big;

import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.engine.BigCraftingKeyCodec;
import com.syaru.ae2craftingoptimizer.engine.OverflowPromotingCraftingPlanner;
import com.syaru.ae2craftingoptimizer.engine.Ae2CraftingPlanSidecars;
import com.syaru.ae2craftingoptimizer.engine.BigCapacityCraftingPlan;
import com.syaru.ae2craftingoptimizer.engine.BigIntegerCraftingPlan;
import com.syaru.ae2craftingoptimizer.engine.BigIntegerSimulationPlan;
import com.syaru.ae2craftingoptimizer.engine.WideCraftingPlan;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
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
    /** 外部CPUが正確なBigInteger計画の提出境界を所有するAPIの契約番号。 */
    public static final int EXTERNAL_CONSUMER_API_VERSION = 1;
    private static final AtomicInteger EXTERNAL_CONSUMER_COUNT = new AtomicInteger();

    private BigCraftingEngineApi() {
    }

    public static boolean isEnabled() {
        return ACOConfig.enableBigIntegerCraftingBackend();
    }

    /**
     * 外部CPUアドオンがACO BigInteger計画の実行を所有することを登録する。
     * ACOは登録先のCPUを識別・tick・実行せず、提出境界の能力だけを公開する。
     * 登録してもAE2標準CPUの提出経路は変更せず、検証と提出は外部CPUが所有する。
     */
    public static void registerExternalBigIntegerPlanConsumer() {
        EXTERNAL_CONSUMER_COUNT.incrementAndGet();
    }

    /** 外部CPUがBigInteger計画を引き受ける登録を済ませたか返す。 */
    public static boolean hasExternalBigIntegerPlanConsumer() {
        return EXTERNAL_CONSUMER_COUNT.get() > 0;
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
     * ACOが生成したWide計画の正確なBigInteger側データを取得する。
     * 素材不足simulationと、個別量がlong内でも合計CPU容量だけがlongを超える計画を含む。
     * simulationがtrueのViewは表示・診断専用で、アドオンは実行へ提出してはいけない。
     * 通常のAE2計画やACOと無関係な飽和計画は返さない。
     */
    public static Optional<BigIntegerCraftingPlanView> inspectBigIntegerPlan(
            ICraftingPlan plan) {
        // BigInteger計算が無効な環境やnull計画へ、Sidecarの有無を推測して返さない。
        if (!isCalculationProfileActive() || plan == null) {
            return Optional.empty();
        }
        return inspectAttachedExactPlan(plan);
    }

    /** 設定判定を除いたSidecar変換本体。単体試験でも実経路と同じ変換を使う。 */
    static Optional<BigIntegerCraftingPlanView> inspectAttachedExactPlan(
            ICraftingPlan plan) {
        WideCraftingPlan metadata = Ae2CraftingPlanSidecars.metadata(plan).orElse(null);
        // ACOが作成していない通常AE2計画には、正確なWide値を捏造しない。
        if (metadata == null) {
            return Optional.empty();
        }

        // 個別数量までlongを超えた計画は、従来どおりBigInteger正本をそのまま公開する。
        if (metadata instanceof BigIntegerCraftingPlan bigPlan) {
            return Optional.of(viewOf(bigPlan));
        }

        // 素材不足のwide計画も、実行不可フラグと正確な不足量を同じ公開Viewへ渡す。
        if (metadata instanceof BigIntegerSimulationPlan simulationPlan) {
            return Optional.of(viewOf(simulationPlan));
        }

        // 合計bytesだけがlongを超えた計画も、同じ公開Viewで正確に取得できるようにする。
        if (metadata instanceof BigCapacityCraftingPlan capacityPlan) {
            return Optional.of(viewOf(capacityPlan));
        }
        return Optional.empty();
    }

    private static BigIntegerCraftingPlanView viewOf(BigIntegerCraftingPlan bigPlan) {
        return new BigIntegerCraftingPlanView(
                bigPlan.finalOutput(),
                bigPlan.exactBytes(),
                bigPlan.simulation(),
                bigPlan.exactPatternTimes(),
                bigPlan.exactPlan().usedInventory(),
                bigPlan.exactPlan().emitted(),
                bigPlan.exactPlan().missing());
    }

    private static BigIntegerCraftingPlanView viewOf(
            BigIntegerSimulationPlan simulationPlan) {
        return new BigIntegerCraftingPlanView(
                simulationPlan.finalOutput(),
                simulationPlan.exactBytes(),
                simulationPlan.simulation(),
                simulationPlan.exactPatternTimes(),
                simulationPlan.exactPlan().usedInventory(),
                simulationPlan.exactPlan().emitted(),
                simulationPlan.exactPlan().missing());
    }

    private static BigIntegerCraftingPlanView viewOf(BigCapacityCraftingPlan capacityPlan) {
        return new BigIntegerCraftingPlanView(
                capacityPlan.finalOutput(),
                capacityPlan.exactBytes(),
                capacityPlan.simulation(),
                widenPatternTimes(capacityPlan.patternTimes()),
                widenCounter(capacityPlan.usedItems()),
                widenCounter(capacityPlan.emittedItems()),
                widenCounter(capacityPlan.missingItems()));
    }

    private static Map<IPatternDetails, BigInteger> widenPatternTimes(
            Map<IPatternDetails, Long> patternTimes) {
        Map<IPatternDetails, BigInteger> exact = new LinkedHashMap<>();
        // BigCapacity計画では各Pattern回数がlong内なので、符号も含めて損失なく拡張する。
        for (Map.Entry<IPatternDetails, Long> entry : patternTimes.entrySet()) {
            exact.put(entry.getKey(), BigInteger.valueOf(entry.getValue()));
        }
        return exact;
    }

    private static Map<AEKey, BigInteger> widenCounter(KeyCounter counter) {
        Map<AEKey, BigInteger> exact = new LinkedHashMap<>();
        // BigCapacity計画のAEKey量を、longへ戻さず公開BigInteger Viewへ移す。
        for (Object2LongMap.Entry<AEKey> entry : counter) {
            exact.put(entry.getKey(), BigInteger.valueOf(entry.getLongValue()));
        }
        return exact;
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
