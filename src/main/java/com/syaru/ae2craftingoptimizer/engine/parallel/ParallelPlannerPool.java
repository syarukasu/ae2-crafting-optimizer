package com.syaru.ae2craftingoptimizer.engine.parallel;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

/** 一件のクラフトツリー内だけで共有する、固定4本のACO専用work-stealing pool。 */
public final class ParallelPlannerPool implements AutoCloseable {
    public static final int PARALLELISM = 4;
    private static final long TEST_AWAIT_SECONDS = 5L;

    private final AtomicInteger nextWorkerName = new AtomicInteger();
    private final Object lifecycleLock = new Object();
    private final ForkJoinPool pool;

    public ParallelPlannerPool() {
        this.pool = new ForkJoinPool(
                PARALLELISM,
                this::newWorker,
                null,
                true);
    }

    void execute(Runnable task) {
        pool.execute(Objects.requireNonNull(task, "task"));
    }

    /** 一つのphaseを担当する4 workerを、closeと競合させず一括投入する。 */
    void executeSessionWorkers(IntConsumer workerTask) {
        Objects.requireNonNull(workerTask, "workerTask");
        CountDownLatch startGate = new CountDownLatch(PARALLELISM);
        synchronized (lifecycleLock) {
            if (pool.isShutdown()) {
                throw new IllegalStateException("parallel planner pool is shut down");
            }
            for (int worker = 0; worker < PARALLELISM; worker++) {
                int workerId = worker;
                pool.execute(() -> {
                    startGate.countDown();
                    awaitStart(startGate);
                    workerTask.accept(workerId);
                });
            }
        }
    }

    public int poolSize() {
        return pool.getPoolSize();
    }

    public int parallelism() {
        return pool.getParallelism();
    }

    public boolean isShutdown() {
        return pool.isShutdown();
    }

    @Override
    public void close() {
        synchronized (lifecycleLock) {
            pool.shutdown();
        }
    }

    boolean awaitTerminationForTest() throws InterruptedException {
        return pool.awaitTermination(TEST_AWAIT_SECONDS, TimeUnit.SECONDS);
    }

    private ForkJoinWorkerThread newWorker(ForkJoinPool owner) {
        ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(owner);
        int sequence = nextWorkerName.getAndIncrement();
        // poolのlive workerは常に4本以下なので、通常時の名前は#1から#4だけになる。
        worker.setName("ACO Tree Planner #" + (sequence % PARALLELISM + 1));
        worker.setDaemon(true);
        return worker;
    }

    private static void awaitStart(CountDownLatch startGate) {
        boolean interrupted = false;
        while (true) {
            try {
                startGate.await();
                break;
            } catch (InterruptedException ignored) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
