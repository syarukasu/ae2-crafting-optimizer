package com.syaru.ae2craftingoptimizer.api.vector;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

/**
 * 決定的な作業台Pattern一種類を、実Workerが一度組み立てて係数展開する実行Step。
 *
 * <p>数量ぶんのThreadやItemStackは作らない。Pattern Busはレシピを一度実行し、
 * 検証した一回分の入出力へ{@link #executions()}を掛けて親CPUへ返す。</p>
 */
public record ExactCraftingStep(
        String patternId,
        int depth,
        BigInteger executions,
        List<ExactCraftingInputSlot> selectedInputs) {
    /** バニラ作業台グリッドが保持できる入力slot数。 */
    private static final int MAXIMUM_CRAFTING_GRID_SLOTS = 9;

    public ExactCraftingStep {
        patternId = Objects.requireNonNull(
                patternId,
                "patternId").trim();
        executions = Objects.requireNonNull(
                executions,
                "executions");
        selectedInputs = List.copyOf(
                Objects.requireNonNull(
                        selectedInputs,
                        "selectedInputs"));
        if (patternId.isEmpty()
                || depth <= 0
                || executions.signum() <= 0
                || selectedInputs.isEmpty()
                || selectedInputs.size()
                        > MAXIMUM_CRAFTING_GRID_SLOTS) {
            throw new IllegalArgumentException(
                    "invalid exact crafting step");
        }
        // slot順と重複キーを維持しつつ、null entryだけを拒否する。
        for (ExactCraftingInputSlot input :
                selectedInputs) {
            Objects.requireNonNull(
                    input,
                    "selected input");
        }
    }
}
