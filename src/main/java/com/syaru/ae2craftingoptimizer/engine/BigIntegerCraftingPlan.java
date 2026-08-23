package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.syaru.ae2craftingoptimizer.optimization.ProviderPatternGenerationTracker;
import java.math.BigInteger;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pattern回数または個別AEKey量がsigned longを超える、AQE専用のBigInteger親計画。
 *
 * <p>AE2の画面同期APIはlong固定なので、標準getterはLong.MAX_VALUE以下の表示Facadeを返す。
 * Advanced AE提出境界ではFacadeから実CPUとCraftingLinkだけを作り、正確なPattern task、
 * waitingFor、remainingOutput、容量を同じ実Jobへ設置する。通常AE2 CPUは専用Mixinで拒否する。</p>
 */
public final class BigIntegerCraftingPlan implements WideCraftingPlan {
    private final GenericStack finalOutput;
    private final BigCraftingPlan<AEKey> exactPlan;
    private final Map<IPatternDetails, BigInteger> exactPatternTimes;
    private final Ae2BigCraftingPlanFactory.PreparedBigRootPlan preparedRoot;
    private final KeyCounter usedItems;
    private final KeyCounter emittedItems;
    private final KeyCounter missingItems;
    private final Map<IPatternDetails, Long> patternTimes;
    private final AtomicBoolean submissionClaimed = new AtomicBoolean();

    public BigIntegerCraftingPlan(
            GenericStack finalOutput,
            BigCraftingPlan<AEKey> exactPlan,
            Map<IPatternDetails, BigInteger> exactPatternTimes,
            Ae2BigCraftingPlanFactory.PreparedBigRootPlan preparedRoot) {
        this(finalOutput, exactPlan, exactPatternTimes, preparedRoot, false);
    }

    public BigIntegerCraftingPlan(
            GenericStack finalOutput,
            BigCraftingPlan<AEKey> exactPlan,
            Map<IPatternDetails, BigInteger> exactPatternTimes,
            Ae2BigCraftingPlanFactory.PreparedBigRootPlan preparedRoot,
            boolean requiresBigIntegerExecution) {
        this.finalOutput = Objects.requireNonNull(finalOutput, "finalOutput");
        this.exactPlan = Objects.requireNonNull(exactPlan, "exactPlan");
        this.exactPatternTimes = BigIntegerPlanProjection.immutablePositiveCounts(
                exactPatternTimes, "exactPatternTimes");
        this.preparedRoot = Objects.requireNonNull(preparedRoot, "preparedRoot");
        // 表示対象とBig親Jobが別注文を指す状態は、提出前に構築エラーとして止める。
        if (!finalOutput.what().equals(exactPlan.requestedKey())
                || !BigInteger.valueOf(finalOutput.amount()).equals(exactPlan.requestedAmount())
                || !preparedRoot.symbolicPlan().equals(exactPlan)
                || (preparedRoot.rootWindowJob() != null
                        && !preparedRoot.reservedBytes().equals(
                                preparedRoot.rootWindowJob().reservedCapacity()))) {
            throw new IllegalArgumentException("BigInteger plan metadata is inconsistent");
        }
        // 個別値またはキー別合計がlong超過してAE2の乗算を使えない計画だけを運ぶ。
        if (!requiresBigIntegerExecution
                && !containsWideCounter(exactPlan, this.exactPatternTimes)) {
            throw new IllegalArgumentException(
                    "BigInteger crafting plan requires an exact-arithmetic reason");
        }
        this.usedItems = BigIntegerPlanProjection.projectKeyCounter(exactPlan.usedInventory());
        this.emittedItems = BigIntegerPlanProjection.projectKeyCounter(exactPlan.emitted());
        this.missingItems = BigIntegerPlanProjection.projectKeyCounter(exactPlan.missing());
        this.patternTimes = BigIntegerPlanProjection.projectPatternCounter(this.exactPatternTimes);
    }

    @Override
    public GenericStack finalOutput() {
        return finalOutput;
    }

    /** CPU一覧へ見せる互換上限。正確な値はexactBytes()だけを容量台帳へ渡す。 */
    @Override
    public long bytes() {
        return Long.MAX_VALUE;
    }

    @Override
    public boolean simulation() {
        return !exactPlan.craftable();
    }

    @Override
    public boolean multiplePaths() {
        return false;
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
        return preparedRoot.reservedBytes();
    }

    /**
     * AE2標準のlong計算へ委譲できるかを、投影済み表示値ではなく正本で判定する。
     *
     * <p>ACOがwide専用実行を取得するのは、個別の数量またはCPU容量がsigned longを
     * 超える場合だけである。ここがtrueの計画をACOが横取りすると、標準AE2が安全に
     * 実行できる通常注文まで物理Target待ちのまま停止する。</p>
     */
    public boolean fitsStandardLongExecution() {
        return exactBytes().compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0
                && !containsWideCounter(exactPlan, exactPatternTimes);
    }

    public BigCraftingPlan<AEKey> exactPlan() {
        return exactPlan;
    }

    public Map<IPatternDetails, BigInteger> exactPatternTimes() {
        return exactPatternTimes;
    }

    public Ae2BigCraftingPlanFactory.PreparedBigRootPlan preparedRoot() {
        return preparedRoot;
    }

    /** 同じ確認画面の二重クリックから、同一Jobを二つのHostへ提出しない。 */
    public boolean claimSubmission() {
        return submissionClaimed.compareAndSet(false, true);
    }

    /** Hostが所有権を受け取る前に失敗した場合だけ、別CPUへの再提出を許可する。 */
    public void releaseSubmissionClaim() {
        submissionClaimed.set(false);
    }

    /** CPU提出時に、この親Jobが実際に参照するPatternだけを現在索引へ再照合する。 */
    public ExactPlanPatternRevalidator.Result validateForSubmission(IGrid grid) {
        return ExactPlanPatternRevalidator.validate(
                grid,
                preparedRoot.patternGeneration(),
                preparedRoot.recipeGeneration(),
                exactPatternTimes.keySet());
    }

    public long patternGeneration() {
        return preparedRoot.patternGeneration();
    }

    public long recipeGeneration() {
        return preparedRoot.recipeGeneration();
    }

    /**
     * 全体世代だけを確認する旧互換API。
     *
     * @deprecated CPU提出時は、無関係なProvider更新を区別できる
     *     {@link #validateForSubmission(IGrid)}を使用する。
     */
    @Deprecated(forRemoval = false)
    public boolean generationsAreCurrent() {
        return preparedRoot.patternGeneration() == ProviderPatternGenerationTracker.generation()
                && preparedRoot.recipeGeneration() == RecipeGenerationTracker.generation();
    }

    private static boolean containsWideCounter(
            BigCraftingPlan<AEKey> plan,
            Map<IPatternDetails, BigInteger> exactPatternTimes) {
        return containsWideValue(exactPatternTimes)
                || containsWideValue(plan.usedInventory())
                || containsWideValue(plan.emitted())
                || containsWideValue(plan.missing());
    }

    private static boolean containsWideValue(Map<?, BigInteger> counts) {
        // 各値を個別に調べ、Map全体の合計だけが大きいBigCapacity計画とは区別する。
        for (BigInteger amount : counts.values()) {
            // 個別値がlongを超えた時点で専用親計画が必要になる。
            if (BigIntegerPlanProjection.exceedsLong(amount)) {
                return true;
            }
        }
        return false;
    }

}
