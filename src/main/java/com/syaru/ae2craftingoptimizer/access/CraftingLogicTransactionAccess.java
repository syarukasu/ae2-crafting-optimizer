package com.syaru.ae2craftingoptimizer.access;

import appeng.crafting.inv.ICraftingInventory;
import org.jetbrains.annotations.Nullable;

public interface CraftingLogicTransactionAccess {
    @Nullable
    Object aco$getExecutingJob();

    ICraftingInventory aco$getCraftingInventory();

    CraftingOwnerTransactionAccess aco$getCraftingOwner();
}
