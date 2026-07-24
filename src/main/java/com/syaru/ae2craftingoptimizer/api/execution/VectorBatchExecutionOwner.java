package com.syaru.ae2craftingoptimizer.api.execution;

/**
 * 外部クラフトエンジンが、大量の論理クラフトを一つの定数時間Batchとして処理できることをACOへ伝える。
 *
 * <p>通常のPattern push数を水増しするためのAPIではない。実装側は、入力抽出・出力会計・保存・復旧を
 * Batch全体で原子的に維持できる場合だけtrueを返すこと。
 */
public interface VectorBatchExecutionOwner {
    /**
     * 現在のネットワークに、実行可能なVector Batch設備が存在するかを返す。
     *
     * @return 個別クラフト数ではなく実行時間で予算管理できる場合だけtrue
     */
    boolean acoSupportsVectorBatchExecution();
}
