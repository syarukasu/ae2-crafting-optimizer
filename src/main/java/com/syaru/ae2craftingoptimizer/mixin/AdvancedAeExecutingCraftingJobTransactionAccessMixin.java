package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingLink;
import appeng.crafting.inv.ICraftingInventory;
import appeng.crafting.inv.ListCraftingInventory;
import com.syaru.ae2craftingoptimizer.access.CraftingJobTransactionAccess;
import java.util.Map;
import java.util.UUID;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Advanced AEの実Job会計をV2 Pattern Batchへ公開する最小Accessor。
 */
@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob", remap = false)
public abstract class AdvancedAeExecutingCraftingJobTransactionAccessMixin
        implements CraftingJobTransactionAccess {
    @Shadow
    @Final
    private Map<IPatternDetails, Object> tasks;

    @Shadow
    @Final
    private ListCraftingInventory waitingFor;

    @Shadow
    @Final
    private CraftingLink link;

    @Override
    public Map<IPatternDetails, Object> aco$getTasks() {
        return tasks;
    }

    @Override
    public ICraftingInventory aco$getWaitingFor() {
        return waitingFor;
    }

    @Override
    public long aco$getWaitingForAmount(AEKey key) {
        return waitingFor.list.get(key);
    }

    @Override
    public UUID aco$getCraftingJobId() {
        return link.getCraftingID();
    }
}
