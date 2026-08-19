package com.syaru.ae2craftingoptimizer.integration;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.access.DelegatingMEInventoryAccess;
import com.syaru.ae2craftingoptimizer.access.ExtendedAePlusBigIntegerCellInventoryAccess;
import com.syaru.ae2craftingoptimizer.api.contract.ExactCountLimits;
import com.syaru.ae2craftingoptimizer.api.contract.ExactStorageAmountProvider;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.engine.BigKeyCounterSidecars;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * NetworkStorageが各mountを集計する境界で、AE2用long FacadeとBigInteger正本を分離する。
 */
public final class BigIntegerStorageSnapshotBridge {
    private static final String EXTENDED_AE_PLUS_BIG_CELL =
            "com.extendedae_plus.api.storage.InfinityBigIntegerCellInventory";
    /** 通常のDriveWatcherは一段だが、アドオンの委譲層を含めても無限循環しない上限。 */
    private static final int MAXIMUM_DELEGATE_DEPTH = 16;
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private static final ExactCountLimits EXACT_COUNT_LIMITS = ExactCountLimits.defaults();
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

        MEStorage exactStorage = unwrapDelegates(storage);
        // 公開契約を実装したStorageは、Mod固有Mixinより先に正確な在庫正本として扱う。
        if (exactStorage instanceof ExactStorageAmountProvider provider) {
            try {
                return captureValidatedProviderAmounts(
                        provider.exactStoredAmounts(),
                        facadeContribution);
            } catch (RuntimeException | LinkageError failure) {
                logAdapterFailure(failure);
                return incompleteFacadeSnapshot(facadeContribution);
            }
        }
        // DriveWatcher等の内側にあるInfinityBigIntegerCellから、正確な内部Mapを読む。
        if (exactStorage
                instanceof ExtendedAePlusBigIntegerCellInventoryAccess accessor) {
            try {
                return captureValidatedProviderAmounts(
                        accessor.aco$getExactStoredAmounts(),
                        facadeContribution);
            } catch (RuntimeException | LinkageError failure) {
                logAdapterFailure(failure);
                return incompleteFacadeSnapshot(facadeContribution);
            }
        }

        // 対象クラスなのにAccessorが無い場合は、丸め値を正確値として採用しない。
        if (isExtendedAePlusBigCell(exactStorage)) {
            logAdapterFailure(new IllegalStateException(
                    "ExtendedAE Plus BigInteger cell accessor was not applied"));
            return incompleteFacadeSnapshot(facadeContribution);
        }
        return BigKeyCounterSidecars.fromFacade(facadeContribution);
    }

    /** 公開Providerの可変Mapを有限契約で検査し、このmountが公開するキーだけ複製する。 */
    static BigKeyCounterSidecars.Snapshot captureValidatedProviderAmounts(
            Map<AEKey, BigInteger> source,
            KeyCounter facadeContribution) {
        Objects.requireNonNull(source, "exact stored amounts");
        Objects.requireNonNull(facadeContribution, "facade contribution");
        EXACT_COUNT_LIMITS.validateKeyCount(source.size());

        Map<AEKey, BigInteger> validated = new LinkedHashMap<>();
        // Providerが返した全値を先に検査し、隠しキーに不正値があっても完全Snapshotにしない。
        for (Map.Entry<AEKey, BigInteger> entry : source.entrySet()) {
            AEKey key = Objects.requireNonNull(entry.getKey(), "exact storage key");
            BigInteger amount = Objects.requireNonNull(entry.getValue(), "exact storage amount");
            EXACT_COUNT_LIMITS.validateNonNegative(amount);
            // partition等でFacadeから隠れたキーは、正確在庫側だけへ復活させない。
            if (facadeContribution.get(key) != 0L && amount.signum() > 0) {
                validated.put(key, amount);
            }
        }

        // Facadeが公開する全キーをProviderが覆う場合だけ、完全な正確Snapshotと認定する。
        for (var entry : facadeContribution) {
            if (entry.getLongValue() == 0L) {
                continue;
            }
            BigInteger exactAmount = source.get(entry.getKey());
            if (exactAmount == null || exactAmount.signum() <= 0) {
                throw new IllegalArgumentException(
                        "exact storage provider omitted an exposed positive key");
            }
        }
        return new BigKeyCounterSidecars.Snapshot(validated, true);
    }

    private static BigKeyCounterSidecars.Snapshot incompleteFacadeSnapshot(KeyCounter facadeContribution) {
        return new BigKeyCounterSidecars.Snapshot(
                BigKeyCounterSidecars.fromFacade(facadeContribution).amounts(),
                false);
    }

    private static MEStorage unwrapDelegates(MEStorage storage) {
        MEStorage current = storage;
        // AE2やアドオンの委譲Storageだけを上限付きで辿り、任意Reflectionは使用しない。
        for (int depth = 0; depth < MAXIMUM_DELEGATE_DEPTH; depth++) {
            // BigIntegerセル本体へ到達したら、それ以上委譲先を探さない。
            if (current instanceof ExactStorageAmountProvider
                    || current instanceof ExtendedAePlusBigIntegerCellInventoryAccess) {
                return current;
            }
            // AE2標準DelegatingMEInventory以外は、安全に内部Storageを取得できないため停止する。
            if (!(current instanceof DelegatingMEInventoryAccess delegating)) {
                return current;
            }
            MEStorage next = delegating.aco$getDelegateStorage();
            // nullや自己参照は破損委譲として停止し、Facade経路へFallbackする。
            if (next == null || next == current) {
                return current;
            }
            current = next;
        }
        return current;
    }

    private static boolean isExtendedAePlusBigCell(MEStorage storage) {
        Class<?> type = storage.getClass();
        // Accessor適用失敗時も、サブクラスを含む実セル型だけを明確な連携失敗として扱う。
        while (type != null) {
            if (type.getName().equals(EXTENDED_AE_PLUS_BIG_CELL)) {
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
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
                    "ACO could not capture an exact BigInteger storage snapshot. "
                            + "BigInteger planning will fall back instead of using clamped stock.",
                    failure);
        }
    }
}
