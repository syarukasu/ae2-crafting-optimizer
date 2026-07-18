package com.syaru.ae2craftingoptimizer.optimization;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class NativeBatchTargetGuardTest {
    @AfterEach
    void clear() {
        NativeBatchTargetGuard.clear();
    }

    @Test
    void targetCanOnlyBeClaimedOncePerScopeAndTick() {
        Object firstScope = new Object();
        Object secondScope = new Object();

        assertTrue(NativeBatchTargetGuard.tryClaim(firstScope, 42L, 10L));
        assertFalse(NativeBatchTargetGuard.tryClaim(firstScope, 42L, 10L));
        assertTrue(NativeBatchTargetGuard.tryClaim(firstScope, 43L, 10L));
        assertTrue(NativeBatchTargetGuard.tryClaim(secondScope, 42L, 10L));
        assertTrue(NativeBatchTargetGuard.tryClaim(firstScope, 42L, 11L));
    }
}
