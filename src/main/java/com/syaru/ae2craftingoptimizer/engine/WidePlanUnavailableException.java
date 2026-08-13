package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.stacks.AEKey;

/**
 * wide計画を正確に作れず、AE2のoverflowするlong計算へ戻してはいけないことを示す例外。
 * 呼出側は通常のlong計画として再実行せず、診断または再試行可能な失敗として扱う。
 */
public final class WidePlanUnavailableException extends RuntimeException {
    private final AEKey output;

    public WidePlanUnavailableException(AEKey output, String message) {
        super(message);
        this.output = output;
    }

    public WidePlanUnavailableException(AEKey output, String message, Throwable cause) {
        super(message, cause);
        this.output = output;
    }

    public AEKey output() {
        return output;
    }
}
