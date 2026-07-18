package com.syaru.ae2craftingoptimizer.mixin;

import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.syaru.ae2craftingoptimizer.access.CraftingClusterHostTransactionAccess;
import com.syaru.ae2craftingoptimizer.access.CraftingClusterRecoveryAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = CraftingBlockEntity.class, remap = false)
public abstract class CraftingBlockEntityTransactionAccessMixin implements CraftingClusterHostTransactionAccess {
    @Shadow
    public abstract CraftingCPUCluster getCluster();

    @Override
    public CraftingClusterRecoveryAccess aco$getCraftingClusterForRecovery() {
        return (CraftingClusterRecoveryAccess) (Object) getCluster();
    }
}
