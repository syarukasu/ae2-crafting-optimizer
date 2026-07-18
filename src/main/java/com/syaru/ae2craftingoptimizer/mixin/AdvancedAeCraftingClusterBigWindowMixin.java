package com.syaru.ae2craftingoptimizer.mixin;

import com.syaru.ae2craftingoptimizer.api.big.BigCraftingHostRegistry;
import com.syaru.ae2craftingoptimizer.integration.AqeBigCraftingExecutionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Big Jobが既に予約した容量から、現在の子Job分だけをAdvanced AEの通常判定へ貸し出す。 */
@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPUCluster", remap = false)
public abstract class AdvancedAeCraftingClusterBigWindowMixin {
    @Inject(method = "getAvailableStorage", at = @At("HEAD"), cancellable = true)
    private void aco$exposeCurrentBigChildAllowance(CallbackInfoReturnable<Long> cir) {
        long allowance = AqeBigCraftingExecutionContext.allowanceFor(this);
        if (allowance <= 0L) {
            return;
        }
        BigCraftingHostRegistry.find(this).ifPresent(host -> {
            long available = host.availableAsSaturatedLong();
            long combined = available > Long.MAX_VALUE - allowance
                    ? Long.MAX_VALUE
                    : available + allowance;
            cir.setReturnValue(combined);
        });
    }
}
