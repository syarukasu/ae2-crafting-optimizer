package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.energy.IEnergyService;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.optimization.CraftingExecutionBudget;
import com.syaru.ae2craftingoptimizer.optimization.ServerTickClock;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic", remap = false)
public abstract class AdvancedAeCraftingCpuLogicExecutionBudgetMixin {
    @Unique
    private CraftingService aco$advancedCraftingService;

    @Unique
    private int aco$advancedRequestedOperations;

    @Unique
    private long aco$advancedExecutionStartedAt;

    @Redirect(
            method = "tickCraftingLogic",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/pedroksl/advanced_ae/common/cluster/AdvCraftingCPU;getCoProcessors()I"),
            remap = false,
            require = 0)
    private int aco$limitAdvancedAeCraftingExecution(@Coerce Object cpu) {
        ICraftingCPU craftingCpu = (ICraftingCPU) cpu;
        return CraftingExecutionBudget.limitCoProcessors(this, craftingCpu, craftingCpu.getCoProcessors());
    }

    @Inject(method = "tickCraftingLogic", at = @At("HEAD"), remap = false, require = 0)
    private void aco$beginAdvancedTick(
            IEnergyService energyService,
            CraftingService craftingService,
            CallbackInfo callbackInfo) {
        aco$advancedCraftingService = craftingService;
        aco$advancedRequestedOperations = 0;
        aco$advancedExecutionStartedAt = 0L;
    }

    @ModifyVariable(
            method = "executeCrafting",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false,
            require = 0)
    private int aco$limitAdvancedSharedExecution(int originalOperations) {
        if (!ACOConfig.enableFairCraftingJobScheduler() || originalOperations <= 0) {
            return originalOperations;
        }
        int limitedOperations = CraftingExecutionBudget.limitSharedOperations(
                aco$advancedCraftingService,
                this,
                originalOperations,
                ServerTickClock.currentTick());
        aco$advancedRequestedOperations = limitedOperations;
        return limitedOperations;
    }

    @Inject(method = "executeCrafting", at = @At("HEAD"), remap = false, require = 0)
    private void aco$beginAdvancedExecution(
            int maxPatterns,
            CraftingService craftingService,
            IEnergyService energyService,
            Level level,
            CallbackInfoReturnable<Integer> callbackInfo) {
        if (ACOConfig.enableFairCraftingJobScheduler()) {
            aco$advancedExecutionStartedAt = System.nanoTime();
        }
    }

    @Inject(method = "executeCrafting", at = @At("RETURN"), remap = false, require = 0)
    private void aco$finishAdvancedExecution(
            int maxPatterns,
            CraftingService craftingService,
            IEnergyService energyService,
            Level level,
            CallbackInfoReturnable<Integer> callbackInfo) {
        long startedAt = aco$advancedExecutionStartedAt;
        if (startedAt == 0L) {
            return;
        }
        long elapsedNanos = System.nanoTime() - startedAt;
        int requested = aco$advancedRequestedOperations > 0
                ? aco$advancedRequestedOperations
                : Math.max(0, maxPatterns);
        int completed = Math.max(0, callbackInfo.getReturnValueI());
        CraftingExecutionBudget.recordExecution(this, requested, completed, elapsedNanos);
        CraftingExecutionBudget.recordSharedExecution(
                craftingService, this, ServerTickClock.currentTick(), elapsedNanos);
        aco$advancedExecutionStartedAt = 0L;
    }

    @Inject(method = "tickCraftingLogic", at = @At("RETURN"), remap = false, require = 0)
    private void aco$finishAdvancedTick(
            IEnergyService energyService,
            CraftingService craftingService,
            CallbackInfo callbackInfo) {
        aco$advancedCraftingService = null;
        aco$advancedExecutionStartedAt = 0L;
    }
}
