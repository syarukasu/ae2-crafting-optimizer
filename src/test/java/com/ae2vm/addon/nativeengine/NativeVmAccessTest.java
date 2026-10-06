package com.ae2vm.addon.nativeengine;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class NativeVmAccessTest {
    @Test void pureBigIntegerArithmeticFinishesWithoutASingleTick() throws Exception {
        var access = new NativeVmAccess();
        var unit = BigInteger.TEN.pow(1024).subtract(BigInteger.ONE);
        try (var worker = new Worker<>(access, () -> {
            var total = BigInteger.ZERO;
            for (int i = 0; i < 100_000; i++) {
                access.checkpoint();
                total = total.add(unit);
            }
            return total;
        })) {
            assertEquals(unit.multiply(BigInteger.valueOf(100_000)), worker.get());
            assertFalse(access.simulateFor(5_000));
            assertEquals(0, access.metrics().intervals());
        }
    }

    @Test void tickingNeverWaitsForAnArithmeticOnlyWorker() throws Exception {
        var access = new NativeVmAccess();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var worker = new Worker<>(access, () -> {
            entered.countDown();
            assertTrue(release.await(2, TimeUnit.SECONDS), "server waited for arithmetic");
            access.checkpoint();
            return 7;
        })) {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertTrue(access.simulateFor(5_000));
            release.countDown();
            assertEquals(7, worker.get());
        } finally { release.countDown(); }
    }

    @Test void expiredIntervalReleasesServerButDoesNotPauseArithmetic() throws Exception {
        var now = new AtomicLong();
        var access = new NativeVmAccess(now::get);
        var server = Thread.currentThread();
        try (var worker = new Worker<>(access, () -> {
            access.read(() -> {
                assertEquals(Thread.State.WAITING, server.getState());
                now.set(5_000_001);
                return null;
            });
            for (int i = 0; i < 100_000; i++) access.checkpoint();
            return 17;
        })) {
            worker.awaitWaiting();
            access.simulateFor(5_000);
            assertEquals(17, worker.get(), "arithmetic required another tick");
            assertEquals(1, access.metrics().intervals());
            assertEquals(5_000_001, access.metrics().exclusiveNanos());
        }
    }

    @Test void consecutiveLiveReadsShareOneGrantWithoutServerTasks() throws Exception {
        var now = new AtomicLong();
        var access = new NativeVmAccess(now::get);
        var server = Thread.currentThread();
        try (var worker = new Worker<>(access, () -> {
            int sum = 0;
            for (int i = 0; i < 100; i++) sum += access.read(() -> {
                assertEquals(Thread.State.WAITING, server.getState());
                return 1;
            });
            now.set(5_000_001);
            access.checkpoint();
            return sum;
        })) {
            worker.awaitWaiting();
            access.simulateFor(5_000);
            assertEquals(100, worker.get());
            assertEquals(1, access.metrics().intervals());
            assertEquals(100, access.metrics().reads());
        }
    }

    @Test void diagnosticsSeparateWorldWaitFromServerExclusiveTime() throws Exception {
        var now = new AtomicLong();
        var access = new NativeVmAccess(now::get);
        try (var worker = new Worker<>(access, () -> {
            var timing = new NativeVmTiming(now::get);
            timing.ae2Scheduled(access);
            try (var phase = timing.phase(NativeVmTiming.Phase.VM)) {
                access.read(() -> { now.set(12_000_001); return null; });
            }
            return timing.summary();
        })) {
            worker.awaitWaiting();
            now.set(7_000_000);
            access.simulateFor(5_000);
            var timing = worker.get();
            assertTrue(timing.contains("ae2WorldWaitMs=7"));
            assertTrue(timing.contains("ae2ExclusiveMs=5"));
            assertTrue(timing.contains("ae2Intervals=1 ae2Reads=1"));
            assertTrue(timing.contains("ae2ReadKinds={OTHER:1}"));
            assertTrue(timing.contains("scheduler=ae2-boundary"));
            assertTrue(timing.contains("vmMs=12 vmActiveMs=-1"));
        }
    }

    @Test void labeledReadsPreserveTheTotalAndShareOneExclusiveInterval() throws Exception {
        var access = new NativeVmAccess();
        try (var worker = new Worker<>(access, () -> {
            access.read(NativeVmAccess.ReadKind.PRODUCER, () -> 1);
            access.read(NativeVmAccess.ReadKind.INPUT_REMAINDER, () -> 2);
            access.read(NativeVmAccess.ReadKind.INPUT_REMAINDER, () -> 3);
            return access.readKindSummary();
        })) {
            worker.awaitWaiting();
            access.simulateFor(5_000);
            assertEquals("{PRODUCER:1,INPUT_REMAINDER:2}", worker.get());
            assertEquals(3, access.metrics().reads());
            assertEquals(1, access.metrics().intervals());
        }
    }

    @Test void nestedCallbacksCannotReleaseTheirOuterWorldAccess() throws Exception {
        var now = new AtomicLong();
        var access = new NativeVmAccess(now::get);
        var server = Thread.currentThread();
        var completed = new AtomicBoolean();
        try (var worker = new Worker<>(access, () -> access.read(() -> {
            now.set(9_000_000);
            access.checkpoint();
            int value = access.read(() -> {
                assertEquals(Thread.State.WAITING, server.getState());
                return 11;
            });
            assertEquals(Thread.State.WAITING, server.getState());
            completed.set(true);
            return value;
        }))) {
            worker.awaitWaiting();
            access.simulateFor(5_000);
            assertTrue(completed.get());
            assertEquals(11, worker.get());
            assertEquals(1, access.metrics().intervals());
        }
    }

    @Test void onlyANewLiveReadWaitsAfterExpiry() throws Exception {
        var now = new AtomicLong();
        var access = new NativeVmAccess(now::get);
        var reads = new AtomicInteger();
        try (var worker = new Worker<>(access, () -> {
            for (int i = 0; i < 2; i++) access.read(() -> {
                reads.incrementAndGet();
                now.addAndGet(5_000_001);
                return null;
            });
            return reads.get();
        })) {
            worker.awaitWaiting();
            access.simulateFor(5_000);
            worker.awaitWaiting();
            assertEquals(1, reads.get());
            access.simulateFor(5_000);
            assertEquals(2, worker.get());
            assertEquals(2, access.metrics().intervals());
        }
    }

    @Test void interruptionWhileWaitingNeedsNoFutureTick() throws Exception {
        var access = new NativeVmAccess();
        var calls = new AtomicInteger();
        try (var worker = new Worker<>(access, () -> access.read(calls::incrementAndGet))) {
            worker.awaitWaiting();
            worker.thread.interrupt();
            var failure = assertThrows(ExecutionException.class, worker::get);
            assertInstanceOf(CancellationException.class, failure.getCause());
            assertEquals(0, calls.get());
            assertFalse(access.simulateFor(5_000));
        }
    }

    @Test void callbackFailureWakesTheServerAndPreservesTheException() throws Exception {
        var access = new NativeVmAccess();
        var failure = new IllegalStateException("invalid pattern");
        try (var worker = new Worker<>(access, () -> access.read(() -> { throw failure; }))) {
            worker.awaitWaiting();
            assertFalse(access.simulateFor(5_000));
            assertSame(failure, assertThrows(ExecutionException.class, worker::get).getCause());
        }
    }

    @Test void independentJobsNeverShareWorldPermission() throws Exception {
        var first = new NativeVmAccess();
        var second = new NativeVmAccess();
        var calls = new AtomicInteger();
        try (var one = new Worker<>(first, () -> first.read(calls::incrementAndGet));
                var two = new Worker<>(second, () -> second.read(calls::incrementAndGet))) {
            one.awaitWaiting(); two.awaitWaiting();
            first.simulateFor(5_000);
            assertEquals(1, one.get());
            assertFalse(two.task.isDone());
            second.simulateFor(5_000);
            assertEquals(2, two.get());
        }
    }

    private static final class Worker<T> implements AutoCloseable {
        final FutureTask<T> task;
        final Thread thread;
        Worker(NativeVmAccess access, Callable<T> action) {
            task = new FutureTask<>(() -> {
                try { return action.call(); }
                finally { access.finish(); }
            });
            thread = new Thread(task, "VM access test");
            thread.setDaemon(true);
            thread.start();
        }
        T get() throws Exception { return task.get(3, TimeUnit.SECONDS); }
        void awaitWaiting() {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (thread.getState() != Thread.State.WAITING && System.nanoTime() < deadline && !task.isDone())
                LockSupport.parkNanos(100_000);
            assertEquals(Thread.State.WAITING, thread.getState(), "worker did not request world access");
        }
        @Override public void close() throws Exception {
            thread.interrupt();
            thread.join(3_000);
            assertFalse(thread.isAlive(), "worker leaked");
        }
    }
}
