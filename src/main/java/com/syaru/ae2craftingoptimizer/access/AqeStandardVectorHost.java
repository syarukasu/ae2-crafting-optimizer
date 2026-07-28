package com.syaru.ae2craftingoptimizer.access;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.crafting.inv.ListCraftingInventory;
import java.util.Collection;
import net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU;

/**
 * ACOの標準AQE Vector Runtimeと、AdvancedAE CPU private状態をつなぐPort。
 */
public interface AqeStandardVectorHost {
    AdvCraftingCPU aco$getStandardVectorCpu();

    Object aco$getStandardVectorJob();

    ListCraftingInventory aco$getStandardVectorInventory();

    long aco$insertStandardVectorOutput(
            AEKey key,
            long amount,
            Actionable actionable);

    void aco$notifyStandardVectorDisplayChanges(
            Collection<AEKey> keys);

    void aco$setStandardVectorDisplay(
            long startItemCount,
            long remainingItemCount,
            float progress);

    void aco$clearStandardVectorDisplay();

    void aco$markStandardVectorDirty();
}
