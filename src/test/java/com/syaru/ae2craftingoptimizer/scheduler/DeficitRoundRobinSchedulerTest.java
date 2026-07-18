package com.syaru.ae2craftingoptimizer.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class DeficitRoundRobinSchedulerTest {
    @Test
    void preservesReservationsForJobsCalledLaterInTheTick() {
        DeficitRoundRobinScheduler scheduler = new DeficitRoundRobinScheduler();
        FairSchedulerPersistentState first = state(1L);
        FairSchedulerPersistentState second = state(2L);

        // Discover both jobs. The first discovery tick may be consumed by the first job.
        scheduler.grant(first, 1_000, 1L, 128, 64, 4_000_000L);
        scheduler.grant(second, 1_000, 1L, 128, 64, 4_000_000L);

        int firstGrant = scheduler.grant(first, 1_000, 2L, 128, 64, 4_000_000L);
        int secondGrant = scheduler.grant(second, 1_000, 2L, 128, 64, 4_000_000L);

        assertEquals(64, firstGrant);
        assertEquals(64, secondGrant);
        assertEquals(0, scheduler.remainingOperations());
    }

    @Test
    void rotatesAQuantumWhenBudgetCannotServeEveryJobInOneTick() {
        DeficitRoundRobinScheduler scheduler = new DeficitRoundRobinScheduler();
        FairSchedulerPersistentState[] states = {
                state(1L), state(2L), state(3L)
        };
        long[] totals = new long[states.length];

        for (long tick = 1L; tick <= 8L; tick++) {
            int consumed = 0;
            for (int index = 0; index < states.length; index++) {
                int grant = scheduler.grant(states[index], 1_000, tick, 128, 64, 4_000_000L);
                totals[index] += grant;
                consumed += grant;
            }
            assertTrue(consumed <= 128);
        }

        for (long total : totals) {
            assertTrue(total > 0L, "every continuously runnable job must eventually receive service");
        }
        long minimum = Math.min(totals[0], Math.min(totals[1], totals[2]));
        long maximum = Math.max(totals[0], Math.max(totals[1], totals[2]));
        assertTrue(maximum - minimum <= 128L, Arrays.toString(totals));
    }

    @Test
    void stopsFurtherGrantsAfterTheWallClockBudgetIsConsumed() {
        DeficitRoundRobinScheduler scheduler = new DeficitRoundRobinScheduler();
        FairSchedulerPersistentState first = state(1L);
        FairSchedulerPersistentState second = state(2L);
        scheduler.grant(first, 1_000, 1L, 128, 64, 1_000L);
        scheduler.grant(second, 1_000, 1L, 128, 64, 1_000L);

        scheduler.grant(first, 1_000, 2L, 128, 64, 1_000L);
        scheduler.recordElapsed(2L, 1_000L);

        assertEquals(0, scheduler.grant(second, 1_000, 2L, 128, 64, 1_000L));
    }

    private static FairSchedulerPersistentState state(long leastSignificantBits) {
        return new FairSchedulerPersistentState(new UUID(0L, leastSignificantBits));
    }
}
