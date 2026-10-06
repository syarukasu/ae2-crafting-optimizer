package com.syaru.ae2craftingoptimizer.api.big;

import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.engine.BigCraftingKeyCodec;
import com.syaru.ae2craftingoptimizer.engine.BigCountMath;
import com.syaru.ae2craftingoptimizer.engine.OverflowPromotingCraftingPlanner;
import com.syaru.ae2craftingoptimizer.engine.Ae2CraftingPlanSidecars;
import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
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
    public static final int AMOUNT_LEDGER_API_VERSION = 2;
    /** AE2計算プロファイル照会APIの契約番号。 */
    public static final int CALCULATION_PROFILE_API_VERSION = 1;
    /** アドオンがACOの正確なBigInteger上限を照会するAPIの契約番号。 */
    public static final int CAPACITY_LIMIT_API_VERSION = 1;
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
     * 現在のACO設定で計画・会計・NBTへ保存できる正確な最大値を返す。
     *
     * <p>アドオンはこの値をCPU容量の上限や表示に利用できる。BigIntegerは不変なので、
     * 呼び出し側へ同じ値を返してもACO内部状態は変更されない。</p>
     */
    public static BigInteger maximumSupportedAmount() {
        return maximumSupportedAmount(ACOConfig.getBigIntegerMaximumBits());
    }

    static BigInteger maximumSupportedAmount(int configuredMaximumBits) {
        // ACOの設定境界外の値を、公開API経由で有効な上限として扱わせない。
        if (configuredMaximumBits < 1
                || configuredMaximumBits > BigCountMath.HARD_MAXIMUM_BITS) {
            throw new IllegalArgumentException(
                    "configuredMaximumBits must be between 1 and "
                            + BigCountMath.HARD_MAXIMUM_BITS);
        }

        BigInteger binaryLimit = BigInteger.ONE
                .shiftLeft(configuredMaximumBits)
                .subtract(BigInteger.ONE);
        // bit上限の端が16,385桁へ届く場合は、ACOの10進16,384桁上限を正本にする。
        return binaryLimit.min(BigCountMath.hardMaximumValue());
    }

    /**
     * ACOが厳密なBigInteger計算境界を所有しているかを返す。
     * 対応CPUアドオンが同じ計算を二重実行しないための能力照会だけを行う。
     */
    public static boolean isCalculationProfileActive() {
        // #218: the retired compiled-graph switch does not disable the bundled calculator.
        return ACOConfig.enableBigIntegerCraftingBackend();
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
        return Ae2CraftingPlanSidecars.metadata(plan).flatMap(metadata -> metadata.exactView());
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

    /**
     * AE2のitem、fluid、chemicalキーを扱うアドオン向け台帳を作成する。
     *
     * <p>アドオンがACO内部の{@code BigCraftingKeyCodec}型を解決しなくて済むよう、
     * 公開APIだけで完結する固定型ファクトリとして提供する。</p>
     */
    public static BigIntegerAmountLedger<AEKey> createAeKeyAmountLedger() {
        return createAmountLedger(AeKeyBigCraftingCodec.INSTANCE);
    }

    /** Restores an add-on amount ledger using the current server-side bit limit. */
    public static <K> BigIntegerAmountLedger<K> loadAmountLedger(
            CompoundTag saved,
            BigCraftingKeyCodec<K> keyCodec) {
        BigIntegerAmountLedger<K> ledger = createAmountLedger(keyCodec);
        ledger.load(Objects.requireNonNull(saved, "saved"));
        return ledger;
    }

    /** AEKey用の保存済み台帳を、ACO内部型を公開せず復元する。 */
    public static BigIntegerAmountLedger<AEKey> loadAeKeyAmountLedger(CompoundTag saved) {
        BigIntegerAmountLedger<AEKey> ledger = createAeKeyAmountLedger();
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
