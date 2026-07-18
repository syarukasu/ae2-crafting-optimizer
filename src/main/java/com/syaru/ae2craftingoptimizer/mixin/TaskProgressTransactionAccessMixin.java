package com.syaru.ae2craftingoptimizer.mixin;

import com.syaru.ae2craftingoptimizer.access.CraftingTaskProgressAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "appeng.crafting.execution.ExecutingCraftingJob$TaskProgress", remap = false)
public abstract class TaskProgressTransactionAccessMixin implements CraftingTaskProgressAccess {
    @Shadow
    private long value;

    @Override
    public long aco$getTaskProgress() {
        return value;
    }

    @Override
    public void aco$setTaskProgress(long value) {
        this.value = value;
    }
}
