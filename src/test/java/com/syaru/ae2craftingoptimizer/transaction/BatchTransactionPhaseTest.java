package com.syaru.ae2craftingoptimizer.transaction;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BatchTransactionPhaseTest {
    @Test
    void onlyAllowsForwardOrRecoveryTransitions() {
        assertTrue(BatchTransactionPhase.PREPARED.canTransitionTo(BatchTransactionPhase.TARGET_ACCEPTED));
        assertTrue(BatchTransactionPhase.TARGET_ACCEPTED.canTransitionTo(BatchTransactionPhase.ACCOUNTED));
        assertTrue(BatchTransactionPhase.RECONCILIATION_REQUIRED.canTransitionTo(BatchTransactionPhase.ROLLED_BACK));
        assertFalse(BatchTransactionPhase.TARGET_ACCEPTED.canTransitionTo(BatchTransactionPhase.ROLLED_BACK));
        assertFalse(BatchTransactionPhase.ACCOUNTED.canTransitionTo(BatchTransactionPhase.PREPARED));
    }
}
