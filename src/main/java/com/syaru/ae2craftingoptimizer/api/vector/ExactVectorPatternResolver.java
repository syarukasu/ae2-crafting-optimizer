package com.syaru.ae2craftingoptimizer.api.vector;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import com.syaru.ae2craftingoptimizer.engine.Ae2CompiledCraftingGraphCache;
import com.syaru.ae2craftingoptimizer.engine.RecipeGenerationTracker;
import com.syaru.ae2craftingoptimizer.optimization.ProviderPatternGenerationTracker;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.level.Level;

/**
 * 外部Executorが、計画に記録されたPattern IDを現在世代のAE2 Patternへ安全に戻すAPI。
 */
public final class ExactVectorPatternResolver {
    private ExactVectorPatternResolver() {
    }

    public static Optional<List<IPatternDetails>> resolve(
            IGrid grid,
            Level level,
            PreparedVectorBatch plan) {
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(plan, "plan");
        // 世代が一つでも変わった計画は、同名Patternが残っていても再利用しない。
        if (ProviderPatternGenerationTracker.generation()
                        != plan.patternGeneration()
                || RecipeGenerationTracker.generation()
                        != plan.recipeGeneration()) {
            return Optional.empty();
        }
        Ae2CompiledCraftingGraphCache.Snapshot snapshot =
                Ae2CompiledCraftingGraphCache.getOrCompile(grid, level);
        if (snapshot.graph().generation() != plan.patternGeneration()
                || snapshot.recipeGeneration() != plan.recipeGeneration()) {
            return Optional.empty();
        }

        List<IPatternDetails> resolved =
                new ArrayList<>(plan.requiredPatternIds().size());
        // 保存順の各IDを一度だけ解決し、欠落した最初のIDで未対応として返す。
        for (String patternId : plan.requiredPatternIds()) {
            IPatternDetails pattern = snapshot.pattern(patternId);
            if (pattern == null) {
                return Optional.empty();
            }
            resolved.add(pattern);
        }
        return Optional.of(List.copyOf(resolved));
    }
}
