package com.syaru.ae2craftingoptimizer.integration;

import appeng.api.stacks.KeyCounter;
import appeng.me.storage.NetworkStorage;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.engine.BigKeyCounterSidecars;
import com.syaru.ae2craftingoptimizer.optimization.OptimizationMetrics;
import com.syaru.ae2craftingoptimizer.optimization.ServerTickClock;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 同一server tick内で完成済みのNetworkStorage在庫集計を再利用する。
 *
 * <p>任意アドオンのStorageが世代通知を持たない場合でも古い在庫を長時間保持しないよう、
 * 再利用範囲は一tickに限定する。さらに全NetworkStorageの実変更とAE2のcache invalidationを
 * 共通世代へ反映し、同じtick中の変更後に古いSnapshotを返さない。</p>
 */
public final class ExactNetworkStorageSnapshotCache {
    private static final AtomicLong STORAGE_GENERATION = new AtomicLong();
    private static final Map<Object, CacheEntry> SNAPSHOTS = new WeakHashMap<>();
    private static final ThreadLocal<CaptureState> CAPTURES =
            ThreadLocal.withInitial(CaptureState::new);

    private ExactNetworkStorageSnapshotCache() {
    }

    /**
     * 利用可能なSnapshotをtargetへ複製する。miss時はRETURNで保存するcaptureを開始する。
     *
     * @return 元のNetworkStorage走査を省略できる場合はtrue
     */
    public static boolean reuseOrBegin(
            NetworkStorage storage,
            KeyCounter target) {
        return reuseOrBegin(
                storage,
                target,
                ACOConfig.enableExactBigIntegerInventorySnapshots(),
                ServerTickClock.currentTick(),
                true);
    }

    /** getAvailableStacksのRETURNで、HEADから変更されていない集計だけを保存する。 */
    public static void finish(
            NetworkStorage storage,
            KeyCounter target) {
        finish(
                storage,
                target,
                ServerTickClock.currentTick());
    }

    /** insert/extractやmount変更後に、同じtickの全依存Snapshotを失効させる。 */
    public static void invalidateAll() {
        // 機能OFF時はNetworkStorageの通常取引へ世代更新や診断加算を持ち込まない。
        if (!ACOConfig.enableExactBigIntegerInventorySnapshots()) {
            return;
        }
        advanceGeneration();
        OptimizationMetrics.recordExactStorageSnapshotInvalidation();
    }

    /** server開始・停止時に弱参照Cacheと未完了captureを破棄する。 */
    public static void reset() {
        synchronized (SNAPSHOTS) {
            SNAPSHOTS.clear();
        }
        STORAGE_GENERATION.set(0L);
        CAPTURES.remove();
    }

    static boolean reuseOrBeginForTests(
            Object storage,
            KeyCounter target,
            boolean enabled,
            long tick) {
        return reuseOrBegin(storage, target, enabled, tick, false);
    }

    static void finishForTests(
            Object storage,
            KeyCounter target,
            long tick) {
        finish(storage, target, tick);
    }

    static void invalidateForTests() {
        advanceGeneration();
    }

    static void resetForTests() {
        reset();
    }

