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
 * Advanced AE CPUをACOの時間予算へ参加させる。
 *
 * <p>クラフト会計はAdvanced AE自身とV2 Pattern Batchへ委譲し、
 * このMixinは一tickの配送量だけを制御する。</p>
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
        return CraftingExecutionBudget.limitCoProcessors(
                this,
                craftingCpu,
                craftingCpu.getCoProcessors());
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
        /*
         * 実Pattern配送は元のexecuteCraftingへ通す。
         * ACO V2が対応Patternを所有した時だけ、その呼出し内で正確な一括会計へ切り替わる。
         */
        return SequentialInstantDispatcher.executeWave(
                this,
                maxPatterns,
                craftingService,
                ServerTickClock.currentTick(),
                limitedOperations -> executeCrafting(
                        limitedOperations,
                        craftingService,
                        energyService,
                        level));
    }
}
