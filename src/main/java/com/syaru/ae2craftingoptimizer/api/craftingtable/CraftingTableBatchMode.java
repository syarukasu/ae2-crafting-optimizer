package com.syaru.ae2craftingoptimizer.api.craftingtable;

/**
 * 一括作業台仕事の数量会計を誰が所有するか。
 *
 * <p>どちらも同じ実レシピ検証と物理Workerを使う。違いは、完成量をAE2の
 * signed-long台帳へ直接返すか、BigInteger親JobへReceiptとして返すかだけ。</p>
 */
public enum CraftingTableBatchMode {
    /** AE2またはAdvanced AEのtasksとwaitingForが正本になる通常仕事。 */
    AE2_JOB,
    /** ACOのBigInteger親Jobが正確な入出力量を所有する仕事。 */
    BIG_INTEGER_JOB
}
