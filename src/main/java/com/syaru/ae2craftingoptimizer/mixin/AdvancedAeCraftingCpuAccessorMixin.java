package com.syaru.ae2craftingoptimizer.mixin;

import java.util.UUID;
import net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPUCluster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reflectionを使わず、対象バージョンで確認済みの子CPU識別子と所属Clusterだけを公開する。 */
@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU", remap = false)
public interface AdvancedAeCraftingCpuAccessorMixin {
    @Accessor("uniqueId")
    UUID aco$getUniqueId();

    @Accessor("cluster")
    AdvCraftingCPUCluster aco$getCluster();
}
