package com.syaru.ae2craftingoptimizer.api.big;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import java.math.BigInteger;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable exact view of an ACO wide plan for optional CPU add-ons.
 *
 * <p>The standard AE2 {@link ICraftingPlan} remains the compatibility facade.
 * Add-ons that can execute bounded windows may opt into this view for both
 * BigInteger counters and capacity-only overflow, without depending on ACO's
 * engine implementation classes.</p>
 */
public record BigIntegerCraftingPlanView(
        GenericStack finalOutput,
        BigInteger exactBytes,
        boolean simulation,
        Map<IPatternDetails, BigInteger> patternTimes,
        Map<AEKey, BigInteger> usedItems,
        Map<AEKey, BigInteger> emittedItems,
        Map<AEKey, BigInteger> missingItems) {

    public BigIntegerCraftingPlanView {
        Objects.requireNonNull(finalOutput, "finalOutput");
        Objects.requireNonNull(exactBytes, "exactBytes");
        Objects.requireNonNull(patternTimes, "patternTimes");
        Objects.requireNonNull(usedItems, "usedItems");
        Objects.requireNonNull(emittedItems, "emittedItems");
        Objects.requireNonNull(missingItems, "missingItems");
        if (exactBytes.signum() < 0) {
            throw new IllegalArgumentException("exactBytes must not be negative");
        }
        patternTimes = Map.copyOf(patternTimes);
        usedItems = Map.copyOf(usedItems);
        emittedItems = Map.copyOf(emittedItems);
        missingItems = Map.copyOf(missingItems);
    }
}
