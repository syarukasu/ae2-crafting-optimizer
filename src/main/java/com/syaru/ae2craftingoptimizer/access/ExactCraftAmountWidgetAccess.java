package com.syaru.ae2craftingoptimizer.access;

import java.math.BigInteger;
import java.util.Optional;

/** Opt-in for the crafting amount widget only. */
public interface ExactCraftAmountWidgetAccess {
    void aco$enableExactCraftAmounts();

    Optional<BigInteger> aco$getExactCraftAmount();

    void aco$setExactCraftAmount(BigInteger amount);
}
