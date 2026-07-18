package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import java.util.Map;

public final class MissingOnlyCraftingPlan {
    private MissingOnlyCraftingPlan() {
    }

    public static CraftingPlan create(
            AEKey output,
            long requestedAmount,
            AEKey missingKey,
            long missingAmount) {
        KeyCounter missingItems = new KeyCounter();
        missingItems.add(missingKey, Math.max(1L, missingAmount));

        return new CraftingPlan(
                new GenericStack(output, requestedAmount),
                0,
                true,
                false,
                new KeyCounter(),
                new KeyCounter(),
                missingItems,
                Map.<IPatternDetails, Long>of());
    }
}
