package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.IGrid;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.syaru.ae2craftingoptimizer.access.CraftingClusterRecoveryAccess;
import com.syaru.ae2craftingoptimizer.access.CraftingOwnerTransactionAccess;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = CraftingCPUCluster.class, remap = false)
public abstract class CraftingCpuClusterTransactionAccessMixin
        implements CraftingOwnerTransactionAccess, CraftingClusterRecoveryAccess {
    @Shadow
    @Final
    public CraftingCpuLogic craftingLogic;

    @Shadow
    public abstract void markDirty();

    @Shadow
    public abstract IGrid getGrid();

    @Shadow
    public abstract BlockPos getBoundsMin();

    @Override
    public Object aco$getCraftingLogic() {
        return craftingLogic;
    }

    @Override
    public void aco$markCraftingOwnerDirty() {
        markDirty();
    }

    @Override
    public IGrid aco$getCraftingGrid() {
        return getGrid();
    }

    @Override
    public BlockPos aco$getCraftingClusterPosition() {
        return getBoundsMin();
    }

    @Override
    public String aco$getCraftingOwnerKind() {
        return "ae2";
    }

    @Override
    public UUID aco$getCraftingOwnerId() {
        return null;
    }

    @Override
    public CraftingOwnerTransactionAccess aco$findCraftingOwner(String ownerKind, UUID ownerId) {
        return "ae2".equals(ownerKind) && ownerId == null ? this : null;
    }
}
