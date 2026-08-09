package com.syaru.ae2craftingoptimizer.api;

import com.syaru.ae2craftingoptimizer.api.batch.v2.BatchTransactionRecord;

/** Compile-only external adapter fixture: it imports no ACO implementation package. */
public final class ExternalBatchRecoveryFixture {
    private ExternalBatchRecoveryFixture() {
    }

    public static String canonicalDigest(BatchTransactionRecord record) {
        return record.payloadDigest();
    }
}
