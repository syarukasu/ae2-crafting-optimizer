package com.syaru.ae2craftingoptimizer.api.batch.v2;

import appeng.api.stacks.GenericStack;
import com.syaru.ae2craftingoptimizer.util.StableFingerprint;
import com.syaru.ae2craftingoptimizer.transaction.BatchTransactionRecord;

/** 取引IDとは独立した、入力・期待出力・実行数の決定的Fingerprint。 */
public final class BatchPayloadFingerprint {
    private BatchPayloadFingerprint() {
    }

    public static String of(PreparedPatternBatch prepared) {
        return of(prepared.offeredExecutions(), prepared.aggregateInputs(), prepared.expectedOutputs());
    }

    public static String of(BatchTransactionRecord record) {
        return of(record.offeredExecutions(), record.extractedInputs(), record.expectedOutputs());
    }

    private static String of(
            long executions,
            Iterable<GenericStack> inputs,
            Iterable<GenericStack> outputs) {
        StringBuilder value = new StringBuilder(256);
        value.append("executions=").append(executions);
        append(value, "inputs", inputs);
        append(value, "outputs", outputs);
        return StableFingerprint.sha256(value);
    }

    private static void append(StringBuilder target, String name, Iterable<GenericStack> stacks) {
        target.append('|').append(name);
        for (GenericStack stack : stacks) {
            target.append('|')
                    .append(stack.what().toTagGeneric())
                    .append('@')
                    .append(stack.amount());
        }
    }
}
