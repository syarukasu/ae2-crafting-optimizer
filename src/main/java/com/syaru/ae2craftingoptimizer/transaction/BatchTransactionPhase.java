package com.syaru.ae2craftingoptimizer.transaction;

import java.util.EnumSet;
import java.util.Set;

public enum BatchTransactionPhase {
    PREPARED,
    TARGET_ACCEPTED,
    ACCOUNTED,
    ROLLED_BACK,
    RECONCILIATION_REQUIRED,
    QUARANTINED;

    public boolean terminal() {
        return this == ACCOUNTED || this == ROLLED_BACK || this == QUARANTINED;
    }

    public boolean canTransitionTo(BatchTransactionPhase next) {
        if (next == this) {
            return true;
        }
        return switch (this) {
            case PREPARED -> Set.of(TARGET_ACCEPTED, ROLLED_BACK, RECONCILIATION_REQUIRED, QUARANTINED)
                    .contains(next);
            case TARGET_ACCEPTED -> Set.of(ACCOUNTED, RECONCILIATION_REQUIRED, QUARANTINED).contains(next);
            case RECONCILIATION_REQUIRED -> EnumSet.of(
                    TARGET_ACCEPTED, ACCOUNTED, ROLLED_BACK, QUARANTINED).contains(next);
            case ACCOUNTED, ROLLED_BACK, QUARANTINED -> false;
        };
    }
}
