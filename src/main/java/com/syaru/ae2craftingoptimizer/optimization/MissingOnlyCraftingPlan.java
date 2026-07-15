package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import java.util.Map;

public final class MissingOnlyCraftingPlan implements ICraftingPlan {
    private final GenericStack finalOutput;
    private final KeyCounter missingItems;

    public MissingOnlyCraftingPlan(AEKey output, long requestedAmount, AEKey missingKey, long missingAmount) {
        this.finalOutput = new GenericStack(output, requestedAmount);
        this.missingItems = new KeyCounter();
        this.missingItems.add(missingKey, Math.max(1L, missingAmount));
    }

    @Override
    public GenericStack finalOutput() {
        return finalOutput;
    }

    @Override
    public long bytes() {
        return 0;
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
        return new KeyCounter();
    }

    @Override
    public KeyCounter emittedItems() {
        return new KeyCounter();
    }

    @Override
    public KeyCounter missingItems() {
        return missingItems;
    }

    @Override
    public Map<IPatternDetails, Long> patternTimes() {
        return Map.of();
    }
}
