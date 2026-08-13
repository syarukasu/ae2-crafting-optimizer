package com.syaru.ae2craftingoptimizer.api.batch.v2;

import com.syaru.ae2craftingoptimizer.api.batch.PatternBatchContext;
import com.syaru.ae2craftingoptimizer.batch.NativePatternBatchSupport;
import java.util.Objects;

/** ACO Journalと外部Adapterが共有する、Pattern Batchの正規識別API。 */
public final class PatternBatchIdentity {
    private PatternBatchIdentity() {
    }

    /**
     * ACOが永続Journalへ保存する値と完全に同じFingerprintを返す。
     * 外部Adapterは独自に同じ符号化を再実装しないこと。
     */
    public static String canonicalFingerprint(PatternBatchContext context) {
        return NativePatternBatchSupport.fingerprint(
                Objects.requireNonNull(context, "context"));
    }
}
