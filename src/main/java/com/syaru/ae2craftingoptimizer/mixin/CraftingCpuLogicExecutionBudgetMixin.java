package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.energy.IEnergyService;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.optimization.CraftingExecutionBudget;
import com.syaru.ae2craftingoptimizer.optimization.SequentialInstantDispatcher;
import com.syaru.ae2craftingoptimizer.optimization.ServerTickClock;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * AE2が一tickで配送するPattern数だけをCPU・Gridの時間予算へ収める。
 * CPU容量、表示上のコプロセッサ数、Pattern内容、クラフト成否は変更しない。
 */
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
        // AE2本来のexecuteCraftingへ処理を委譲し、外側do/whileの一波ごとにだけ時間を測る。
        // これによりtask/waitingFor/電力会計を複製せず、次の波を安全に次tickへ送れる。
        return SequentialInstantDispatcher.executeWave(
                this,
                maxOperations,
                craftingService,
                ServerTickClock.currentTick(),
                limitedOperations -> logic.executeCrafting(
                        limitedOperations, craftingService, energyService, level));
    }
}
