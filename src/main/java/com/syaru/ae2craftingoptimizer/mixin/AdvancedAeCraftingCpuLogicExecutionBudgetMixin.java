package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.energy.IEnergyService;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.optimization.CraftingExecutionBudget;
import com.syaru.ae2craftingoptimizer.optimization.SequentialInstantDispatcher;
import com.syaru.ae2craftingoptimizer.optimization.ServerTickClock;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Advanced AE CPUをACOの操作数・時間予算へ参加させる。
 * Quantum Computer固有の並列数は変更せず、そのtickで実際に消費できる上限だけを狭める。
 */
@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic", remap = false)
public abstract class AdvancedAeCraftingCpuLogicExecutionBudgetMixin {
    @Shadow(remap = false)
    public abstract int executeCrafting(
            int maxPatterns,
            CraftingService craftingService,
            IEnergyService energyService,
            Level level);

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

    @Redirect(
            method = "tickCraftingLogic",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/pedroksl/advanced_ae/common/logic/AdvCraftingCPULogic;executeCrafting(ILappeng/me/service/CraftingService;Lappeng/api/networking/energy/IEnergyService;Lnet/minecraft/world/level/Level;)I"),
            remap = false,
            require = 0)
    private int aco$dispatchAdvancedAeInstantWave(
            @Coerce Object logic,
            int maxPatterns,
            CraftingService craftingService,
            IEnergyService energyService,
            Level level) {
        // AdvancedAEもAE2と同じ元会計を使用し、Quantum Computerの巨大な並列数を
        // 固定回数で切らず、計測波と時間予算で次tickへ分割する。
        return SequentialInstantDispatcher.executeWave(
                this,
                maxPatterns,
                craftingService,
                ServerTickClock.currentTick(),
                limitedOperations -> executeCrafting(
                        limitedOperations, craftingService, energyService, level));
    }
}
