package com.syaru.ae2craftingoptimizer.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class FairSchedulerPersistentStateTest {
    @Test
    void roundTripsIdentityDeficitAndCursorThroughNbt() {
        UUID id = UUID.randomUUID();
        FairSchedulerPersistentState state = new FairSchedulerPersistentState(id);
        state.credit(512L, 4_096L);
        state.consume(128L);
        state.updateCursor(42L);

        FairSchedulerPersistentState restored = new FairSchedulerPersistentState();
        restored.load(state.save());

        assertEquals(id, restored.jobId());
        assertEquals(384L, restored.deficit());
        assertEquals(42L, restored.cursor());
    }
}
