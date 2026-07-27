package com.syaru.ae2craftingoptimizer.api.vector;

import appeng.api.networking.IGrid;
import com.syaru.ae2craftingoptimizer.integration.ExactVectorGridTickBudget;
import java.util.Objects;

/**
 * Exact Vector Executorが、同じME Gridの空き時間から論理段をまとめて取得する公開境界。
 *
 * <p>返却値は数量ではなく、既にコンパイル済みのクリティカルパス段数である。
 * Executorは返却された範囲だけを一括会計し、0なら次のserver tickまで待機する。</p>
 */
public final class ExactVectorExecutionBudget {
    private ExactVectorExecutionBudget() {
    }

    /**
     * 現在tickで進められる論理段数を返す。
     *
     * <p>一Gridの最初の要求は飢餓防止のため一段以上を受理する。二回目以降は共有soft
     * 時間予算と件数上限に従うため、空いているserver tickでは20段などを一度に完了できる。</p>
     */
    public static int claimLogicalStages(
            IGrid grid,
            int requestedStages) {
        Objects.requireNonNull(grid, "grid");
        if (requestedStages <= 0) {
            throw new IllegalArgumentException(
                    "requestedStages must be positive");
        }
        return ExactVectorGridTickBudget.claimActiveStages(
                grid, requestedStages);
    }
}
