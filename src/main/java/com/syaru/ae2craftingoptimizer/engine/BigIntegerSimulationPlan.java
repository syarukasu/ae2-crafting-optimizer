package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import java.math.BigInteger;
import java.util.Map;
import java.util.Objects;

/**
 * long計算へ戻さずに返す、BigInteger正本の不足simulation計画。
 *
 * <p>実行用の{@link BigIntegerCraftingPlan}とは意図的に別型にする。
 * 素材不足の計画をAQEやInsaneAEの実行境界へ渡さず、AE2の確認画面だけへ返すためである。</p>
 */
public final class BigIntegerSimulationPlan implements WideCraftingPlan {
    private final GenericStack finalOutput;
    private final BigCraftingPlan<AEKey> exactPlan;
    private final Map<IPatternDetails, BigInteger> exactPatternTimes;
    private final KeyCounter usedItems;
    private final KeyCounter emittedItems;
    private final KeyCounter missingItems;
    private final Map<IPatternDetails, Long> patternTimes;
    private final BigInteger exactBytes;

    public BigIntegerSimulationPlan(
            GenericStack finalOutput,
            BigCraftingPlan<AEKey> exactPlan,
            Map<IPatternDetails, BigInteger> exactPatternTimes,
            BigInteger exactBytes) {
        this(
                finalOutput,
                exactPlan,
                exactPatternTimes,
                exactBytes,
                com.syaru.ae2craftingoptimizer.config.ACOConfig.getBigIntegerMaximumBits());
    }

    BigIntegerSimulationPlan(
            GenericStack finalOutput,
            BigCraftingPlan<AEKey> exactPlan,
            Map<IPatternDetails, BigInteger> exactPatternTimes,
            BigInteger exactBytes,
            int maximumBits) {
        this.finalOutput = Objects.requireNonNull(finalOutput, "finalOutput");
        this.exactPlan = Objects.requireNonNull(exactPlan, "exactPlan");
        // simulationは不足計画だけを表し、実行可能計画を誤って提出境界へ流さない。
        if (exactPlan.craftable()) {
            throw new IllegalArgumentException("BigInteger simulation plan must be missing");
        }
        this.exactPatternTimes = BigIntegerPlanProjection.immutablePositiveCounts(
                exactPatternTimes, "exactPatternTimes");
        this.exactBytes = BigCountMath.requireMaximumBits(
                Objects.requireNonNull(exactBytes, "exactBytes"),
                "simulation/exactBytes",
                maximumBits);
        // 表示する注文とBigInteger正本が異なる計画を外へ出さない。
        if (!finalOutput.what().equals(exactPlan.requestedKey())
                || !BigInteger.valueOf(finalOutput.amount()).equals(exactPlan.requestedAmount())) {
            throw new IllegalArgumentException("BigInteger simulation metadata is inconsistent");
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

    /** AE2互換境界では負数へwrapさせず、long範囲内は正確値、超過だけを飽和する。 */
    @Override
    public long bytes() {
        return BigIntegerPlanProjection.saturatedLong(exactBytes);
    }

    @Override
    public boolean simulation() {
        return true;
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
        return exactBytes;
    }

    public BigCraftingPlan<AEKey> exactPlan() {
        return exactPlan;
    }

    public Map<IPatternDetails, BigInteger> exactPatternTimes() {
        return exactPatternTimes;
    }

}
