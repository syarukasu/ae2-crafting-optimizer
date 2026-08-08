package com.syaru.ae2craftingoptimizer.optimization;

import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shares one AE2 calculation without allowing one requester's cancellation to
 * cancel the calculation still used by other requesters.
 */
final class SharedCalculationFuture<T> {
    private final Future<T> delegate;
    private final AtomicInteger subscribers = new AtomicInteger();

    SharedCalculationFuture(Future<T> delegate) {
        this.delegate = delegate;
    }

    Future<T> acquire() {
        subscribers.incrementAndGet();
        return new Subscriber();
    }

    Future<T> delegate() {
        return delegate;
    }

    boolean isDone() {
        return delegate.isDone();
    }

    boolean isCancelled() {
        return delegate.isCancelled();
    }

    private void release(boolean mayInterruptIfRunning) {
        int remaining = subscribers.decrementAndGet();
        if (remaining == 0 && !delegate.isDone()) {
            delegate.cancel(mayInterruptIfRunning);
        }
    }

    private final class Subscriber implements Future<T> {
        private volatile boolean cancelled;
        private boolean released;

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            synchronized (this) {
                if (cancelled || released || isDone()) {
                    return false;
                }
                cancelled = true;
                released = true;
            }
            release(mayInterruptIfRunning);
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled || delegate.isCancelled();
        }

        @Override
        public boolean isDone() {
            return cancelled || delegate.isDone();
        }

        @Override
        public T get() throws InterruptedException, java.util.concurrent.ExecutionException {
            ensureNotCancelled();
            return delegate.get();
        }

        @Override
        public T get(long timeout, TimeUnit unit)
                throws InterruptedException, java.util.concurrent.ExecutionException,
                java.util.concurrent.TimeoutException {
            ensureNotCancelled();
            return delegate.get(timeout, unit);
        }

        private void ensureNotCancelled() {
            if (cancelled) {
                throw new CancellationException();
            }
        }
    }
}
