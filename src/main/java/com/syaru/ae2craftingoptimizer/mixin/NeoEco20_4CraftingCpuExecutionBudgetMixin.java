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

/** Neo ECO 20.4.xのlong Fast Pathを含む公開executeCrafting記述子専用Mixin。 */
@Pseudo
@Mixin(targets = "cn.dancingsnow.neoecoae.api.me.ECOCraftingCPULogic", remap = false)
public abstract class NeoEco20_4CraftingCpuExecutionBudgetMixin {
    @Unique
    private CraftingService aco$neoEcoCraftingService;

    @Unique
    private int aco$neoEcoRequestedOperations;

    @Unique
    private long aco$neoEcoExecutionStartedAt;

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
    }

    @Inject(
            method = "getOperationLimit()I",
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0)
    private void aco$limitNeoEcoOperations(CallbackInfoReturnable<Integer> callbackInfo) {
        NeoEcoExecutionBudgetSupport.LimitDecision decision = NeoEcoExecutionBudgetSupport.limitOperations(
                this,
                aco$neoEcoCraftingService,
                callbackInfo.getReturnValueI(),
                false);
        aco$neoEcoRequestedOperations = Math.max(
                aco$neoEcoRequestedOperations,
                decision.requestedOperations());
        callbackInfo.setReturnValue(decision.limitedOperations());
    }

    @Inject(
            method = "executeCrafting(ILappeng/me/service/CraftingService;Lappeng/api/networking/energy/IEnergyService;Lnet/minecraft/world/level/Level;)I",
            at = @At("HEAD"),
            remap = false,
            require = 0)
    private void aco$beginNeoEcoExecution(CallbackInfoReturnable<Integer> callbackInfo) {
        aco$neoEcoExecutionStartedAt = NeoEcoExecutionBudgetSupport.beginExecution();
    }

    @Inject(
            method = "executeCrafting(ILappeng/me/service/CraftingService;Lappeng/api/networking/energy/IEnergyService;Lnet/minecraft/world/level/Level;)I",
            at = @At("RETURN"),
            remap = false,
            require = 0)
    private void aco$finishNeoEcoExecution(CallbackInfoReturnable<Integer> callbackInfo) {
        // 20.4は一回のFast Path受理量をlongで会計するが、戻り値は実際のProvider dispatch件数である。
        NeoEcoExecutionBudgetSupport.recordExecution(
                this,
                aco$neoEcoCraftingService,
                aco$neoEcoRequestedOperations,
                false,
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
    }
}
