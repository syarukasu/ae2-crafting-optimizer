package com.syaru.ae2craftingoptimizer.access;

import com.syaru.ae2craftingoptimizer.optimization.CraftingCalculationDeduplicator;

/** 各CraftingServiceが自分の計算共有索引を所有するためのMixin境界。 */
public interface CraftingCalculationCacheAccess {
    CraftingCalculationDeduplicator.ServiceState aco$getCraftingCalculationCacheState();
}
