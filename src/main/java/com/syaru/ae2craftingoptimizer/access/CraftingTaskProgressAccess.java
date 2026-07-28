package com.syaru.ae2craftingoptimizer.access;

import com.syaru.ae2craftingoptimizer.engine.ExactCraftingJobLedger;
import java.math.BigInteger;

public interface CraftingTaskProgressAccess {
    long aco$getTaskProgress();

    void aco$setTaskProgress(long value);

    default BigInteger aco$getExactTaskProgress() {
        return BigInteger.valueOf(aco$getTaskProgress());
    }

    default void aco$setExactTaskProgress(BigInteger value) {
        aco$setTaskProgress(ExactCraftingJobLedger.saturatedLong(value));
    }
}
