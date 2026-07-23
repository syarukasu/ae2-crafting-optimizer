package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.syaru.ae2craftingoptimizer.optimization.ProviderPatternGenerationTracker;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pattern回数または個別AEKey量がsigned longを超える、AQE専用のBigInteger親計画。
 *
 * <p>AE2の画面同期APIはlong固定なので、標準getterはLong.MAX_VALUE以下の表示Facadeを返す。
 * Facadeは実行や容量会計には使用せず、Advanced AE提出境界で{@link #preparedRoot()}を
 * ACO Hostへ原子的に移譲する。通常AE2 CPUは専用Mixinで必ず拒否する。</p>
 */
public final class BigIntegerCraftingPlan implements ICraftingPlan {
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

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
        this.finalOutput = Objects.requireNonNull(finalOutput, "finalOutput");
        this.exactPlan = Objects.requireNonNull(exactPlan, "exactPlan");
        this.exactPatternTimes = immutablePositiveCounts(
                exactPatternTimes, "exactPatternTimes");
        this.preparedRoot = Objects.requireNonNull(preparedRoot, "preparedRoot");
        // 表示対象とBig親Jobが別注文を指す状態は、提出前に構築エラーとして止める。
        if (!finalOutput.what().equals(exactPlan.requestedKey())
                || !BigInteger.valueOf(finalOutput.amount()).equals(exactPlan.requestedAmount())
                || !preparedRoot.symbolicPlan().equals(exactPlan)
                || !preparedRoot.reservedBytes().equals(preparedRoot.job().reservedCapacity())) {
            throw new IllegalArgumentException("BigInteger plan metadata is inconsistent");
        }
        // この型は少なくとも一つの個別カウンタがlongを超える計画だけを運ぶ。
        if (!containsWideCounter(exactPlan, this.exactPatternTimes)) {
            throw new IllegalArgumentException(
                    "BigInteger crafting plan requires at least one counter past signed long");
        }
        this.usedItems = projectKeyCounter(exactPlan.usedInventory());
        this.emittedItems = projectKeyCounter(exactPlan.emitted());
        this.missingItems = projectKeyCounter(exactPlan.missing());
        this.patternTimes = projectPatternCounter(this.exactPatternTimes);
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

    /** 計算後にPatternまたはrecipeが変わった親Jobを実行しない。 */
    public boolean generationsAreCurrent() {
        return preparedRoot.patternGeneration() == ProviderPatternGenerationTracker.generation()
                && preparedRoot.recipeGeneration() == RecipeGenerationTracker.generation();
    }

    private static KeyCounter projectKeyCounter(Map<AEKey, BigInteger> exact) {
        KeyCounter projected = new KeyCounter();
        // 画面同期用Facadeだけを作り、BigInteger正本Mapは変更しない。
        exact.forEach((key, amount) -> projected.add(key, saturatedLong(amount)));
        return projected;
    }

    private static Map<IPatternDetails, Long> projectPatternCounter(
            Map<IPatternDetails, BigInteger> exact) {
        Map<IPatternDetails, Long> projected = new LinkedHashMap<>();
        // AE2の画面が要求するMap値だけを飽和し、実行回数はpreparedRoot内へ保持する。
        exact.forEach((pattern, amount) -> projected.put(pattern, saturatedLong(amount)));
        return Map.copyOf(projected);
    }

    private static long saturatedLong(BigInteger amount) {
        // Long.MAX_VALUE以下だけをexact変換し、超過値を負数へwrapさせない。
        return amount.compareTo(LONG_MAX) > 0 ? Long.MAX_VALUE : amount.longValueExact();
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
            if (amount.compareTo(LONG_MAX) > 0) {
                return true;
            }
        }
        return false;
    }

    private static <K> Map<K, BigInteger> immutablePositiveCounts(
            Map<K, BigInteger> counts,
            String name) {
        Map<K, BigInteger> copy = new LinkedHashMap<>();
        Objects.requireNonNull(counts, name).forEach((key, amount) -> {
            Objects.requireNonNull(key, name + " key");
            BigCountMath.requireNonNegative(amount, name);
            // Pattern回数0は実行計画へ含めず、負数は上の共通検査で拒否する。
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException(name + " values must be positive");
            }
            copy.put(key, amount);
        });
        return Map.copyOf(copy);
    }
}
