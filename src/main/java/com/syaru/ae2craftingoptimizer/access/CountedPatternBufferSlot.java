package com.syaru.ae2craftingoptimizer.access;

import appeng.api.stacks.AEKey;
import java.math.BigInteger;
import java.util.Map;

/** Read-only raw long-map admission; does not expand GT's recipe stack views. */
public interface CountedPatternBufferSlot {
    long aco$countedHeadroom(Map<AEKey, BigInteger> inputs, long offered);
}
