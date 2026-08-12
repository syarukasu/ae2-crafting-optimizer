package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * long計算へ戻さずに返す、BigInteger正本の不足simulation計画。
 *
 * <p>実行用の{@link BigIntegerCraftingPlan}とは意図的に別型にする。
 * 素材不足の計画をAQEやInsaneAEの実行境界へ渡さず、AE2の確認画面だけへ返すためである。</p>
 */
public final class BigIntegerSimulationPlan implements WideCraftingPlan {
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

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
        this.exactPatternTimes = immutablePositiveCounts(
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
        this.usedItems = projectKeyCounter(exactPlan.usedInventory());
        this.emittedItems = projectKeyCounter(exactPlan.emitted());
        this.missingItems = projectKeyCounter(exactPlan.missing());
        this.patternTimes = projectPatternCounter(this.exactPatternTimes);
    }

    @Override
    public GenericStack finalOutput() {
        return finalOutput;
    }

    /** AE2互換境界では負数へwrapさせず、long範囲内は正確値、超過だけを飽和する。 */
    @Override
    public long bytes() {
        return saturatedLong(exactBytes);
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

    private static KeyCounter projectKeyCounter(Map<AEKey, BigInteger> exact) {
        KeyCounter projected = new KeyCounter();
        // AE2の表示用Counterだけを作り、BigInteger Mapを正本として保持する。
        exact.forEach((key, amount) -> projected.add(key, saturatedLong(amount)));
        return projected;
    }

    private static Map<IPatternDetails, Long> projectPatternCounter(
            Map<IPatternDetails, BigInteger> exact) {
        Map<IPatternDetails, Long> projected = new LinkedHashMap<>();
        // 画面互換のPattern回数だけを飽和し、正確な回数はSidecarへ残す。
        exact.forEach((pattern, amount) -> projected.put(pattern, saturatedLong(amount)));
        return Map.copyOf(projected);
    }

    private static long saturatedLong(BigInteger amount) {
        // long範囲を超える値を負数へwrapさせず、画面互換の上限へ飽和する。
        return amount.compareTo(LONG_MAX) > 0 ? Long.MAX_VALUE : amount.longValueExact();
    }

    private static Map<IPatternDetails, BigInteger> immutablePositiveCounts(
            Map<IPatternDetails, BigInteger> counts,
            String name) {
        Map<IPatternDetails, BigInteger> copy = new LinkedHashMap<>();
        Objects.requireNonNull(counts, name).forEach((pattern, amount) -> {
            Objects.requireNonNull(pattern, name + " key");
            BigCountMath.requireNonNegative(amount, name);
            // 実行回数0のPatternは表示・会計へ含めない。
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException(name + " values must be positive");
            }
            copy.put(pattern, amount);
        });
        return Map.copyOf(copy);
    }
}
