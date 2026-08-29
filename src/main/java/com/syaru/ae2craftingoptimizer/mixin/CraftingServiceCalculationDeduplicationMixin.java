package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.access.CraftingServiceCalculationHookAccess;
import com.syaru.ae2craftingoptimizer.optimization.CraftingCalculationDeduplicator;
import java.util.concurrent.Future;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingService.class, remap = false)
public abstract class CraftingServiceCalculationDeduplicationMixin
        implements CraftingServiceCalculationHookAccess {
    @Shadow
    @Final
    private IGrid grid;

    @Inject(
            method = "beginCraftingCalculation",
            at = @At("HEAD"),
            cancellable = true,
            require = 1)
    private void aco$reuseActiveCraftingCalculation(
            Level level,
            ICraftingSimulationRequester requester,
            AEKey output,
            long amount,
            CalculationStrategy strategy,
            CallbackInfoReturnable<Future<ICraftingPlan>> cir) {
        Future<ICraftingPlan> activeCalculation = CraftingCalculationDeduplicator.findActive(
                (CraftingService) (Object) this,
                level,
                requester,
                output,
                amount,
                strategy);
        if (activeCalculation != null) {
            cir.setReturnValue(activeCalculation);
        }
    }

    @Inject(
            method = "beginCraftingCalculation",
            at = @At("RETURN"),
            cancellable = true,
            require = 1)
    private void aco$rememberActiveCraftingCalculation(
            Level level,
            ICraftingSimulationRequester requester,
            AEKey output,
            long amount,
            CalculationStrategy strategy,
            CallbackInfoReturnable<Future<ICraftingPlan>> cir) {
        Future<ICraftingPlan> returned = CraftingCalculationDeduplicator.remember(
                (CraftingService) (Object) this,
                level,
                requester,
                output,
                amount,
                strategy,
                cir.getReturnValue());
        // 共有Futureを最初の呼出し元へ返し、個別キャンセルが他の利用者を壊さないようにする。
        if (returned != cir.getReturnValue()) {
            cir.setReturnValue(returned);
        }
    }
}
