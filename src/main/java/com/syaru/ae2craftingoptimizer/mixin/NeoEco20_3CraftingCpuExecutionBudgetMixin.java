package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.energy.IEnergyService;
import appeng.me.service.CraftingService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Neo ECO 20.3.xの旧FastPathBatchBudget記述子専用Mixin。 */
@Pseudo
@Mixin(targets = "cn.dancingsnow.neoecoae.api.me.ECOCraftingCPULogic", remap = false)
public abstract class NeoEco20_3CraftingCpuExecutionBudgetMixin {
    @Unique
    private CraftingService aco$neoEcoCraftingService;

    @Unique
    private int aco$neoEcoRequestedOperations;

    @Unique
    private long aco$neoEcoExecutionStartedAt;

    @Unique
    private boolean aco$neoEcoVectorBatch;

    @Inject(
            method = "tickCraftingLogic(Lappeng/api/networking/energy/IEnergyService;Lappeng/me/service/CraftingService;)V",
            at = @At("HEAD"),
            remap = false,
            require = 0)
    private void aco$beginNeoEcoTick(
            IEnergyService energyService,
            CraftingService craftingService,
            CallbackInfo callbackInfo) {
        aco$neoEcoCraftingService = craftingService;
        aco$neoEcoRequestedOperations = 0;
        aco$neoEcoExecutionStartedAt = 0L;
        aco$neoEcoVectorBatch = false;
    }

    @Inject(
            method = "getOperationLimit()I",
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0)
    private void aco$limitNeoEcoSlowPath(CallbackInfoReturnable<Integer> callbackInfo) {
        aco$applyNeoEcoBudget(callbackInfo, false);
    }

    @Inject(
            method = "effectiveFastPathTickLimit()I",
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0)
    private void aco$limitNeoEcoFastPath(CallbackInfoReturnable<Integer> callbackInfo) {
        aco$applyNeoEcoBudget(callbackInfo, true);
    }

    @Inject(
            method = "executeCrafting(IILappeng/me/service/CraftingService;Lappeng/api/networking/energy/IEnergyService;Lnet/minecraft/world/level/Level;Lcn/dancingsnow/neoecoae/api/me/ECOCraftingCPULogic$FastPathBatchBudget;)I",
            at = @At("HEAD"),
            remap = false,
            require = 0)
    private void aco$beginNeoEcoExecution(CallbackInfoReturnable<Integer> callbackInfo) {
        aco$neoEcoExecutionStartedAt = NeoEcoExecutionBudgetSupport.beginExecution();
    }

    @Inject(
            method = "executeCrafting(IILappeng/me/service/CraftingService;Lappeng/api/networking/energy/IEnergyService;Lnet/minecraft/world/level/Level;Lcn/dancingsnow/neoecoae/api/me/ECOCraftingCPULogic$FastPathBatchBudget;)I",
            at = @At("RETURN"),
            remap = false,
            require = 0)
    private void aco$finishNeoEcoExecution(CallbackInfoReturnable<Integer> callbackInfo) {
        NeoEcoExecutionBudgetSupport.recordExecution(
                this,
                aco$neoEcoCraftingService,
                aco$neoEcoRequestedOperations,
                aco$neoEcoVectorBatch,
                aco$neoEcoExecutionStartedAt,
                callbackInfo.getReturnValueI());
        aco$neoEcoExecutionStartedAt = 0L;
    }

    @Inject(
            method = "tickCraftingLogic(Lappeng/api/networking/energy/IEnergyService;Lappeng/me/service/CraftingService;)V",
            at = @At("RETURN"),
            remap = false,
            require = 0)
    private void aco$finishNeoEcoTick(
            IEnergyService energyService,
            CraftingService craftingService,
            CallbackInfo callbackInfo) {
        aco$neoEcoCraftingService = null;
        aco$neoEcoExecutionStartedAt = 0L;
        aco$neoEcoVectorBatch = false;
    }

    @Unique
    private void aco$applyNeoEcoBudget(
            CallbackInfoReturnable<Integer> callbackInfo,
            boolean fastPath) {
        NeoEcoExecutionBudgetSupport.LimitDecision decision = NeoEcoExecutionBudgetSupport.limitOperations(
                this,
                aco$neoEcoCraftingService,
                callbackInfo.getReturnValueI(),
                fastPath);
        aco$neoEcoRequestedOperations = Math.max(
                aco$neoEcoRequestedOperations,
                decision.requestedOperations());
        aco$neoEcoVectorBatch |= decision.vectorBatch();
        callbackInfo.setReturnValue(decision.limitedOperations());
    }
}
