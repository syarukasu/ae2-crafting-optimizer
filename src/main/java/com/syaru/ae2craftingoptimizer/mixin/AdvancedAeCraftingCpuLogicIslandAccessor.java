package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.stacks.AEKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * AdvancedAE CPUのTask一括変更を、監視GUIへ一度だけ通知する。
 */
@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic", remap = false)
public interface AdvancedAeCraftingCpuLogicIslandAccessor {
    @Invoker("postChange")
    void aco$invokePostChange(AEKey key);
}
