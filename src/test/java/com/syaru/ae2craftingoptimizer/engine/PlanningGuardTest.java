package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class PlanningGuardTest {
    @Test
    void cancellationIsObservedAtTheNextCheckpoint() {
        PlanningCancellationToken token = new PlanningCancellationToken();
        assertDoesNotThrow(() -> token.checkpoint(1));
        token.cancel();
        assertThrows(PlanningCancelledException.class, () -> token.checkpoint(2));
    }

    @Test
    void generationChangeInvalidatesTheSnapshot() {
        AtomicLong patterns = new AtomicLong(1L);
        AtomicLong inventory = new AtomicLong(2L);
        AtomicLong recipes = new AtomicLong(3L);
        PlanningGenerationSnapshot snapshot = new PlanningGenerationSnapshot(1L, 2L, 3L);
        PlanningGuard guard = snapshot.guard(
                patterns::get,
                inventory::get,
                recipes::get,
                new PlanningCancellationToken());

        assertDoesNotThrow(() -> guard.checkpoint(1));
        inventory.incrementAndGet();
        assertThrows(StalePlanningSnapshotException.class, () -> guard.checkpoint(2));
    }
}
