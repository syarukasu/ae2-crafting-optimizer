package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.energy.IEnergyService;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.optimization.CraftingExecutionBudget;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = CraftingCpuLogic.class, remap = false)
public abstract class CraftingCpuLogicExecutionBudgetMixin {
    @Redirect(
            method = "tickCraftingLogic",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/me/cluster/implementations/CraftingCPUCluster;getCoProcessors()I"),
            require = 0)
    private int aco$limitAe2CraftingExecution(CraftingCPUCluster cluster) {
        return CraftingExecutionBudget.limitCoProcessors(this, cluster, cluster.getCoProcessors());
    }

    @Redirect(
            method = "tickCraftingLogic",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/crafting/execution/CraftingCpuLogic;executeCrafting(ILappeng/me/service/CraftingService;Lappeng/api/networking/energy/IEnergyService;Lnet/minecraft/world/level/Level;)I"),
            require = 0)
    private int aco$recordAe2CraftingExecution(
            CraftingCpuLogic logic,
            int maxOperations,
            CraftingService craftingService,
            IEnergyService energyService,
            Level level) {
        long startedAt = System.nanoTime();
        int completedOperations = logic.executeCrafting(maxOperations, craftingService, energyService, level);
        CraftingExecutionBudget.recordExecution(this, maxOperations, completedOperations, System.nanoTime() - startedAt);
        return completedOperations;
    }
}
