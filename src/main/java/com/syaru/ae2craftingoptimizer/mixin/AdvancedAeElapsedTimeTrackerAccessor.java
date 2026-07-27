package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.stacks.AEKeyType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Compiled Islandで実体化しない中間素材の進捗を、AdvancedAE標準Trackerへ反映する。
 */
@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.ElapsedTimeTracker", remap = false)
public interface AdvancedAeElapsedTimeTrackerAccessor {
    @Invoker("decrementItems")
    void aco$invokeDecrementItems(long amount, AEKeyType keyType);
}
