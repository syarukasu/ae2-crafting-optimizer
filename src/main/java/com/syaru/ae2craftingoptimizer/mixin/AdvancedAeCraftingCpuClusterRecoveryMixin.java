package com.syaru.ae2craftingoptimizer.mixin;

import com.syaru.ae2craftingoptimizer.access.CraftingClusterRecoveryAccess;
import com.syaru.ae2craftingoptimizer.access.CraftingOwnerTransactionAccess;
import java.util.HashMap;
import java.util.UUID;
import net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPUCluster", remap = false)
public abstract class AdvancedAeCraftingCpuClusterRecoveryMixin implements CraftingClusterRecoveryAccess {
    @Shadow
    @Final
    private HashMap<UUID, AdvCraftingCPU> activeCpus;

    @Override
    public CraftingOwnerTransactionAccess aco$findCraftingOwner(String ownerKind, UUID ownerId) {
        if (!"advanced_ae".equals(ownerKind) || ownerId == null) {
            return null;
        }
        return (CraftingOwnerTransactionAccess) activeCpus.get(ownerId);
    }
}
