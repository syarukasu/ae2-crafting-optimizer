package com.syaru.ae2craftingoptimizer.engine;

/** Root Programをコンパイルできなかった正確な理由。 */
public enum RootProgramFailure {
    NONE,
    CYCLE,
    MULTIPLE_PRODUCERS,
    MULTIPLE_OUTPUTS,
    PROGRAM_TOO_LARGE,
    INCOMPLETE_PATTERN_SNAPSHOT,
    MISSING_FROM_SNAPSHOT;

    /** 同じ世代で再構築しても変化しない、レシピ構造由来の失敗かを返す。 */
    public boolean structural() {
        return this == CYCLE
                || this == MULTIPLE_PRODUCERS
                || this == MULTIPLE_OUTPUTS
                || this == PROGRAM_TOO_LARGE;
    }

    /** Snapshotを取り直せば解消する可能性がある、一時的な失敗かを返す。 */
    public boolean snapshotShaped() {
        return this == INCOMPLETE_PATTERN_SNAPSHOT || this == MISSING_FROM_SNAPSHOT;
    }
}
