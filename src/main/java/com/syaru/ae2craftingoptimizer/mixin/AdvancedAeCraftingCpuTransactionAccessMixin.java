package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.IGrid;
import com.syaru.ae2craftingoptimizer.access.CraftingOwnerTransactionAccess;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU;
import net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPUCluster;
import net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU", remap = false)
public abstract class AdvancedAeCraftingCpuTransactionAccessMixin implements CraftingOwnerTransactionAccess {
    @Shadow
    @Final
    private UUID uniqueId;

    @Shadow
    @Final
    private AdvCraftingCPUCluster cluster;

    @Shadow
    @Final
    public AdvCraftingCPULogic craftingLogic;

    @Override
    public Object aco$getCraftingLogic() {
        return craftingLogic;
    }

    @Override
    public void aco$markCraftingOwnerDirty() {
        ((AdvCraftingCPU) (Object) this).markDirty();
    }

    @Override
    public IGrid aco$getCraftingGrid() {
        return ((AdvCraftingCPU) (Object) this).getGrid();
    }

    @Override
    public BlockPos aco$getCraftingClusterPosition() {
        return cluster.getBoundsMin();
    }

    @Override
    public String aco$getCraftingOwnerKind() {
        return "advanced_ae";
    }

    @Override
    public UUID aco$getCraftingOwnerId() {
        return uniqueId;
    }
}
