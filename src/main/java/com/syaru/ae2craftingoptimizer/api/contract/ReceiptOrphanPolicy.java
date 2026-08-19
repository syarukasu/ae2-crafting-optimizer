package com.syaru.ae2craftingoptimizer.api.contract;

/** Fail-closed orphan policy. Unknown or incomplete proof never authorizes deletion. */
public final class ReceiptOrphanPolicy {
    private ReceiptOrphanPolicy() {
    }

    public static boolean mayForget(LiveTransactionProof proof) {
        return proof != null && proof.result() == LiveTransactionState.ABSENT_CONFIRMED;
    }
}
