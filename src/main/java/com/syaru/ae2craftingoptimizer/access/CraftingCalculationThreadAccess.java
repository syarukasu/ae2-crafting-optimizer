package com.syaru.ae2craftingoptimizer.access;

import appeng.api.networking.crafting.ICraftingService;

/** Issue #179: 計算workerからlive Pattern境界だけをServerへ渡す。数量計算は所有しない。 */
public interface CraftingCalculationThreadAccess {
    void aco$checkpointIngredientSearch() throws InterruptedException;

    boolean aco$runPatternLookup(Runnable lookup);

    void aco$observeCraftingService(ICraftingService service);
}
