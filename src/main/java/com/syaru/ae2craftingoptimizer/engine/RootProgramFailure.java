package com.syaru.ae2craftingoptimizer.engine;

/**
 * Root Programをコンパイルできなかった理由。
 *
 * <p>「プレイヤーが構成を直せば解ける構造的な理由」と「その世代のSnapshotが揃わなかった
 * 一時的な理由」を必ず区別する。前者は同じ世代で何度試しても同じ結果になるが、後者は
 * Patternのコンパイル失敗や世代のずれが原因なので、取り直せば解けることがある。</p>
 */
public enum RootProgramFailure {
    /** 失敗していない。 */
    NONE,
    /** ルートから到達する枝が強連結成分に属する、または並べ直せない循環がある。 */
    CYCLE,
    /** 一つの出力へ複数のPatternが登録されており、数式経路では選べない。 */
    MULTIPLE_PRODUCERS,
    /** 副産物・複数出力・非正の出力量など、単一路線として扱えない出力形状のPatternが含まれる。 */
    MULTIPLE_OUTPUTS,
    /** ノード数または入力辺数が固定上限を超えた。 */
    PROGRAM_TOO_LARGE,
    /** この世代のSnapshotで一部のPatternをコンパイルできず、到達キーが不完全だった。 */
    INCOMPLETE_PATTERN_SNAPSHOT,
    /** AE2が作成可能と扱うルートが、この世代のSnapshotのcraftablesに存在しなかった。 */
    MISSING_FROM_SNAPSHOT;

    /**
     * 同じ世代のまま取り直しても結果が変わらない、構成側の理由かどうか。
     */
    public boolean structural() {
        return this == CYCLE
                || this == MULTIPLE_PRODUCERS
                || this == MULTIPLE_OUTPUTS
                || this == PROGRAM_TOO_LARGE;
    }

    /**
     * Snapshotを取り直せば解ける可能性がある理由かどうか。
     *
     * <p>プレイヤーが構成を直しても消えないため、曖昧と同じ扱いで報告してはいけない。</p>
     */
    public boolean snapshotShaped() {
        return this == INCOMPLETE_PATTERN_SNAPSHOT || this == MISSING_FROM_SNAPSHOT;
    }
}
