package com.syaru.ae2craftingoptimizer.api.execution;

import appeng.api.networking.energy.IEnergyService;
import appeng.me.service.CraftingService;

/**
 * 外部設備が、ACOのCompiled Crafting Islandを原子的に実行できることを示す任意API。
 *
 * <p>ACOはNeo ECOへこのAPIだけを問い合わせる。設備固有クラスをACOへ直結しないため、
 * AACが無い環境では通常のAE2/Neo ECO処理へそのまま戻る。</p>
 */
public interface CraftingIslandExecutionOwner {
    /** この呼出しでは島を扱わず、元のexecuteCraftingを続行する戻り値。 */
    int NOT_HANDLED = -1;

    /**
     * 入力が揃った一つの島を実行する。
     *
     * @param executionBudgetHint 呼出しtickで通常配送へ割り当てられた参考予算
     * @return 未対応または入力待ちの{@link #NOT_HANDLED}、不確定状態で停止した0、
     *         または完了した原子Wave数の1
     */
    int acoTryExecuteCompiledCraftingIsland(
            int executionBudgetHint,
            CraftingService craftingService,
            IEnergyService energyService);
}
