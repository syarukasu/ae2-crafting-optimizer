package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ICraftingInventory;
import appeng.crafting.inv.ListCraftingInventory;
import com.syaru.ae2craftingoptimizer.access.CraftingJobTransactionAccess;
import java.util.Map;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = ExecutingCraftingJob.class, remap = false)
public abstract class ExecutingCraftingJobTransactionAccessMixin implements CraftingJobTransactionAccess {
    @Shadow
    @Final
    private Map<IPatternDetails, Object> tasks;

    @Shadow
    @Final
    private ListCraftingInventory waitingFor;

    @Override
    public Map<IPatternDetails, Object> aco$getTasks() {
        return tasks;
    }

    @Override
    public ICraftingInventory aco$getWaitingFor() {
        return waitingFor;
    }
}
