package com.syaru.ae2craftingoptimizer.optimization;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SequentialInstantDispatcherTest {
    @Test
    void coldStartUsesProbeAsOneWaveNotTickLimit() {
        assertEquals(65_536, SequentialInstantDispatcher.calculateWaveOperations(
                264_192, 4_000_000L, 0L, 65_536, 65_536));
    }

    @Test
    void measuredWaveUsesSeventyFivePercentOfRemainingBudget() {
        assertEquals(3_000, SequentialInstantDispatcher.calculateWaveOperations(
                264_192, 4_000_000L, 1_000L, 256, 65_536));
    }

    @Test
    void waveNeverExceedsRequestedOrConfiguredMaximum() {
        assertEquals(40, SequentialInstantDispatcher.calculateWaveOperations(
                40, 4_000_000L, 1L, 256, 65_536));
        assertEquals(65_536, SequentialInstantDispatcher.calculateWaveOperations(
                264_192, 4_000_000L, 1L, 256, 65_536));
    }

    @Test
    void exhaustedBudgetProducesNoWave() {
        assertEquals(0, SequentialInstantDispatcher.calculateWaveOperations(
                264_192, 0L, 1_000L, 256, 65_536));
    }
}
