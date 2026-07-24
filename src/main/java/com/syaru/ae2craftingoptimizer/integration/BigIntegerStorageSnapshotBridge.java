package com.syaru.ae2craftingoptimizer.integration;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.engine.BigKeyCounterSidecars;
import com.syaru.ae2craftingoptimizer.mixin.ExtendedAePlusBigIntegerCellInventoryAccessor;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * NetworkStorageが各mountを集計する境界で、AE2用long FacadeとBigInteger正本を分離する。
 */
public final class BigIntegerStorageSnapshotBridge {
    private static final String EXTENDED_AE_PLUS_BIG_CELL =
            "com.extendedae_plus.api.storage.InfinityBigIntegerCellInventory";
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private static final AtomicBoolean LOGGED_ADAPTER_FAILURE = new AtomicBoolean();
    private static final ThreadLocal<ArrayDeque<KeyCounter>> TEMPORARY_COUNTERS =
            ThreadLocal.withInitial(ArrayDeque::new);

    private BigIntegerStorageSnapshotBridge() {
    }

    /** 一つのmounted storageを一時Counterへ取得し、安全にネットワーク全体へ統合する。 */
    public static void collect(MEStorage storage, KeyCounter target) {
        collect(storage, target, ACOConfig.enableExactBigIntegerInventorySnapshots());
    }

    /** Forge Configを起動しない単体試験から、有効状態だけを明示する内部入口。 */
    static void collect(
            MEStorage storage,
            KeyCounter target,
            boolean exactSnapshotsEnabled) {
        // 機能OFF時はAE2本来の呼出しを一切変えない。
        if (!exactSnapshotsEnabled) {
            storage.getAvailableStacks(target);
            return;
        }

        ArrayDeque<KeyCounter> pool = TEMPORARY_COUNTERS.get();
        KeyCounter facadeContribution = pool.pollFirst();
        // 同じスレッドの再帰呼出しで空なら一基だけ増やし、通常tickでは既存Counterを再利用する。
        if (facadeContribution == null) {
            facadeContribution = new KeyCounter();
        }
        try {
            storage.getAvailableStacks(facadeContribution);
            BigKeyCounterSidecars.Snapshot exactContribution =
                    captureExactContribution(storage, facadeContribution);

            BigKeyCounterSidecars.merge(target, exactContribution);
            mergeSaturatedFacade(target, exactContribution, facadeContribution);
        } finally {
            // 次のmountへ前回のlong値とBigInteger Sidecarを持ち越さない。
            facadeContribution.clear();
            BigKeyCounterSidecars.clear(facadeContribution);
            pool.addFirst(facadeContribution);
        }
    }

    private static BigKeyCounterSidecars.Snapshot captureExactContribution(
            MEStorage storage,
            KeyCounter facadeContribution) {
        BigKeyCounterSidecars.Snapshot nested =
                BigKeyCounterSidecars.snapshot(facadeContribution).orElse(null);
        // NetworkStorageが入れ子なら、内側で既に集計した正確なSidecarをそのまま使用する。
        if (nested != null) {
            return nested;
        }

        // Optional Mixinが適用されたInfinityBigIntegerCellだけ、内部BigInteger Mapを読む。
        if (storage instanceof ExtendedAePlusBigIntegerCellInventoryAccessor accessor) {
            try {
                Map<AEKey, BigInteger> copy = new LinkedHashMap<>();
                // セル内部Mapは可変なので、同一tick中の変更から計画Snapshotを隔離する。
                for (Map.Entry<AEKey, BigInteger> entry :
                        accessor.aco$getExactStoredAmounts().entrySet()) {
                    copy.put(entry.getKey(), entry.getValue());
                }
                return new BigKeyCounterSidecars.Snapshot(copy, true);
            } catch (RuntimeException | LinkageError failure) {
                logAdapterFailure(failure);
                return new BigKeyCounterSidecars.Snapshot(
                        BigKeyCounterSidecars.fromFacade(facadeContribution).amounts(),
                        false);
            }
        }

        // 対象クラスなのにAccessorが無い場合は、丸め値を正確値として採用しない。
        if (storage.getClass().getName().equals(EXTENDED_AE_PLUS_BIG_CELL)) {
            logAdapterFailure(new IllegalStateException(
                    "ExtendedAE Plus BigInteger cell accessor was not applied"));
            return new BigKeyCounterSidecars.Snapshot(
                    BigKeyCounterSidecars.fromFacade(facadeContribution).amounts(),
                    false);
        }
        return BigKeyCounterSidecars.fromFacade(facadeContribution);
    }

    private static void mergeSaturatedFacade(
            KeyCounter target,
            BigKeyCounterSidecars.Snapshot exactContribution,
            KeyCounter facadeContribution) {
        Map<AEKey, BigInteger> source = exactContribution.amounts();
        // 不完全なAdapter結果でも、負数化防止用に取得できた非負long値は安全に統合する。
        if (!exactContribution.complete()) {
            source = BigKeyCounterSidecars.fromFacade(facadeContribution).amounts();
            // 既に負数へwrapしたavailable stackは0扱いにせず、表示だけ最大値へ退避する。
            for (var entry : facadeContribution) {
                // 負数だけをoverflow済みと判定し、通常の正数は下の飽和加算へ任せる。
                if (entry.getLongValue() < 0L) {
                    target.set(entry.getKey(), Long.MAX_VALUE);
                }
            }
        }

        // AE2へ見せる各キーはLong.MAX_VALUEで飽和させ、加算overflowを発生させない。
        for (Map.Entry<AEKey, BigInteger> entry : source.entrySet()) {
            AEKey key = entry.getKey();
            BigInteger incoming = entry.getValue();
            long current = target.get(key);
            // 既存値が負数、または一基分だけでlong超過なら即座に最大値へ飽和する。
            if (current < 0L || incoming.compareTo(LONG_MAX) > 0) {
                target.set(key, Long.MAX_VALUE);
                continue;
            }
            long incomingLong = incoming.longValueExact();
            // current + incomingがlong境界を越える場合は、加算せず最大値へ飽和させる。
            if (current > Long.MAX_VALUE - incomingLong) {
                target.set(key, Long.MAX_VALUE);
            } else if (incomingLong > 0L) {
                target.set(key, current + incomingLong);
            }
        }
    }

    private static void logAdapterFailure(Throwable failure) {
        // 同じ互換失敗をtickごとに出さず、最初の一件だけ明確な原因として記録する。
        if (LOGGED_ADAPTER_FAILURE.compareAndSet(false, true)) {
            AE2CraftingOptimizer.LOGGER.error(
                    "ACO could not capture exact ExtendedAE Plus BigInteger cell inventory. "
                            + "BigInteger planning will fall back instead of using clamped stock.",
                    failure);
        }
    }
}
