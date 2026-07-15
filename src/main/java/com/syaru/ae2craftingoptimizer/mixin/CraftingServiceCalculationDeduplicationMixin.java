package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.optimization.CraftingCalculationDeduplicator;
import com.syaru.ae2craftingoptimizer.optimization.DeterministicCraftingPreflight;
import java.util.concurrent.Future;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingService.class, remap = false)
public abstract class CraftingServiceCalculationDeduplicationMixin {
    @Shadow
    @Final
    private IGrid grid;

    @Inject(method = "beginCraftingCalculation", at = @At("HEAD"), cancellable = true)
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
            return;
        }

        Future<ICraftingPlan> fastFailedPlan = DeterministicCraftingPreflight.tryFastFail(
                (CraftingService) (Object) this,
                grid,
                level,
                requester,
                output,
                amount,
                strategy);
        if (fastFailedPlan != null) {
            cir.setReturnValue(fastFailedPlan);
        }
    }

    @Inject(method = "beginCraftingCalculation", at = @At("RETURN"))
    private void aco$rememberActiveCraftingCalculation(
            Level level,
            ICraftingSimulationRequester requester,
            AEKey output,
            long amount,
            CalculationStrategy strategy,
            CallbackInfoReturnable<Future<ICraftingPlan>> cir) {
        CraftingCalculationDeduplicator.remember(
                (CraftingService) (Object) this,
                level,
                requester,
                output,
                amount,
                strategy,
                cir.getReturnValue());
    }
}
