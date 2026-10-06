package com.syaru.ae2craftingoptimizer.optimization;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SequentialInstantDispatcherTest {
    @Test
    void repeatedSameTickCallsPreserveTheFullNativeRequest() {
        Object owner = new Object() {
            @Override public int hashCode() {
                throw new AssertionError("Dispatch must not keep per-owner state");
            }
        };
        var calls = new AtomicInteger();
        for (int requested : new int[] {1, 10, 65, 1024, 65537, 1048576, Integer.MAX_VALUE}) {
            for (int repeat = 0; repeat < 3; repeat++) {
                assertEquals(requested, SequentialInstantDispatcher.executeWave(owner, requested, null, 7, actual -> {
                    calls.incrementAndGet();
                    assertEquals(requested, actual);
                    return actual;
                }));
            }
        }
        assertEquals(21, calls.get());
    }

    @Test
    void nativeBlockingAndResumeDoNotAcquireAnAcoCooldown() {
        var calls = new AtomicInteger();
        var requested = 1_048_576;
        SequentialInstantDispatcher.CraftingWave nativeDispatch =
                actual -> calls.getAndIncrement() == 0 ? 0 : actual;
        assertEquals(0, SequentialInstantDispatcher.executeWave(this, requested, null, 7, nativeDispatch));
        assertEquals(requested, SequentialInstantDispatcher.executeWave(this, requested, null, 7, nativeDispatch));
        assertEquals(2, calls.get());
    }

    @Test
    void nativeFailurePropagatesWithoutRetryOrPenalizingTheNextCall() {
        var failure = new IllegalStateException("provider failure");
        var calls = new AtomicInteger();
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> SequentialInstantDispatcher.executeWave(this, 1024, null, 7, actual -> {
                    calls.incrementAndGet();
                    throw failure;
                })));
        assertEquals(1, calls.get());
        assertEquals(1024, SequentialInstantDispatcher.executeWave(this, 1024, null, 7, actual -> actual));
    }

    @Test
    void emptyLegacyRequestsDoNotInvokeNativeExecution() {
        for (int request : new int[] {Integer.MIN_VALUE, -1, 0}) {
            assertEquals(0, SequentialInstantDispatcher.executeWave(this, request, null, 7, actual -> {
                throw new AssertionError("No work");
            }));
        }
        assertEquals(0, SequentialInstantDispatcher.executeWave(this, 1, null, 7, null));
        SequentialInstantDispatcher.clear();
    }
}
