package com.syaru.ae2craftingoptimizer.api.vector;

/**
 * Exact Vector Transactionで、境界入出力を所有・配送する側を明示する。
 */
public enum VectorResourceMode {
    /** AAC ExecutorがME Storageから入力を取得し、成果物もME Storageへ返す。 */
    NETWORK_STORAGE,
    /** AQE CPUが入力を預かり、実行完了後のJob会計と成果物配送もACOが行う。 */
    HOST_ESCROWED
}
