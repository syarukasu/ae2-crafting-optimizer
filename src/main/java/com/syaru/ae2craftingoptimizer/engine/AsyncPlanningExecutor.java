package com.syaru.ae2craftingoptimizer.engine;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import org.jetbrains.annotations.Nullable;

/** AE2の計算workerから純粋な計画処理だけを分離する、ACO専用の単一worker。 */
final class AsyncPlanningExecutor implements AutoCloseable {
    private final ExecutorService executor;

    AsyncPlanningExecutor() {
        executor = Executors.newSingleThreadExecutor(
                task -> {
                    Thread worker = new Thread(task, "ACO Planner");
                    worker.setDaemon(true);
                    return worker;
                });
    }

    /** Server停止後は呼出threadで代行せず、明示的に受付を辞退する。 */
    @Nullable
    <T> Future<T> submit(Callable<T> task) {
        Objects.requireNonNull(task, "task");
        try {
            return executor.submit(task);
        } catch (RejectedExecutionException unavailable) {
            return null;
        }
    }

    @Override
    public void close() {
        List<Runnable> queued = executor.shutdownNow();
        // shutdownNowはqueueから外すだけなので、待機側へ完了を通知するためFutureもcancelする。
        for (Runnable task : queued) {
            if (task instanceof Future<?> future) {
                future.cancel(false);
            }
        }
    }
}
