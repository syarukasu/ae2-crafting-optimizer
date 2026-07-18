package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.CraftingCalculation;
import com.syaru.ae2craftingoptimizer.optimization.CraftingCalculationDiagnostics;
import com.syaru.ae2craftingoptimizer.engine.Ae2CraftingShadowValidator;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CraftingCalculation.class, remap = false)
public abstract class CraftingCalculationDiagnosticsMixin {
    @Shadow
    @Final
    private AEKey output;

    @Shadow
    @Final
    private long requestedAmount;

    @Shadow
    @Final
    private CalculationStrategy strategy;

    @Unique
    private long aco$calculationStartedAt;

    @Unique
    private Ae2CraftingShadowValidator.Capture aco$shadowCapture;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void aco$captureGrid(
            Level level,
            IGrid grid,
            ICraftingSimulationRequester requester,
            GenericStack output,
            CalculationStrategy strategy,
            CallbackInfo ci) {
        aco$shadowCapture = Ae2CraftingShadowValidator.capture(level, grid);
    }

    @Inject(method = "run", at = @At("HEAD"))
    private void aco$startCalculationTimer(CallbackInfoReturnable<ICraftingPlan> cir) {
        aco$calculationStartedAt = System.nanoTime();
    }

    @Inject(method = "run", at = @At("RETURN"))
    private void aco$logSlowCalculation(CallbackInfoReturnable<ICraftingPlan> cir) {
        CraftingCalculationDiagnostics.logIfSlow(
                output,
                requestedAmount,
                cir.getReturnValue(),
                System.nanoTime() - aco$calculationStartedAt);
        Ae2CraftingShadowValidator.validate(
                aco$shadowCapture, output, requestedAmount, cir.getReturnValue());
    }
}
