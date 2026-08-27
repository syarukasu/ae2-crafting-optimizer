package com.syaru.ae2craftingoptimizer.optimization;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExecutionDiagnosticsTest {
    @BeforeEach
    void resetBeforeTest() {
        OptimizationMetrics.reset();
    }

    @AfterEach
    void resetAfterTest() {
        OptimizationMetrics.reset();
    }

    @Test
    void reportsExecutionWaveTimingAndTransactionalProbeBreakdown() {
        OptimizationMetrics.recordSequentialInstantWave(128, 96, 250_000L);
        OptimizationMetrics.recordSequentialInstantBudgetStop();
        OptimizationMetrics.recordTransactionalV2Probe();
        OptimizationMetrics.recordTransactionalV2NoAdapterBypass();
        OptimizationMetrics.recordTransactionalV2TasksScanned(7);
        OptimizationMetrics.recordTransactionalV2RouteMatch();
        OptimizationMetrics.recordTransactionalV2StandardFallback();
        OptimizationMetrics.recordTransactionalV2PatternMetadataCacheHit();
        OptimizationMetrics.recordTransactionalV2PatternMetadataCacheMiss();
        OptimizationMetrics.recordTransactionalV2PatternMetadataUnstable();

        var lines = OptimizationMetrics.summaryLines();
        assertTrue(lines.stream().anyMatch(line -> line.equals(
                "Sequential Instant: 1 wave(s), 96/128 operation(s), 1 budget stop(s), max wave 250 us, average wave 250 us")));
        assertTrue(lines.stream().anyMatch(line -> line.equals(
                "Transactional V2 probes: 1 call(s), 1 no-adapter bypass(es), 7 task(s) scanned, 1 route match(es), 1 standard fallback(s)")));
        assertTrue(lines.stream().anyMatch(line -> line.equals(
                "Transactional V2 pattern metadata: 1 cache hit(s), 1 miss(es), 1 unstable compile(s)")));
    }
}
