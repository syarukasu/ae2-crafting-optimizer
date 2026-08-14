package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.syaru.ae2craftingoptimizer.optimization.ProviderPatternGenerationTracker;
import java.math.BigInteger;
import java.util.Map;
import java.util.Objects;

/**
 * 各AEKey量とPattern回数はlongに収まるが、合計CPU容量だけがlongを超える厳密計画。
 *
 * <p>AE2の公開APIは容量をlongでしか受け取れないため、{@link #bytes()}は互換用に
 * {@link Long#MAX_VALUE}を返す。対応CPUアドオンは{@link #exactBytes()}を公開Sidecarから
 * 取得し、互換値を容量会計の正本として使用しない。</p>
 */
public final class BigCapacityCraftingPlan implements WideCraftingPlan {
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private final GenericStack finalOutput;
    private final boolean simulation;
    private final boolean multiplePaths;
    private final KeyCounter usedItems;
    private final KeyCounter emittedItems;
    private final KeyCounter missingItems;
    private final Map<IPatternDetails, Long> patternTimes;
    private final BigInteger exactBytes;
    private final long patternGeneration;
    private final long recipeGeneration;

    public BigCapacityCraftingPlan(
            GenericStack finalOutput,
            boolean simulation,
            boolean multiplePaths,
            KeyCounter usedItems,
            KeyCounter emittedItems,
            KeyCounter missingItems,
            Map<IPatternDetails, Long> patternTimes,
            BigInteger exactBytes,
            long patternGeneration,
            long recipeGeneration) {
        this.finalOutput = Objects.requireNonNull(finalOutput, "finalOutput");
        this.simulation = simulation;
        this.multiplePaths = multiplePaths;
        this.usedItems = Objects.requireNonNull(usedItems, "usedItems");
        this.emittedItems = Objects.requireNonNull(emittedItems, "emittedItems");
        this.missingItems = Objects.requireNonNull(missingItems, "missingItems");
        this.patternTimes = Map.copyOf(Objects.requireNonNull(patternTimes, "patternTimes"));
        this.exactBytes = Objects.requireNonNull(exactBytes, "exactBytes");
        // この型はlongで表現できない容量だけを運ぶ。範囲内なら通常CraftingPlanを使う。
        if (exactBytes.compareTo(LONG_MAX) <= 0) {
            throw new IllegalArgumentException("Big-capacity plan must exceed the signed-long range");
        }
        // 世代番号が無効なら、古いPatternを実行する危険があるため生成時点で拒否する。
        if (patternGeneration < 0L || recipeGeneration < 0L) {
            throw new IllegalArgumentException("Big-capacity plan generations must be non-negative");
        }
        this.patternGeneration = patternGeneration;
        this.recipeGeneration = recipeGeneration;
    }

    @Override
    public GenericStack finalOutput() {
        return finalOutput;
    }

    /** AE2互換境界だけに見せる飽和値。真の容量はexactBytes()を使用する。 */
    @Override
    public long bytes() {
        return Long.MAX_VALUE;
    }

    @Override
    public boolean simulation() {
        return simulation;
    }

    @Override
    public boolean multiplePaths() {
        return multiplePaths;
    }

    @Override
    public KeyCounter usedItems() {
        return usedItems;
    }

    @Override
    public KeyCounter emittedItems() {
        return emittedItems;
    }

    @Override
    public KeyCounter missingItems() {
        return missingItems;
    }

    @Override
    public Map<IPatternDetails, Long> patternTimes() {
        return patternTimes;
    }

    public BigInteger exactBytes() {
        return exactBytes;
    }

    /** CPU提出時に、この計画が実際に参照するPatternだけを現在索引へ再照合する。 */
    public ExactPlanPatternRevalidator.Result validateForSubmission(IGrid grid) {
        return ExactPlanPatternRevalidator.validate(
                grid,
                patternGeneration,
                recipeGeneration,
                patternTimes.keySet());
    }

    public long patternGeneration() {
        return patternGeneration;
    }

    public long recipeGeneration() {
        return recipeGeneration;
    }

    /**
     * 全体世代だけを確認する旧互換API。
     *
     * @deprecated CPU提出時は、無関係なProvider更新を区別できる
     *     {@link #validateForSubmission(IGrid)}を使用する。
     */
    @Deprecated(forRemoval = false)
    public boolean generationsAreCurrent() {
        return patternGeneration == ProviderPatternGenerationTracker.generation()
                && recipeGeneration == RecipeGenerationTracker.generation();
    }
}
