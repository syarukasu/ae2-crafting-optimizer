package com.syaru.ae2craftingoptimizer.optimization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class SharedCalculationFutureTest {
    @Test
    void oneRequesterCancellationDoesNotCancelOtherSubscribers() throws Exception {
        CompletableFuture<String> delegate = new CompletableFuture<>();
        SharedCalculationFuture<String> shared = new SharedCalculationFuture<>(delegate);
        Future<String> first = shared.acquire();
        Future<String> second = shared.acquire();

        assertTrue(first.cancel(true));
        assertTrue(first.isCancelled());
        assertFalse(delegate.isCancelled());

        delegate.complete("plan");
        assertEquals("plan", second.get());
        assertFalse(second.isCancelled());
    }

    @Test
    void lastRequesterCancellationCancelsUnderlyingCalculation() {
        CompletableFuture<String> delegate = new CompletableFuture<>();
        SharedCalculationFuture<String> shared = new SharedCalculationFuture<>(delegate);
        Future<String> first = shared.acquire();
        Future<String> second = shared.acquire();

        assertTrue(first.cancel(true));
        assertTrue(second.cancel(true));
        assertTrue(delegate.isCancelled());
    }

    @Test
    void completedDelegateDoesNotCreateAStaleSubscriber() {
        CompletableFuture<String> delegate = CompletableFuture.completedFuture("plan");
        SharedCalculationFuture<String> shared = new SharedCalculationFuture<>(delegate);

        assertTrue(shared.acquire() == null);
        assertSame(delegate, shared.acquireOrDelegate());
    }

    @Test
    void subscriberOwnershipIsScopedToItsSharedCalculation() {
        SharedCalculationFuture<String> first = new SharedCalculationFuture<>(new CompletableFuture<>());
        SharedCalculationFuture<String> second = new SharedCalculationFuture<>(new CompletableFuture<>());
        Future<String> subscriber = first.acquire();

        assertTrue(first.owns(subscriber));
        assertFalse(second.owns(subscriber));
        assertFalse(first.owns(CompletableFuture.completedFuture("plan")));
    }

    @Test
    void cancellationWhileGetIsWaitingDoesNotLeakSharedResult() throws Exception {
        assertCancellationWhileWaiting(false);
    }

    @Test
    void cancellationWhileTimedGetIsWaitingDoesNotLeakSharedResult() throws Exception {
        assertCancellationWhileWaiting(true);
    }

    private static void assertCancellationWhileWaiting(boolean timedGet) throws Exception {
        BlockingFuture<String> delegate = new BlockingFuture<>();
        SharedCalculationFuture<String> shared = new SharedCalculationFuture<>(delegate);
        Future<String> cancelledSubscriber = shared.acquire();
        Future<String> survivingSubscriber = shared.acquire();

        CompletableFuture<Throwable> observed = CompletableFuture.supplyAsync(() -> {
            try {
                // timed getと通常getの双方で、待機開始後キャンセルの競合を再現する。
                if (timedGet) {
                    cancelledSubscriber.get(5L, TimeUnit.SECONDS);
                } else {
                    cancelledSubscriber.get();
                }
                return null;
            } catch (Exception failure) {
                return failure;
            }
        });

        assertTrue(delegate.awaitGetCall(5L, TimeUnit.SECONDS));
        assertTrue(cancelledSubscriber.cancel(false));
        assertFalse(delegate.isCancelled());
        delegate.complete("plan");

        Throwable failure = observed.get(5L, TimeUnit.SECONDS);
        assertTrue(failure instanceof CancellationException);
        assertEquals("plan", survivingSubscriber.get(5L, TimeUnit.SECONDS));
    }

    private static final class BlockingFuture<T> implements Future<T> {
        private final CountDownLatch getCalled = new CountDownLatch(1);
        private final CountDownLatch completed = new CountDownLatch(1);
        private volatile boolean cancelled;
        private T value;

        boolean awaitGetCall(long timeout, TimeUnit unit) throws InterruptedException {
            return getCalled.await(timeout, unit);
        }

        void complete(T completedValue) {
            value = completedValue;
            completed.countDown();
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            // 完了済みFutureは通常のFuture契約どおりキャンセルしない。
            if (isDone()) {
                return false;
            }
            cancelled = true;
            completed.countDown();
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return cancelled || completed.getCount() == 0L;
        }

        @Override
        public T get() throws InterruptedException {
            getCalled.countDown();
            completed.await();
            return completedValue();
        }

        @Override
        public T get(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
            getCalled.countDown();
            // 指定時間内にテスト用Futureが完了しない場合は、競合試験を明示的に失敗させる。
            if (!completed.await(timeout, unit)) {
                throw new TimeoutException("blocking test future timed out");
            }
            return completedValue();
        }

        private T completedValue() {
            // 下流Future自体がキャンセルされた場合だけCancellationExceptionを返す。
            if (cancelled) {
                throw new CancellationException();
            }
            return value;
        }
    }
}
