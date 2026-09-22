package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import java.math.BigInteger;
import java.util.Map;
import java.util.Objects;

/** Issue #208: completed VM quantities, independent of a CPU's physical preparation. */
public final class VmCalculatedCraftingPlan implements WideCraftingPlan {
    private final BigCraftingPlan<AEKey> exactPlan;
    private final Map<IPatternDetails, BigInteger> times;
    private final BigInteger bytes;
    private final long patterns;
    private final long recipes;
    private final boolean multiplePaths;
    private BigIntegerCraftingPlan prepared;

    public VmCalculatedCraftingPlan(BigCraftingPlan<AEKey> exactPlan,
            Map<IPatternDetails, BigInteger> times, BigInteger bytes,
            long patterns, long recipes, boolean multiplePaths, int maximumBits) {
        this.exactPlan = Objects.requireNonNull(exactPlan, "exactPlan");
        if (!exactPlan.craftable()) throw new IllegalArgumentException("use the missing-plan contract for missing inputs");
        this.times = BigIntegerPlanProjection.immutablePositiveCounts(times, "vm/patternTimes");
        this.bytes = BigCountMath.requireMaximumBits(bytes, "vm/bytes", maximumBits);
        BigCountMath.requireMaximumBits(exactPlan.requestedAmount(), "vm/request", maximumBits);
        this.patterns = patterns;
        this.recipes = recipes;
        this.multiplePaths = multiplePaths;
    }

    public BigCraftingPlan<AEKey> exactPlan() { return exactPlan; }
    public Map<IPatternDetails, BigInteger> exactPatternTimes() { return times; }
    public long patternGeneration() { return patterns; }
    public long recipeGeneration() { return recipes; }
    @Override public BigInteger exactBytes() { return bytes; }
    @Override public GenericStack finalOutput() {
        return new GenericStack(exactPlan.requestedKey(), BigIntegerPlanProjection.saturatedLong(exactPlan.requestedAmount()));
    }
    @Override public long bytes() { return BigIntegerPlanProjection.saturatedLong(bytes); }
    @Override public boolean simulation() { return false; }
    @Override public boolean multiplePaths() { return multiplePaths; }
    @Override public KeyCounter usedItems() { return BigIntegerPlanProjection.projectKeyCounter(exactPlan.usedInventory()); }
    @Override public KeyCounter emittedItems() { return BigIntegerPlanProjection.projectKeyCounter(exactPlan.emitted()); }
    @Override public KeyCounter missingItems() { return new KeyCounter(); }
    @Override public Map<IPatternDetails, Long> patternTimes() { return BigIntegerPlanProjection.projectPatternCounter(times); }

    // Only the server-thread submission adapter prepares this. Aliased facades
    // share the same claim, so they cannot turn one order into two physical jobs.
    BigIntegerCraftingPlan prepared() { return prepared; }
    void rememberPrepared(BigIntegerCraftingPlan plan) {
        if (prepared != null) throw new IllegalStateException("VM physical result is already prepared");
        prepared = Objects.requireNonNull(plan);
    }
}
