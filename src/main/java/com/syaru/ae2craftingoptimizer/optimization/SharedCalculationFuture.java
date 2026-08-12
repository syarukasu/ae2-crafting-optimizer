package com.syaru.ae2craftingoptimizer.optimization;

import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
/**
 * Shares one AE2 calculation without allowing one requester's cancellation to
 * cancel the calculation still used by other requesters.
 */
final class SharedCalculationFuture<T> {
    private final Future<T> delegate;
    private final Object lock = new Object();
    private int subscribers;
    private boolean cancellationRequested;

    SharedCalculationFuture(Future<T> delegate) {
        this.delegate = delegate;
    }

    Future<T> acquire() {
        synchronized (lock) {
            // 完了済み計算は新しい所有権を持たせず、そのまま読み取り専用で共有する。
            if (delegate.isDone() || cancellationRequested) {
                return null;
            }
            subscribers++;
            return new Subscriber(true);
        }
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

    private void release(boolean mayInterruptIfRunning, boolean tracked) {
        if (!tracked) {
            return;
        }
        boolean cancelDelegate = false;
        synchronized (lock) {
            // 同じSubscriberからの二重cancelを下流Futureへ二重伝播させない。
            subscribers--;
            // 最後の所有者が離れた時だけ、AE2の計算本体をキャンセルする。
            if (subscribers == 0 && !delegate.isDone() && !cancellationRequested) {
                cancellationRequested = true;
                cancelDelegate = true;
            }
        }
        // delegate.cancelは外部実装の処理を含むため、共有ロックの外で呼び出す。
        if (cancelDelegate) {
            delegate.cancel(mayInterruptIfRunning);
        }
    }

    private final class Subscriber implements Future<T> {
        private final boolean tracked;
        private volatile boolean cancelled;
        private boolean released;

        private Subscriber(boolean tracked) {
            this.tracked = tracked;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            synchronized (this) {
                // 同一呼出し元の二重cancelは所有者数を減らさない。
                if (cancelled || released || isDone()) {
                    return false;
                }
                cancelled = true;
                released = true;
            }
            release(mayInterruptIfRunning, tracked);
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
            // 自分だけがキャンセルした場合は、他の購読者の結果を隠す。
            if (cancelled) {
                throw new CancellationException();
            }
        }
    }
}
