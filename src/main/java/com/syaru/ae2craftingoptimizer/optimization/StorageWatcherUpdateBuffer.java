package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.networking.storage.IStorageWatcherNode;
import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

public final class StorageWatcherUpdateBuffer {
    private static final Map<IStorageWatcherNode, Map<AEKey, Long>> PENDING = new WeakHashMap<>();
    private static int ticksSinceFlush;

    private StorageWatcherUpdateBuffer() {
    }

    public static void onStackChange(IStorageWatcherNode watcher, AEKey key, long amount) {
        if (!ACOConfig.throttleStorageWatcherUpdates()) {
            watcher.onStackChange(key, amount);
            return;
        }

        synchronized (PENDING) {
            Map<AEKey, Long> watcherUpdates = PENDING.computeIfAbsent(watcher, ignored -> new HashMap<>());
            watcherUpdates.put(key, amount);
            if (countPendingUpdates() >= ACOConfig.getMaximumBufferedChanges()) {
                flushLocked();
            }
        }
    }

    public static void tick() {
        if (!ACOConfig.throttleStorageWatcherUpdates()) {
            flush();
            return;
        }

        synchronized (PENDING) {
            ticksSinceFlush++;
            if (ticksSinceFlush >= ACOConfig.getStorageWatcherUpdateIntervalTicks()) {
                flushLocked();
            }
        }
    }

    public static void flush() {
        synchronized (PENDING) {
            flushLocked();
        }
    }

    private static int countPendingUpdates() {
        int total = 0;
        for (Map<AEKey, Long> updates : PENDING.values()) {
            total += updates.size();
        }
        return total;
    }

    private static void flushLocked() {
        Iterator<Map.Entry<IStorageWatcherNode, Map<AEKey, Long>>> iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<IStorageWatcherNode, Map<AEKey, Long>> watcherEntry = iterator.next();
            IStorageWatcherNode watcher = watcherEntry.getKey();
            if (watcher != null) {
                for (Map.Entry<AEKey, Long> update : watcherEntry.getValue().entrySet()) {
                    watcher.onStackChange(update.getKey(), update.getValue());
                }
            }
            iterator.remove();
        }
        ticksSinceFlush = 0;
    }
}
