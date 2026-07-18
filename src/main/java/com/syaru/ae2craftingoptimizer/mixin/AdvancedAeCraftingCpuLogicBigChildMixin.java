package com.syaru.ae2craftingoptimizer.mixin;

import com.syaru.ae2craftingoptimizer.integration.AqeBigCraftingExecutionManager;
import net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Advanced AEが子Jobの出力会計を終えた後にだけBigInteger側のExecutionを確定する。 */
@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic", remap = false)
public abstract class AdvancedAeCraftingCpuLogicBigChildMixin {
    @Shadow
    @Final
    private AdvCraftingCPU cpu;

    @Inject(method = "finishJob", at = @At("RETURN"))
    private void aco$resolveBigChild(boolean successful, CallbackInfo ci) {
        AdvancedAeCraftingCpuAccessorMixin access =
                (AdvancedAeCraftingCpuAccessorMixin) (Object) cpu;
        AqeBigCraftingExecutionManager.onChildFinished(
                access.aco$getCluster(), access.aco$getUniqueId(), successful);
    }
}
