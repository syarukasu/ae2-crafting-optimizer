package com.syaru.ae2craftingoptimizer.mixin;

import com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferPartMachine;
import com.syaru.ae2craftingoptimizer.access.SharedPatternBufferAmounts;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.gregtechceu.gtceu.integration.ae2.machine.trait.InternalSlotRecipeHandler$SlotFluidRecipeHandler", remap = false)
public class GTBufferFluidTotalMixin {
    @Shadow @Final private MEPatternBufferPartMachine.InternalSlot slot;

    @Inject(method = "getTotalContentAmount", at = @At("HEAD"), cancellable = true, require = 1)
    private void aco$sharedFluidTotal(CallbackInfoReturnable<Double> cir) {
        if (ACOConfig.enableCountedProcessingTransfers()
                && (Object) slot instanceof SharedPatternBufferAmounts amounts)
            cir.setReturnValue(amounts.aco$totalFluidAmount());
    }
}

