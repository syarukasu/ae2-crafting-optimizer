package com.syaru.ae2craftingoptimizer.optimization;

import java.util.concurrent.atomic.AtomicLong;

/** 一つのAE2 StorageServiceだけが所有する、lock-free更新可能な単調revision。 */
public final class StorageRevisionState {
    /** 0は未初期化と区別するため使わず、最初の有効revisionを1とする。 */
    private static final long INITIAL_REVISION = 1L;

    private final AtomicLong revision = new AtomicLong(INITIAL_REVISION);

    /** AE2がcached inventoryを更新した後に、その内容を表すrevisionを固定する。 */
    public long capture() {
        return revision.get();
    }

    public long current() {
        return revision.get();
    }

    /** mount、unmount、insert、extractを待たずに通知できる単調更新経路。 */
    public void advance() {
        while (true) {
            long current = revision.get();
            // Issue #167: wrapによるABA一致を作らず、現実上到達不能な上限で明示失敗する。
            if (current == Long.MAX_VALUE) {
                throw new IllegalStateException("storage revision exhausted");
            }
            if (revision.compareAndSet(current, current + 1L)) {
                return;
            }
        }
    }
}
