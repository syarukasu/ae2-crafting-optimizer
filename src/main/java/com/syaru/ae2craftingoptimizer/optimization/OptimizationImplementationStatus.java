package com.syaru.ae2craftingoptimizer.optimization;

/**
 * 機能台帳に登録した最適化の実装状態。
 *
 * <p>過去のConfigキーを残すことと、対応する実行経路が存在することを区別する。
 */
public enum OptimizationImplementationStatus {
    /** 対応するruntime入口と回帰試験が存在する。 */
    ACTIVE,
    /** 既存TOMLを読めるようキーだけ残すが、危険な旧実装は廃止済みで再登録しない。 */
    RETIRED_COMPATIBILITY_KEY
}
