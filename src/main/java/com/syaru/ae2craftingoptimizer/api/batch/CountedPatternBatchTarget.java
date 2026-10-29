package com.syaru.ae2craftingoptimizer.api.batch;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import java.math.BigInteger;
import java.util.Map;

/** Opt-in: the native provider accepts scaled inputs without changing pattern semantics. */
public interface CountedPatternBatchTarget {
    /** Read live headroom without mutation. Return -1 when the contract cannot be certified. */
    long aco$countedExecutionLimit(IPatternDetails pattern, Map<AEKey, BigInteger> inputs, long offered);
}
