package com.syaru.ae2craftingoptimizer.api.batch.v2;

/**
 * Transactional Batchの電力を送信元と実機のどちらが所有するか。
 */
public enum BatchEnergyAccountingMode {
    /** AE2 CPUが論理実行回数ぶんのPattern電力を先に消費する。 */
    SOURCE_LOGICAL_EXECUTIONS,
    /** 実Workerが一つの物理仕事として電力と進捗を所有する。 */
    TARGET_PHYSICAL_OPERATION
}
