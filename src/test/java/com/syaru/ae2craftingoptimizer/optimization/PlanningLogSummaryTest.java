package com.syaru.ae2craftingoptimizer.optimization;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class PlanningLogSummaryTest {
    @Test
    void cacheOnlyActivityIsVisibleWithoutPretendingAnotherCalculationRan() {
        var s = new PlanningLogSummary(100);
        assertFalse(s.isDue(99));
        assertTrue(s.isDue(100));
        var report = s.poll(100, true);
        assertEquals(0, report.started());
        assertEquals(0, report.completed());
        assertNull(s.poll(200, false));
    }

    @Test
    void reportsOnlyAtIntervalAndSuppressesIdleWindows() {
        var s = new PlanningLogSummary(100);
        s.reset(0);
        assertNull(s.poll(100));
        long token = s.begin();
        s.finish(token, "compiled-strict", true, false, 2_000_000);
        assertNull(s.poll(199));
        var report = s.poll(200);
        assertEquals(1, report.started());
        assertEquals(1, report.compiled());
        assertEquals(1, report.exact());
        assertEquals(0, report.active());
        assertEquals(2, report.averageMs());
        assertNull(s.poll(300));
    }

    @Test
    void activeCalculationRemainsVisibleAcrossWindows() {
        var s = new PlanningLogSummary(100);
        long token = s.begin();
        assertEquals(1, s.poll(100).active());
        assertEquals(1, s.poll(200).active());
        s.finish(token, "ae2-snapshot", false, true, 10_000_000);
        var report = s.poll(300);
        assertEquals(0, report.started());
        assertEquals(1, report.completed());
        assertEquals(1, report.snapshot());
        assertEquals(1, report.missing());
    }

    @Test
    void resetRejectsOldWorkerCompletion() {
        var s = new PlanningLogSummary(100);
        long old = s.begin();
        s.reset(10);
        long current = s.begin();
        s.finish(old, "compiled-strict", true, false, 10);
        s.finish(current, "ae2-standard", false, false, 1_000_000);
        var report = s.poll(110);
        assertEquals(1, report.standard());
        assertEquals(0, report.compiled());
        assertEquals(0, report.active());
    }

    @Test
    void abortedIsNotMissingOrSuccessfullyCompleted() {
        var s = new PlanningLogSummary(100);
        s.finish(s.begin(), null, false, false, 100);
        var report = s.poll(100);
        assertEquals(1, report.aborted());
        assertEquals(0, report.completed());
        assertEquals(0, report.missing());
        assertEquals(0, report.averageMs());
        assertFalse(report.line().contains("speedup"));
    }

    @Test
    void unknownEngineIsNotCountedAsCompiledOrVm() {
        var s = new PlanningLogSummary(100);
        s.finish(s.begin(), "external", false, false, 1);
        var report = s.poll(100);
        assertEquals(1, report.other());
        assertEquals(0, report.compiled());
    }

    @Test
    void concurrentCompletionsAreNotLost() throws Exception {
        var s = new PlanningLogSummary(100);
        var pool = Executors.newFixedThreadPool(4);
        try {
            var futures = new ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 1000; i++) {
                futures.add(pool.submit(() -> s.finish(s.begin(), "compiled-strict", false, false, 3_000_000)));
            }
            for (var f : futures) f.get();
            var report = s.poll(100);
            assertEquals(1000, report.completed());
            assertEquals(1000, report.started());
            assertEquals(0, report.active());
            assertEquals(3, report.averageMs());
            assertEquals(3, report.maxMs());
        } finally {
            pool.shutdownNow();
        }
    }
}
