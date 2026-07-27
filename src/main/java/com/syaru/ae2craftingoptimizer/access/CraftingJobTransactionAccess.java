package com.syaru.ae2craftingoptimizer.access;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.crafting.inv.ICraftingInventory;
import java.util.Map;
import java.util.UUID;

public interface CraftingJobTransactionAccess {
    Map<IPatternDetails, Object> aco$getTasks();

    ICraftingInventory aco$getWaitingFor();

    long aco$getWaitingForAmount(AEKey key);

    UUID aco$getCraftingJobId();
}
