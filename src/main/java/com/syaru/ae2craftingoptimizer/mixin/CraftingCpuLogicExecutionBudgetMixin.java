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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * AE2が一tickで配送するPattern数だけをCPU・Gridの時間予算へ収める。
 * CPU容量、表示上のコプロセッサ数、Pattern内容、クラフト成否は変更しない。
 */
/*
 * issue #102/#123: 同じAE2実行箇所を専門アドオンもRedirectする環境でACOが先に取得すると、
 * 相手側の巨大CPU・一括処理とAE2標準の面ラウンドロビンを壊していた。
 * Mixin標準優先度1000より低い900にして、競合時は実行経路の所有者へ譲る。
 * ACO単独環境では競合がないため、このMixinは従来どおり適用される。
 */
@Mixin(value = CraftingCpuLogic.class, remap = false, priority = 900)
public abstract class CraftingCpuLogicExecutionBudgetMixin {
    /** ACOがこのCraftingCpuLogicの実行予算境界を実際に所有できた場合だけtrue。 */
    @Unique
    private boolean aco$ownsExecutionBudgetHook;

    @Redirect(
            method = "tickCraftingLogic",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/me/cluster/implementations/CraftingCPUCluster;getCoProcessors()I"),
            // issue #123: 高優先度の専門実装へ譲った場合だけ0件を許可する。
            require = 0,
            expect = 1)
    private int aco$limitAe2CraftingExecution(CraftingCPUCluster cluster) {
        aco$ownsExecutionBudgetHook = true;
        return CraftingExecutionBudget.limitCoProcessors(this, cluster, cluster.getCoProcessors());
    }

    @Redirect(
            method = "tickCraftingLogic",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/crafting/execution/CraftingCpuLogic;executeCrafting(ILappeng/me/service/CraftingService;Lappeng/api/networking/energy/IEnergyService;Lnet/minecraft/world/level/Level;)I"),
            require = 1)
    private int aco$recordAe2CraftingExecution(
            CraftingCpuLogic logic,
            int maxOperations,
            CraftingService craftingService,
            IEnergyService energyService,
            Level level) {
        // issue #102/#123: 高優先度の専門実装へ予算所有権を譲ったCPUにはACOの再制限を重ねない。
        if (!aco$ownsExecutionBudgetHook) {
            return logic.executeCrafting(maxOperations, craftingService, energyService, level);
        }

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
