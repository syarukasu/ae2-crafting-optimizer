package com.syaru.ae2craftingoptimizer.engine;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.function.Supplier;

/** Issue #179: tick待機を解除したworkerからのServer処理。計算用executorは作らない。 */
public final class PlanningServerTasks {
    // 状態側はexecutorを保持せず、終了済みServerの弱参照を強参照へ戻さない。
    private static final Map<Executor, PendingWork> PENDING = new WeakHashMap<>();

    private PlanningServerTasks() {
    }

    public static void registerWorker(Executor server) {
        synchronized (PENDING) {
            var work = PENDING.computeIfAbsent(server, ignored -> new PendingWork());
            // 停止後の新規workerは、次tick待ちへ入る前に取り消す。
            if (work.stopped) {
                throw new PlanningCancelledException(0);
            }
            work.workers.add(Thread.currentThread());
        }
    }

    public static void releaseWorker(Executor server) {
        synchronized (PENDING) {
            var work = PENDING.get(server);
            // 未登録のworkerや、既に解放済みのServerには何もしない。
            if (work == null) {
                return;
            }
            work.workers.remove(Thread.currentThread());
            removeIfIdle(server, work);
        }
    }

    public static <T> T call(Executor server, Supplier<T> action) {
        // 取消済みworkerから新たなbindingや在庫取得を予約しない。
        if (Thread.currentThread().isInterrupted()) {
            throw new PlanningCancelledException(0);
        }
        FutureTask<T> task = new FutureTask<>(action::get);
        synchronized (PENDING) {
            // stopと登録の競合を、この短い所有権更新だけで排他する。
            var work = PENDING.computeIfAbsent(server, ignored -> new PendingWork());
            if (work.stopped) {
                throw new PlanningCancelledException(0);
            }
            work.tasks.add(task);
        }
        try {
            server.execute(task);
            return task.get();
        } catch (InterruptedException interrupted) {
            task.cancel(false);
            Thread.currentThread().interrupt();
            throw new PlanningCancelledException(0);
        } catch (CancellationException cancelled) {
            throw new PlanningCancelledException(0);
        } catch (ExecutionException failed) {
            Throwable cause = failed.getCause();
            // 診断理由とVM Errorを別の失敗へ読み替えない。
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error fatal) {
                throw fatal;
            }
            throw new IllegalStateException("planning server task failed", cause);
        } finally {
            task.cancel(false);
            synchronized (PENDING) {
                var work = PENDING.get(server);
                // stop側が所有権を引き取った集合へ再登録しない。
                if (work != null) {
                    work.tasks.remove(task);
                    removeIfIdle(server, work);
                }
            }
        }
    }

    public static void stop(Executor server) {
        Set<FutureTask<?>> tasks;
        synchronized (PENDING) {
            var work = PENDING.computeIfAbsent(server, ignored -> new PendingWork());
            work.stopped = true;
            tasks = new HashSet<>(work.tasks);
            /*
             * 登録解除と同じlock内で通知する。解除後に別注文へ再利用されたThreadを
             * 遅れてinterruptしない。Serverはworkerの終了を待たない。
             */
            for (Thread worker : work.workers) {
                worker.interrupt();
            }
        }
        // Serverがもう実行しないtaskを取消し、切離し区間のworkerのgetを解除する。
        for (FutureTask<?> task : tasks) {
            task.cancel(false);
        }
    }

    private static void removeIfIdle(Executor server, PendingWork work) {
        // 停止印は次の受付を拒否するため残し、通常の完了状態だけを解放する。
        if (!work.stopped && work.tasks.isEmpty() && work.workers.isEmpty()) {
            PENDING.remove(server);
        }
    }

    private static final class PendingWork {
        private final Set<FutureTask<?>> tasks = new HashSet<>();
        private final Set<Thread> workers = new HashSet<>();
        private boolean stopped;
    }
}
