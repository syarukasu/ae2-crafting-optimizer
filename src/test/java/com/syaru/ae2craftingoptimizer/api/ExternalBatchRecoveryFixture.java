package com.syaru.ae2craftingoptimizer.api;

import com.syaru.ae2craftingoptimizer.api.batch.v2.BatchTransactionRecord;

/** 外部Adapter相当のcompile fixture。ACO実装packageを一切importしない。 */
public final class ExternalBatchRecoveryFixture {
    private ExternalBatchRecoveryFixture() {
    }

    public static String canonicalDigest(BatchTransactionRecord record) {
        return record.payloadDigest();
    }
}
