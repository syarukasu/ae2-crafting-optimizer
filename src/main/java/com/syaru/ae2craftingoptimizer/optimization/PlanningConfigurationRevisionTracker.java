package com.syaru.ae2craftingoptimizer.optimization;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Plannerの判断へ影響するACO設定の単調revisionを管理する。
 *
 * <p>ForgeのConfig reloadは任意threadで発火するため、workerは設定オブジェクトを
 * 同期せず、このrevisionをCaptureの前後で比較して混在した計画を採用しない。</p>
 */
public final class PlanningConfigurationRevisionTracker {
    private static final AtomicLong REVISION = new AtomicLong(1L);

    private PlanningConfigurationRevisionTracker() {
    }

    public static long current() {
        return REVISION.get();
    }

    /** ACO Configのload、reload、unloadごとに一度だけ呼び出す。 */
    public static long invalidate() {
        return REVISION.updateAndGet(current -> {
            // Issue #167: wrapすると古い設定CaptureとのABA一致を作るため明示失敗する。
            if (current == Long.MAX_VALUE) {
                throw new IllegalStateException("planning configuration revision exhausted");
            }
            return current + 1L;
        });
    }

    public static boolean isCurrent(long revision) {
        return revision > 0L && REVISION.get() == revision;
    }
}
