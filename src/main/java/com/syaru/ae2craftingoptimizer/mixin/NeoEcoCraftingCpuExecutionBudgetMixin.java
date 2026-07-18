package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.energy.IEnergyService;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.optimization.CraftingExecutionBudget;
import com.syaru.ae2craftingoptimizer.optimization.ServerTickClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "cn.dancingsnow.neoecoae.api.me.ECOCraftingCPULogic", remap = false)
public abstract class NeoEcoCraftingCpuExecutionBudgetMixin {
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
        if (!ACOConfig.throttleNeoEcoAeExecution()) {
            return;
        }
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
    private void aco$limitNeoEcoSlowPath(CallbackInfoReturnable<Integer> callbackInfo) {
        aco$applyNeoEcoBudget(callbackInfo);
    }

    @Inject(
            method = "effectiveFastPathTickLimit()I",
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0)
    private void aco$limitNeoEcoFastPath(CallbackInfoReturnable<Integer> callbackInfo) {
        aco$applyNeoEcoBudget(callbackInfo);
    }

    @Inject(
            method = "executeCrafting(IILappeng/me/service/CraftingService;Lappeng/api/networking/energy/IEnergyService;Lnet/minecraft/world/level/Level;Lcn/dancingsnow/neoecoae/api/me/ECOCraftingCPULogic$FastPathBatchBudget;)I",
            at = @At("HEAD"),
            remap = false,
            require = 0)
    private void aco$beginNeoEcoExecution(CallbackInfoReturnable<Integer> callbackInfo) {
        if (ACOConfig.throttleNeoEcoAeExecution()) {
            aco$neoEcoExecutionStartedAt = System.nanoTime();
        }
    }

    @Inject(
            method = "executeCrafting(IILappeng/me/service/CraftingService;Lappeng/api/networking/energy/IEnergyService;Lnet/minecraft/world/level/Level;Lcn/dancingsnow/neoecoae/api/me/ECOCraftingCPULogic$FastPathBatchBudget;)I",
            at = @At("RETURN"),
            remap = false,
            require = 0)
    private void aco$finishNeoEcoExecution(CallbackInfoReturnable<Integer> callbackInfo) {
        long startedAt = aco$neoEcoExecutionStartedAt;
        if (!ACOConfig.throttleNeoEcoAeExecution() || startedAt == 0L) {
            return;
        }

        long elapsedNanos = System.nanoTime() - startedAt;
        int completedOperations = Math.max(0, callbackInfo.getReturnValueI());
        CraftingExecutionBudget.recordExecution(
                this,
                aco$neoEcoRequestedOperations,
                completedOperations,
                elapsedNanos);
        CraftingExecutionBudget.recordSharedExecution(
                aco$neoEcoCraftingService,
                this,
                ServerTickClock.currentTick(),
                elapsedNanos);
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

    @Unique
    private void aco$applyNeoEcoBudget(CallbackInfoReturnable<Integer> callbackInfo) {
        if (!ACOConfig.throttleNeoEcoAeExecution()) {
            return;
        }

        int originalOperations = callbackInfo.getReturnValueI();
        if (originalOperations <= 0) {
            return;
        }

        int perCpuOperations = CraftingExecutionBudget.limitExternalOperations(
                this,
                originalOperations,
                "Neo ECO AE");
        int limitedOperations = CraftingExecutionBudget.limitSharedOperations(
                aco$neoEcoCraftingService,
                this,
                perCpuOperations,
                ServerTickClock.currentTick());
        aco$neoEcoRequestedOperations = Math.max(aco$neoEcoRequestedOperations, limitedOperations);
        callbackInfo.setReturnValue(limitedOperations);
    }
}
