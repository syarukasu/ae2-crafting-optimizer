package com.syaru.ae2craftingoptimizer.api.execution;

import appeng.api.crafting.IPatternDetails;

/**
 * 一つのCompiled Crafting Islandへ束縛した、短命な外部設備セッション。
 */
public interface CraftingIslandBackendSession {
    /** 一Waveで完了できる、島の外部sink Pattern実行回数を返す。 */
    long acoRootExecutionCapacity();

    /** セッション作成後も指定Patternの所有権が保たれているか再検証する。 */
    boolean acoSupportsPattern(IPatternDetails pattern);

    /** 島内の一論理Pattern実行あたりに課すAE電力を返す。 */
    double acoEnergyPerLogicalExecution();

    /** 構造、Grid接続、設定がcommit直前にも有効か再検証する。 */
    boolean acoStillAvailable();

    /** 統計と一度だけ出す失敗ログへ表示する短い名前を返す。 */
    String acoBackendName();
}
