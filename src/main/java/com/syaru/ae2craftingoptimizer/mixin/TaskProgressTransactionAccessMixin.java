package com.syaru.ae2craftingoptimizer.mixin;

import com.syaru.ae2craftingoptimizer.access.CraftingTaskProgressAccess;
import com.syaru.ae2craftingoptimizer.engine.ExactCraftingJobLedger;
import java.math.BigInteger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@Mixin(targets = "appeng.crafting.execution.ExecutingCraftingJob$TaskProgress", remap = false)
public abstract class TaskProgressTransactionAccessMixin implements CraftingTaskProgressAccess {
    @Shadow
    private long value;

    /** 通常Jobではnull、Issue #115のexact Jobではこの値が残タスク数の正本になる。 */
    @Unique
    private BigInteger aco$exactValue;

    @Override
    public long aco$getTaskProgress() {
        return value;
    }

    @Override
    public void aco$setTaskProgress(long value) {
        this.value = value;
    }

    @Override
    public BigInteger aco$getExactTaskProgress() {
        return aco$exactValue == null ? BigInteger.valueOf(value) : aco$exactValue;
    }

    @Override
    public void aco$setExactTaskProgress(BigInteger value) {
        aco$exactValue = value;
        // AE2自身が読むlong欄は互換Facadeとして常に0..Long.MAX_VALUEへ収める。
        this.value = ExactCraftingJobLedger.saturatedLong(value);
    }
}
