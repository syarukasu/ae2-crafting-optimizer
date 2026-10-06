package com.ae2vm.addon.nativeengine;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class NativeVmTimingTest {
    @Test void categoriesPreserveTotalCountsAndQueueTime() {
        var timing = new NativeVmTiming(() -> 0L);
        timing.serverFinished(NativeVmTiming.ServerCall.STOCK, 0, 3_000_000, 4_000_000);
        timing.serverFinished(NativeVmTiming.ServerCall.INPUTS, 0, 5_000_000, 7_000_000);
        timing.serverFinished(NativeVmTiming.ServerCall.INPUTS, 0, 7_000_000, 10_000_000);
        timing.serverFinished(0, 2_000_000, 6_000_000);
        String result = timing.summary();
        assertTrue(result.contains("serverQueueMs=17 serverWorkMs=10"), result);
        assertTrue(result.contains("handoffs=4"), result);
        assertTrue(result.contains("serverCalls={STOCK:1,INPUTS:2,OTHER:1}"), result);
        assertTrue(result.contains("serverQueueByKindMs={STOCK:3,INPUTS:12,OTHER:2}"), result);
    }

    @Test void capturedPatternTypesHaveBoundedNamesAndCardinality() {
        var timing = new NativeVmTiming(() -> 0L);
        timing.patternType("native.Type"); timing.patternType("native.Type");
        timing.patternType("x".repeat(512));
        for (int i = 0; i < 100; i++) timing.patternType("custom.Type" + i);
        String result = timing.summary();
        assertTrue(result.contains("patternTypes={native.Type:2,"), result);
        assertTrue(result.contains("otherPatternTypes=86"), result);
        assertFalse(result.contains("x".repeat(121)), result);
        assertTrue(result.length() < 1500, result);
    }

    @Test void liveRemainderSummaryIsBoundedAndKeepsNullResults() {
        var timing = new NativeVmTiming(() -> 0L);
        timing.remainderRead("custom.Input", "mod:tool", true);
        timing.remainderRead("custom.Input", "mod:tool", false);
        for (int i = 0; i < 20; i++)
            timing.remainderRead("custom.Input", "mod:item_" + i, true);
        String result = timing.summary();
        assertTrue(result.contains("remainderCalls=22"), result);
        assertTrue(result.contains("remainderNull=21"), result);
        assertTrue(result.contains("custom.Input/mod:tool:2"), result);
        assertTrue(result.contains("otherRemainderCalls=9"), result);
        assertTrue(result.length() < 1500, result);
    }


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
        timing.execution(4, 5, 6); timing.execution(1, 2, 3, 8);
        assertTrue(timing.summary().contains("prepareMs=4 vmMs=0 vmActiveMs=0"));
        assertTrue(timing.summary().contains("validateMs=3"));
        assertTrue(timing.summary().endsWith("stateCopies=5 branchTrials=7 branchEntries=9 aggregatedBlocks=8"));
    }

    @Test void ae2ScheduledWallTimeIsNotReportedAsActiveCpuTime() {
        var timing = new NativeVmTiming();
        timing.ae2Scheduled();
        assertTrue(timing.summary().contains("vmActiveMs=-1"));
        assertTrue(timing.summary().contains("scheduler=ae2"));
        assertTrue(timing.summary().contains("handoffs=0"));
    }
}
