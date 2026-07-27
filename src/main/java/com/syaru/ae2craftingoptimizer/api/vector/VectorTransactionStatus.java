package com.syaru.ae2craftingoptimizer.api.vector;

/** Exact Vector Transactionの永続状態。 */
public enum VectorTransactionStatus {
    PREPARED,
    INPUTS_EXTRACTING,
    INPUTS_ESCROWED,
    RUNNING,
    PAUSED_ENERGY,
    OUTPUT_PENDING,
    ACCOUNTING,
    COMPLETED,
    CANCELLED,
    QUARANTINED;

    public boolean terminal() {
        return this == COMPLETED || this == CANCELLED || this == QUARANTINED;
    }

    public boolean ownsInputs() {
        return switch (this) {
            case INPUTS_EXTRACTING,
                    INPUTS_ESCROWED,
                    RUNNING,
                    PAUSED_ENERGY,
                    OUTPUT_PENDING,
                    ACCOUNTING,
                    COMPLETED,
                    QUARANTINED -> true;
            case PREPARED, CANCELLED -> false;
        };
    }
}
