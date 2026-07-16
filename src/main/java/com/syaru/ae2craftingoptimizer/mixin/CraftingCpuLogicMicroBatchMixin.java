package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.energy.IEnergyService;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.optimization.BatchedCraftingExecutor;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingCpuLogic.class, remap = false)
public abstract class CraftingCpuLogicMicroBatchMixin {
    @Inject(method = "executeCrafting", at = @At("HEAD"), cancellable = true, require = 0)
    private void aco$tryPatternMicroBatch(
            int maxPatterns,
            CraftingService craftingService,
            IEnergyService energyService,
            Level level,
            CallbackInfoReturnable<Integer> cir) {
        int result = BatchedCraftingExecutor.tryExecute(this, maxPatterns, craftingService, energyService, level);
        if (result != BatchedCraftingExecutor.NOT_HANDLED) {
            cir.setReturnValue(result);
        }
    }
}
