package com.ae2vm.addon.nativeengine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

class NativeVmDiagnosticsTest {
    private final Logger log = mock(Logger.class);
    private final AtomicLong clock = new AtomicLong();
    private final NativeVmDiagnostics diagnostics = new NativeVmDiagnostics(log, clock::get, false);

    @Test void routineOrdersDoNotSpamEvenWhenDebugIsEnabled() {
        when(log.isDebugEnabled()).thenReturn(true);
        for (int i = 0; i < 10_000; i++) {
            diagnostics.started();
            diagnostics.completed(i, "bio_fuel", 120, 25, false);
        }
        assertFalse(diagnostics.verbose());
        verifyNoInteractions(log);
    }

    @Test void summarizesAllOrdersOnceAndResetsOnlyTheWindowCounters() {
        for (int i = 0; i < 10_000; i++) {
            diagnostics.started();
            diagnostics.completed(i, "bio_fuel", 120, 25, i % 2 == 0);
        }
        seconds(60);
        diagnostics.running(0, "test", 0, 0);
        assertArrayEquals(new Object[] {60_000L, 10_000L, 10_000L, 5_000L, 0L, 0L, 0L, 0L, 25L, 25L},
                events("summary").get(0));
        for (int i = 0; i < 100; i++) diagnostics.running(0, "test", 0, 0);
        assertEquals(1, events("summary").size());
        diagnostics.started();
        diagnostics.completed(0, "test", 1, 42, false);
        seconds(120);
        diagnostics.running(0, "test", 0, 0);
        assertArrayEquals(new Object[] {60_000L, 1L, 1L, 0L, 0L, 0L, 0L, 0L, 42L, 42L},
                events("summary").get(1));
    }

    @Test void slowCompletionLimitIsGlobalNotPerOrderAndRetainsSuppressedCounts() {
        diagnostics.completed(1, "test", 1, 4_999, false);
        assertTrue(events("slow_calculation").isEmpty());
        diagnostics.completed(2, "test", 1, 5_000, false);
        diagnostics.completed(3, "other", 2, 6_000, true);
        seconds(29);
        diagnostics.completed(4, "other", 2, 6_000, false);
        assertEquals(1, events("slow_calculation").size());
        seconds(30);
        diagnostics.completed(5, "test", 1, 7_000, false);
        assertEquals(2, events("slow_calculation").size());
        seconds(60);
        diagnostics.running(0, "test", 0, 0);
        var summary = events("summary").get(0);
        assertEquals(4L, summary[7]);
        assertEquals(7_000L, summary[9]);
    }

    @Test void runningLimitIsGlobalAndSeparateFromSlowCompletions() {
        diagnostics.running(1, "test", 0, 4_999);
        verifyNoInteractions(log);
        diagnostics.running(1, "test", 1, 5_000);
        diagnostics.running(2, "other", 2, 5_000);
        seconds(29);
        diagnostics.running(3, "other", 3, 6_000);
        assertEquals(1, events("running").size());
        seconds(30);
        diagnostics.running(4, "other", 4, 7_000);
        diagnostics.completed(4, "other", 1, 7_000, false);
        assertEquals(2, events("running").size());
        assertEquals(1, events("slow_calculation").size());
    }

    @Test void errorsKeepTheirCausesWhileRoutineCancellationAndRecaptureOnlyAccumulate() {
        diagnostics.cancelled();
        diagnostics.recaptured();
        verifyNoInteractions(log);
        var cause = new IllegalStateException("accounting mismatch");
        diagnostics.failed(1, "test", 1, cause);
        diagnostics.failed(2, "test", 1, cause);
        assertEquals(2, events("failed").size());
        assertSame(cause, events("failed").get(0)[3]);
        seconds(60);
        diagnostics.running(0, "test", 0, 0);
        var summary = events("summary").get(0);
        assertEquals(1L, summary[4]);
        assertEquals(2L, summary[5]);
        assertEquals(1L, summary[6]);
    }

    @Test void concurrentOrdersCannotBypassTheGlobalBudgetOrLoseCounts() throws Exception {
        var pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (int i = 0; i < 4; i++) tasks.add(pool.submit(() -> {
                for (int j = 0; j < 1_000; j++) {
                    diagnostics.started();
                    diagnostics.running(j, "test", 1, 5_000);
                    diagnostics.completed(j, "test", 1, 5_000, false);
                }
            }));
            for (var task : tasks) task.get(10, TimeUnit.SECONDS);
            assertEquals(1, events("running").size());
            assertEquals(1, events("slow_calculation").size());
            seconds(60);
            diagnostics.running(0, "test", 0, 0);
            var summary = events("summary").get(0);
            assertEquals(4_000L, summary[1]);
            assertEquals(4_000L, summary[2]);
            assertEquals(4_000L, summary[7]);
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test void verboseTracesRequireExplicitOptIn() {
        assertFalse(diagnostics.verbose());
        assertTrue(new NativeVmDiagnostics(log, clock::get, true).verbose());
    }

    @Test void recentSamplesAreBoundedExactImmutableAndDoNotLog() {
        var quantity = java.math.BigInteger.TEN.pow(64);
        for (int i = 0; i < 100; i++) diagnostics.record(new NativeVm.CalculationSample(i,
                "emextras:supreme_quantum_control_circuit", quantity, i, i == 99 ? "failed" : "ready"));
        var samples = diagnostics.recent();
        assertEquals(64, samples.size());
        assertEquals(36, samples.get(0).order());
        assertEquals(quantity, samples.get(63).requested());
        assertEquals("failed", samples.get(63).status());
        assertThrows(UnsupportedOperationException.class, samples::clear);
        diagnostics.record(new NativeVm.CalculationSample(100, "other", quantity, 1, "cancelled"));
        assertEquals(36, samples.get(0).order(), "Caller has an immutable snapshot");
        verifyNoInteractions(log);
    }

    @Test void monotonicClockMayStartNegative() {
        var negativeClock = new AtomicLong(-TimeUnit.SECONDS.toNanos(120));
        var negative = new NativeVmDiagnostics(log, negativeClock::get, false);
        negative.started();
        negative.completed(1, "test", 1, 5_000, false);
        assertEquals(1, events("slow_calculation").size());
        negativeClock.addAndGet(TimeUnit.SECONDS.toNanos(60));
        negative.running(0, "test", 0, 0);
        assertEquals(1, events("summary").size());
    }

    private void seconds(long value) { clock.set(TimeUnit.SECONDS.toNanos(value)); }

    private List<Object[]> events(String event) {
        List<Object[]> result = new ArrayList<>();
        mockingDetails(log).getInvocations().forEach(call -> {
            var args = call.getRawArguments();
            if (args.length == 2 && args[0] instanceof String message
                    && message.startsWith("AE2-VM event=" + event + " ")) {
                result.add((Object[]) args[1]);
            }
        });
        return result;
    }
}
