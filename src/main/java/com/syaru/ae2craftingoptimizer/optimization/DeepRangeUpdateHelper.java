package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.stacks.AEKey;
import java.util.Set;

public interface DeepRangeUpdateHelper {
    Set<AEKey> aco$getMutableChanges();

    void aco$finishRangeBatch(boolean hasPendingChanges);
}
