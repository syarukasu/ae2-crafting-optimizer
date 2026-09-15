package com.syaru.ae2craftingoptimizer.optimization;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TransactionalBatchTargetGuardTest {
    @AfterEach
    void clear() {
        TransactionalBatchTargetGuard.clear();
    }

    @Test
    void targetCanOnlyBeClaimedOncePerScopeAndTick() {
        Object firstScope = new Object();
        Object secondScope = new Object();

        assertTrue(TransactionalBatchTargetGuard.tryClaim(firstScope, 42L, 10L));
        assertFalse(TransactionalBatchTargetGuard.tryClaim(firstScope, 42L, 10L));
        assertTrue(TransactionalBatchTargetGuard.tryClaim(firstScope, 43L, 10L));
        assertTrue(TransactionalBatchTargetGuard.tryClaim(secondScope, 42L, 10L));
        assertTrue(TransactionalBatchTargetGuard.tryClaim(firstScope, 42L, 11L));
    }
}