    private static boolean reuseOrBegin(
            Object storage,
            KeyCounter target,
            boolean enabled,
            long tick,
            boolean recordMetrics) {
        // 無効時、server tick開始前、または加算済みCounterではAE2本来の走査を維持する。
        if (!enabled || tick <= 0L || !isEmpty(target)) {
            return false;
        }

        long generation = STORAGE_GENERATION.get();
        CaptureState state = CAPTURES.get();
        state.moveTo(tick, generation);

        CacheEntry cached;
        synchronized (SNAPSHOTS) {
            cached = SNAPSHOTS.get(storage);
            // tickまたは世代が違う値を後続呼出しで誤利用しない。
            if (cached != null
                    && (cached.tick() != tick
                            || cached.generation() != generation)) {
                SNAPSHOTS.remove(storage);
                cached = null;
            }
        }
        // 同じtick・同じ世代の完成Snapshotだけを、正確値ごとtargetへ複製する。
        if (cached != null) {
            copyInto(cached.snapshot(), target);
            // 単体試験ではグローバル診断値を変更しない。
            if (recordMetrics) {
                OptimizationMetrics.recordExactStorageSnapshotCache(true);
            }
            return true;
        }

        // 同一NetworkStorageの再帰呼出しはAE2のmountsInUse保護へ任せ、captureを上書きしない。
        if (state.active().containsKey(storage)) {
            return false;
        }

        boolean nestedNetworkScan = !state.active().isEmpty();
        state.active().put(
                storage,
                new ActiveCapture(target, tick, generation));
        // 単体試験ではグローバル診断値を変更しない。
        if (recordMetrics) {
            OptimizationMetrics.recordExactStorageSnapshotCache(false);
            // 外側Networkの走査中に別Networkへ入った回数だけをnestedとして記録する。
            if (nestedNetworkScan) {
                OptimizationMetrics.recordExactStorageNestedScan();
            }
        }
        return false;
    }

    private static void finish(
            Object storage,
            KeyCounter target,
            long tick) {
        CaptureState state = CAPTURES.get();
        long generation = STORAGE_GENERATION.get();
        state.moveTo(tick, generation);

        ActiveCapture capture = state.active().get(storage);
        // cache hitや非対象呼出しのRETURNでは保存処理を行わない。
        if (capture == null || capture.target() != target) {
            return;
        }
        state.active().remove(storage);

        // 集計中にstorage世代が変わったSnapshotは、完全な一時点を表さないため破棄する。
        if (capture.tick() != tick
                || capture.generation() != generation
                || generation != STORAGE_GENERATION.get()) {
            return;
        }

        KeyCounter snapshot = BigKeyCounterSidecars.copyOf(target);
        // 複製中に別threadから世代が変わった場合も、古いSnapshotを公開しない。
        if (generation != STORAGE_GENERATION.get()) {
            return;
        }
        synchronized (SNAPSHOTS) {
            // Lock待ちの間にも世代が変わり得るため、公開直前に最後の照合を行う。
            if (generation == STORAGE_GENERATION.get()) {
                SNAPSHOTS.put(
                        storage,
                        new CacheEntry(tick, generation, snapshot));
            }
        }
    }

    private static boolean isEmpty(KeyCounter counter) {
        // long Facadeに既存値があれば、Network寄与だけを安全にCacheできない。
        if (!counter.isEmpty()) {
            return false;
        }
        // Facadeが空でもSidecarに値があるCounterは、加算済みtargetとして扱う。
        return BigKeyCounterSidecars.snapshot(counter)
                .map(snapshot -> snapshot.amounts().isEmpty())
                .orElse(true);
    }

    private static void copyInto(
            KeyCounter source,
            KeyCounter target) {
        target.addAll(source);
        BigKeyCounterSidecars.copyVisible(source, target);
    }

    private static void advanceGeneration() {
        STORAGE_GENERATION.updateAndGet(
                generation -> generation == Long.MAX_VALUE
                        ? 1L
                        : generation + 1L);
    }

    private record CacheEntry(
            long tick,
            long generation,
            KeyCounter snapshot) {
    }

    private record ActiveCapture(
            KeyCounter target,
            long tick,
            long generation) {
    }

    private static final class CaptureState {
        private final IdentityHashMap<Object, ActiveCapture> active =
                new IdentityHashMap<>();
        private long tick = Long.MIN_VALUE;
        private long generation = Long.MIN_VALUE;

        private IdentityHashMap<Object, ActiveCapture> active() {
            return active;
        }

        private void moveTo(
                long currentTick,
                long currentGeneration) {
            // 例外でRETURNへ到達しなかったcaptureも、次tickまたは次世代で必ず破棄する。
            if (tick != currentTick || generation != currentGeneration) {
                active.clear();
                tick = currentTick;
                generation = currentGeneration;
            }
        }
    }
}
