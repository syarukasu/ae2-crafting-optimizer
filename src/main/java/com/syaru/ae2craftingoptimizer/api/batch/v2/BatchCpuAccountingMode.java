package com.syaru.ae2craftingoptimizer.api.batch.v2;

/**
 * Batch一件をCrafting CPUのtick予算へどう数えるか。
 */
public enum BatchCpuAccountingMode {
    /**
     * 従来Adapter向け。受理した論理実行数を、そのままCPU操作数として数える。
     */
    LOGICAL_EXECUTIONS,

    /**
     * 実ワーカーがN回を一つの原子的仕事として所有するAdapter向け。
     *
     * <p>Task残量はN回ぶん正確に減らすが、{@code executeCrafting}の戻り値は
     * 物理配送一回として1にする。</p>
     */
    SINGLE_PHYSICAL_OPERATION
}
