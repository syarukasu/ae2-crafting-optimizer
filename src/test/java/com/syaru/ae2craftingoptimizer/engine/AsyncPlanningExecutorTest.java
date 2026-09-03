package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AsyncPlanningExecutorTest {
    @Test
    void runsPlanningOnOneDedicatedWorkerInsteadOfTheCaller() throws Exception {
        String caller = Thread.currentThread().getName();
        try (AsyncPlanningExecutor executor = new AsyncPlanningExecutor()) {
            Future<String> task = executor.submit(() -> Thread.currentThread().getName());

            assertNotEquals(caller, task.get(5, TimeUnit.SECONDS));
            assertEquals("ACO Planner", task.get());
        }
    }

    @Test
    void serverStopCancelsQueuedPlanning() throws Exception {
        AsyncPlanningExecutor executor = new AsyncPlanningExecutor();
        CountDownLatch running = new CountDownLatch(1);
        executor.submit(() -> {
            running.countDown();
            new CountDownLatch(1).await();
            return null;
        });
        assertTrue(running.await(5, TimeUnit.SECONDS));
        Future<String> queued = executor.submit(() -> "must not run");

        executor.close();

        assertTrue(queued.isCancelled());
    }
}
