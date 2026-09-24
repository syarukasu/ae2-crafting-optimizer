package com.ae2vm.addon.nativeengine;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class NativeVmTimingTest {
    @Test void separatesQueueActionAndWorkerWallWithoutDoubleCounting() {
        var clock = new AtomicLong(-100_000_000L);
        var timing = new NativeVmTiming(clock::get);
        try (var span = timing.phase(NativeVmTiming.Phase.VM)) {
            clock.addAndGet(10_000_000);
            long submitted = clock.get();
            clock.addAndGet(30_000_000);
            long started = clock.get();
            clock.addAndGet(5_000_000);
            timing.serverFinished(submitted, started, clock.get());
            clock.addAndGet(2_000_000);
            timing.unblocked(submitted);
            clock.addAndGet(20_000_000);
        }
        String result = timing.summary();
        assertTrue(result.contains("vmMs=67 vmActiveMs=30"), result);
        assertTrue(result.contains("serverQueueMs=30 serverWorkMs=5 handoffBlockedMs=37 handoffs=1"), result);
    }

    @Test void accumulatesRetriesClosesOnceAndKeepsPhasesDistinct() {
        var clock = new AtomicLong();
        var timing = new NativeVmTiming(clock::get);
        for (int i = 0; i < 2; i++) {
            var span = timing.phase(NativeVmTiming.Phase.PREPARE);
            clock.addAndGet(2_000_000);
            span.close(); span.close();
        }
        try (var span = timing.phase(NativeVmTiming.Phase.VALIDATE)) { clock.addAndGet(3_000_000); }
        timing.execution(4, 5, 6); timing.execution(1, 2, 3);
        assertTrue(timing.summary().contains("prepareMs=4 vmMs=0 vmActiveMs=0 validateMs=3"));
        assertTrue(timing.summary().endsWith("stateCopies=5 branchTrials=7 branchEntries=9"));
    }
}
