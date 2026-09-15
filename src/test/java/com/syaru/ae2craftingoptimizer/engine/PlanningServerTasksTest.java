package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class PlanningServerTasksTest {
    @Test
    void detachedWorkerWaitsForServerButDoesNotExecuteItsTask() throws Exception {
        var queue = new ArrayBlockingQueue<Runnable>(1);
        Executor server = queue::add;
        var worker = Executors.newSingleThreadExecutor();
        Thread owner = Thread.currentThread();
        try {
            var result = worker.submit(() -> PlanningServerTasks.call(server, Thread::currentThread));
            Runnable task = queue.poll(5, TimeUnit.SECONDS); // Test deadlock deadline, not a production timeout.
            assertNotNull(task);
            assertFalse(result.isDone());
            task.run();
            assertSame(owner, result.get(5, TimeUnit.SECONDS));
            var failure = worker.submit(() -> PlanningServerTasks.call(server, () -> {
                throw new ArithmeticException("exact failure");
            }));
            Runnable failingTask = queue.poll(5, TimeUnit.SECONDS);
            assertNotNull(failingTask);
            failingTask.run();
            assertInstanceOf(ArithmeticException.class,
                    assertThrows(ExecutionException.class, () -> failure.get(5, TimeUnit.SECONDS)).getCause());
        } finally {
            PlanningServerTasks.stop(server);
            worker.shutdownNow();
        }
    }

    @Test
    void stopReleasesWaitingWorkerAndPreventsLateMaterialization() throws Exception {
        var queue = new ArrayBlockingQueue<Runnable>(1);
        Executor server = queue::add;
        var worker = Executors.newSingleThreadExecutor();
        var materialized = new AtomicBoolean();
        var interrupted = new AtomicBoolean();
        try {
            var result = worker.submit(() -> {
                PlanningServerTasks.registerWorker(server);
                try {
                    return PlanningServerTasks.call(server, () -> {
                        materialized.set(true);
                        return 1;
                    });
                } finally {
                    interrupted.set(Thread.currentThread().isInterrupted());
                    PlanningServerTasks.releaseWorker(server);
                }
            });
            Runnable pending = queue.poll(5, TimeUnit.SECONDS);
            assertNotNull(pending);
            PlanningServerTasks.stop(server);
            assertInstanceOf(PlanningCancelledException.class,
                    assertThrows(ExecutionException.class, () -> result.get(5, TimeUnit.SECONDS)).getCause());
            pending.run();
            assertFalse(materialized.get());
            assertTrue(interrupted.get());
            assertThrows(PlanningCancelledException.class, () -> PlanningServerTasks.registerWorker(server));
            assertThrows(PlanningCancelledException.class, () -> PlanningServerTasks.call(server, () -> 2));
        } finally {
            PlanningServerTasks.stop(server);
            worker.shutdownNow();
        }
    }
}
