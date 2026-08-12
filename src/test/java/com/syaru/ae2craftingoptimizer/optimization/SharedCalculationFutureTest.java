package com.syaru.ae2craftingoptimizer.optimization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
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
    }
}
