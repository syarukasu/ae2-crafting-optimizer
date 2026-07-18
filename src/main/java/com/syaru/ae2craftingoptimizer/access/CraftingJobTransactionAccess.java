package com.syaru.ae2craftingoptimizer.access;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.inv.ICraftingInventory;
import java.util.Map;

public interface CraftingJobTransactionAccess {
    Map<IPatternDetails, Object> aco$getTasks();

    ICraftingInventory aco$getWaitingFor();
}
