package com.syaru.ae2craftingoptimizer.mixin;

import com.syaru.ae2craftingoptimizer.access.CraftingClusterHostTransactionAccess;
import com.syaru.ae2craftingoptimizer.access.CraftingClusterRecoveryAccess;
import net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPUCluster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.entities.AdvCraftingBlockEntity", remap = false)
public abstract class AdvancedAeCraftingBlockEntityTransactionAccessMixin
        implements CraftingClusterHostTransactionAccess {
    @Shadow
    public abstract AdvCraftingCPUCluster getCluster();

    @Override
    public CraftingClusterRecoveryAccess aco$getCraftingClusterForRecovery() {
        return (CraftingClusterRecoveryAccess) getCluster();
    }
}
