package com.syaru.ae2craftingoptimizer.api.contract;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Explicitly closable registration handle. Closing more than once is safe. */
public final class RevisionWakeupRegistration implements AutoCloseable {
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Runnable closeAction;

    RevisionWakeupRegistration(Runnable closeAction) {
        this.closeAction = Objects.requireNonNull(closeAction, "closeAction");
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            closeAction.run();
        }
    }
}
