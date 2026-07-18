package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.energy.IEnergyService;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.optimization.TransactionalCraftingExecutorV2;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic", remap = false)
public abstract class AdvancedAeCraftingCpuLogicTransactionalBatchV2Mixin {
    @Inject(method = "executeCrafting", at = @At("HEAD"), cancellable = true, require = 0)
    private void aco$tryAdvancedTransactionalPatternBatchV2(
            int maxPatterns,
            CraftingService craftingService,
            IEnergyService energyService,
            Level level,
            CallbackInfoReturnable<Integer> cir) {
        int result = TransactionalCraftingExecutorV2.tryExecute(
                this, maxPatterns, craftingService, energyService, level);
        if (result != TransactionalCraftingExecutorV2.NOT_HANDLED) {
            cir.setReturnValue(result);
        }
    }
}
