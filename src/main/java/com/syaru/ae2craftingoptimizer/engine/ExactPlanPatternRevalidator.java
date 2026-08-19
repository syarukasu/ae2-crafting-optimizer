package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.syaru.ae2craftingoptimizer.optimization.ProviderPatternGenerationTracker;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Exact計画が参照するPatternだけを、CPU提出直前のCraftingServiceへ再照合する。
 *
 * <p>Issue #90: 全Provider共通世代だけを比較すると、無関係なProvider更新でも
 * BigInteger計画がすべて失効する。全体世代が変わった場合だけ、計画内のPatternが
 * 現在の索引に同一参照で残ることを確認する。</p>
 */
public final class ExactPlanPatternRevalidator {
    private ExactPlanPatternRevalidator() {
    }

    public static Result validate(
            IGrid grid,
            long plannedPatternGeneration,
            long plannedRecipeGeneration,
            Collection<IPatternDetails> plannedPatterns) {
        Objects.requireNonNull(grid, "grid");
        return validate(
                plannedPatternGeneration,
                plannedRecipeGeneration,
                ProviderPatternGenerationTracker.generation(),
                RecipeGenerationTracker.generation(),
                plannedPatterns,
                key -> grid.getCraftingService().getCraftingFor(key));
    }

    static Result validate(
            long plannedPatternGeneration,
            long plannedRecipeGeneration,
            long currentPatternGeneration,
            long currentRecipeGeneration,
            Collection<IPatternDetails> plannedPatterns,
            Function<AEKey, Collection<IPatternDetails>> currentPatterns) {
        Objects.requireNonNull(plannedPatterns, "plannedPatterns");
        Objects.requireNonNull(currentPatterns, "currentPatterns");

        // Recipe reload後は同じPattern参照でも意味が変わり得るため、再利用しない。
        if (plannedRecipeGeneration != currentRecipeGeneration) {
            return Result.RECIPE_GENERATION_CHANGED;
        }
        // 同じ全体世代なら、計画時の厳密Topology検証をそのまま利用できる。
        if (plannedPatternGeneration == currentPatternGeneration) {
            return Result.CURRENT_GENERATION;
        }
        // Patternを持たない在庫完結計画には、Provider世代で失効する参照が存在しない。
        if (plannedPatterns.isEmpty()) {
            return Result.REFERENCED_PATTERNS_REVALIDATED;
        }

        Map<AEKey, Set<IPatternDetails>> requiredByOutput = new LinkedHashMap<>();
        // 主出力ごとに必要Patternをまとめ、CraftingService索引の取得を一回へ抑える。
        for (IPatternDetails pattern : plannedPatterns) {
            IPatternDetails checkedPattern = Objects.requireNonNull(pattern, "planned pattern");
            GenericStack primaryOutput = checkedPattern.getPrimaryOutput();
            // 主出力なしのPatternは現在索引から安全に引き直せないため拒否する。
            if (primaryOutput == null) {
                return Result.REFERENCED_PATTERN_CHANGED;
            }
            requiredByOutput
                    .computeIfAbsent(
                            primaryOutput.what(),
                            ignored -> java.util.Collections.newSetFromMap(
                                    new IdentityHashMap<>()))
                    .add(checkedPattern);
        }

        // 無関係なProvider更新は許容し、計画が実際に使うPatternだけを再照合する。
        for (Map.Entry<AEKey, Set<IPatternDetails>> entry : requiredByOutput.entrySet()) {
            Collection<IPatternDetails> indexed = currentPatterns.apply(entry.getKey());
            // 出力索引自体が消えた場合は、古いPatternを実行対象へ残さない。
            if (indexed == null || indexed.isEmpty()) {
                return Result.REFERENCED_PATTERN_CHANGED;
            }
            Set<IPatternDetails> missing = java.util.Collections.newSetFromMap(
                    new IdentityHashMap<>());
            missing.addAll(entry.getValue());
            // equalsではなく同一参照を照合し、同内容に見える差し替えを誤採用しない。
            for (IPatternDetails current : indexed) {
                missing.remove(current);
                // この出力に必要な全Patternが見つかれば残りの索引走査を省略する。
                if (missing.isEmpty()) {
                    break;
                }
            }
            // 一件でも参照Patternが消えていれば、計画全体を提出前に拒否する。
            if (!missing.isEmpty()) {
                return Result.REFERENCED_PATTERN_CHANGED;
            }
        }
        return Result.REFERENCED_PATTERNS_REVALIDATED;
    }

    public enum Result {
        CURRENT_GENERATION(true, "planning generations are current"),
        REFERENCED_PATTERNS_REVALIDATED(true, "referenced patterns remain current"),
        RECIPE_GENERATION_CHANGED(false, "recipe generation changed after planning"),
        REFERENCED_PATTERN_CHANGED(false, "a referenced pattern changed after planning");

        private final boolean valid;
        private final String detail;

        Result(boolean valid, String detail) {
            this.valid = valid;
            this.detail = detail;
        }

        public boolean valid() {
            return valid;
        }

        public String detail() {
            return detail;
        }
    }
}
