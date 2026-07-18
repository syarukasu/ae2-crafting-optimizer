package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.energy.IEnergyService;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.optimization.TransactionalCraftingExecutorV2;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * AE2 CPUのPattern配送直前にV2 Batchを試す入口。
 * ExecutorがNOT_HANDLEDを返した場合はキャンセルせず、AE2本来のexecuteCraftingをそのまま実行する。
 */
@Mixin(value = CraftingCpuLogic.class, remap = false)
public abstract class CraftingCpuLogicTransactionalBatchV2Mixin {
    @Inject(method = "executeCrafting", at = @At("HEAD"), cancellable = true, require = 0)
    private void aco$tryTransactionalPatternBatchV2(
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
