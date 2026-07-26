package com.syaru.ae2craftingoptimizer.api.vector;

import appeng.api.networking.IGrid;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/** ACOをAACなどの設備MODへ依存させない、Grid単位の弱参照Executor登録簿。 */
public final class ExactVectorExecutorRegistry {
    private static final WeakHashMap<
                    IGrid,
                    WeakHashMap<
                            Object,
                            WeakReference<ExactVectorExecutor>>>
            EXECUTORS = new WeakHashMap<>();

    private ExactVectorExecutorRegistry() {
    }

    public static synchronized void register(
            IGrid grid,
            Object owner,
            ExactVectorExecutor executor) {
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(executor, "executor");
        WeakHashMap<Object, WeakReference<ExactVectorExecutor>> byOwner =
                EXECUTORS.computeIfAbsent(grid, ignored -> new WeakHashMap<>());
        WeakReference<ExactVectorExecutor> previousReference =
                byOwner.put(owner, new WeakReference<>(executor));
        ExactVectorExecutor previous = previousReference == null
                ? null
                : previousReference.get();
        // 同じ設備実体を別Executorへ差し替える場合だけ、明示解除漏れとして拒否する。
        if (previous != null && previous != executor) {
            byOwner.put(owner, previousReference);
            throw new IllegalStateException(
                    "vector executor owner is already registered");
        }
    }

    public static synchronized void unregister(IGrid grid, Object owner) {
        WeakHashMap<Object, WeakReference<ExactVectorExecutor>> byOwner =
                EXECUTORS.get(Objects.requireNonNull(grid, "grid"));
        // GridまたはOwnerが既にGC・解除済みなら冪等に終了する。
        if (byOwner == null) {
            return;
        }
        byOwner.remove(Objects.requireNonNull(owner, "owner"));
        if (byOwner.isEmpty()) {
            EXECUTORS.remove(grid);
        }
    }

    public static synchronized List<ExactVectorExecutor> find(IGrid grid) {
        List<ExactVectorExecutor> registered = findRegistered(grid);
        // 新規Transactionを受理できるExecutorだけを開始候補へ残す。
        if (registered.isEmpty()) {
            return List.of();
        }
        List<ExactVectorExecutor> result =
                new ArrayList<>(registered.size());
        // availability判定に失敗した設備は開始候補から外し、サーバーtickへ例外を漏らさない。
        for (ExactVectorExecutor executor : registered) {
            try {
                if (executor.isAvailable()) {
                    result.add(executor);
                }
            } catch (RuntimeException | LinkageError failure) {
                AE2CraftingOptimizer.LOGGER.error(
                        "Exact Vector executor availability check failed; "
                                + "the executor will not receive new work: {}",
                        executor.getClass().getName(),
                        failure);
            }
        }
        return List.copyOf(result);
    }

    /**
     * 稼働枠の空きに関係なく、Gridへ接続中のExecutorを返す。
     *
     * <p>Receipt照合とキャンセルは、新規受理可否ではなく設備の同一性を使う。</p>
     */
    public static synchronized List<ExactVectorExecutor> findRegistered(
            IGrid grid) {
        WeakHashMap<Object, WeakReference<ExactVectorExecutor>> byOwner =
                EXECUTORS.get(Objects.requireNonNull(grid, "grid"));
        // 登録設備がないGridでは不変の空Listを返す。
        if (byOwner == null || byOwner.isEmpty()) {
            return List.of();
        }
        List<ExactVectorExecutor> result = new ArrayList<>(byOwner.size());
        Iterator<
                        Map.Entry<
                                Object,
                                WeakReference<ExactVectorExecutor>>>
                entries = byOwner.entrySet().iterator();
        // ownerまたはExecutorがGC済みのentryを掃除し、公開Listへ混ぜない。
        while (entries.hasNext()) {
            ExactVectorExecutor executor =
                    entries.next().getValue().get();
            if (executor != null) {
                result.add(executor);
            } else {
                entries.remove();
            }
        }
        if (byOwner.isEmpty()) {
            EXECUTORS.remove(grid);
        }
        return List.copyOf(result);
    }

    /**
     * 同じGridの全Executorが永続所有する未完了Transaction数を返す。
     */
    public static int activeTransactionCount(IGrid grid) {
        int total = 0;
        // Executor実装の呼出し中はRegistry lockを保持しない。
        for (ExactVectorExecutor executor : findRegistered(grid)) {
            int count;
            try {
                count = executor.activeTransactionCount();
            } catch (RuntimeException | LinkageError failure) {
                AE2CraftingOptimizer.LOGGER.error(
                        "Exact Vector executor active transaction count "
                                + "failed; treating the grid as full: {}",
                        executor.getClass().getName(),
                        failure);
                return Integer.MAX_VALUE;
            }
            // 不正な負数は上限回避に使わせず、Grid全体を満杯扱いにする。
            if (count < 0) {
                AE2CraftingOptimizer.LOGGER.error(
                        "Exact Vector executor returned a negative active "
                                + "transaction count; treating the grid as "
                                + "full: {}",
                        executor.getClass().getName());
                return Integer.MAX_VALUE;
            }
            // 異常な実装でもintをwrapさせず、上限判定で必ず停止できる値へ飽和する。
            if (Integer.MAX_VALUE - total < count) {
                return Integer.MAX_VALUE;
            }
            total += count;
        }
        return total;
    }

    public static synchronized void clear() {
        EXECUTORS.clear();
    }
}
