package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.optimization.AdvancedAeCraftingInvoker;
import com.syaru.ae2craftingoptimizer.optimization.CraftingExecutionBudget;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic", remap = false)
public abstract class AdvancedAeCraftingCpuLogicExecutionBudgetMixin {
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
    private int aco$recordAdvancedAeCraftingExecution(
            @Coerce Object logic,
            int maxOperations,
            CraftingService craftingService,
            IEnergyService energyService,
            Level level) {
        long startedAt = System.nanoTime();
        int completedOperations = AdvancedAeCraftingInvoker.executeCrafting(
                logic,
                maxOperations,
                craftingService,
                energyService,
                level);
        CraftingExecutionBudget.recordExecution(this, maxOperations, completedOperations, System.nanoTime() - startedAt);
        return completedOperations;
    }
}
