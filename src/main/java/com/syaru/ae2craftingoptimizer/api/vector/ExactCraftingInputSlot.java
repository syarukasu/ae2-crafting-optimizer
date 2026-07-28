package com.syaru.ae2craftingoptimizer.api.vector;

import appeng.api.stacks.AEKey;
import java.util.Objects;

/**
 * 一つの作業台Pattern slotでPlannerが選んだ、一回実行当たりの具体入力。
 *
 * <p>候補集合ではなく選択結果を保存する。これにより再起動後も別のタグ素材へ
 * すり替わらず、NeoECOの実assembleへ計画時と同じグリッドを渡せる。</p>
 */
public record ExactCraftingInputSlot(
        AEKey key,
        long amountPerExecution) {
    public ExactCraftingInputSlot {
        Objects.requireNonNull(
                key,
                "key");
        // AE2 Pattern APIが表現する一回入力量はsigned longの正数だけを受理する。
        if (amountPerExecution <= 0L) {
            throw new IllegalArgumentException(
                    "amountPerExecution must be positive");
        }
    }
}
