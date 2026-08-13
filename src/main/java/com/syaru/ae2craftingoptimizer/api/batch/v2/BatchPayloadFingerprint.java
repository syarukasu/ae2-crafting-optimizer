package com.syaru.ae2craftingoptimizer.api.batch.v2;

import appeng.api.stacks.GenericStack;
import com.syaru.ae2craftingoptimizer.util.StableFingerprint;

/** 取引IDとは独立した、入力・期待出力・実行数の決定的Fingerprint。 */
public final class BatchPayloadFingerprint {
    private BatchPayloadFingerprint() {
    }

    public static String of(PreparedPatternBatch prepared) {
        return of(prepared.offeredExecutions(), prepared.aggregateInputs(), prepared.expectedOutputs());
    }

    public static String of(BatchTransactionRecord record) {
        if (record == null) {
            throw new NullPointerException("record");
        }
        return of(record.offeredExecutions(), record.extractedInputs(), record.expectedOutputs());
    }

    /**
     * ACO 1.5.x旧ABI向けの互換入口。
     * 新しい外部連携は公開Recordのoverloadを使用する。
     */
    @Deprecated(forRemoval = false)
    public static String of(
            com.syaru.ae2craftingoptimizer.transaction.BatchTransactionRecord record) {
        if (record == null) {
            throw new NullPointerException("record");
        }
        return record.toPublicView().payloadDigest();
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
                    .append(stack.what().toTagGeneric(com.syaru.ae2craftingoptimizer.lifecycle.ACORegistryAccess.require()))
                    .append('@')
                    .append(stack.amount());
        }
    }
}
