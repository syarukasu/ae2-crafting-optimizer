package com.syaru.ae2craftingoptimizer.optimization;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlanningConfigurationRevisionTrackerTest {
    @Test
    void invalidationRejectsCapturesFromThePreviousConfiguration() {
        long captured = PlanningConfigurationRevisionTracker.current();

        PlanningConfigurationRevisionTracker.invalidate();

        assertFalse(PlanningConfigurationRevisionTracker.isCurrent(captured));
        assertTrue(PlanningConfigurationRevisionTracker.isCurrent(
                PlanningConfigurationRevisionTracker.current()));
    }
}
