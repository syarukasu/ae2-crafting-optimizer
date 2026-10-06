package com.syaru.ae2craftingoptimizer.mixin;

import appeng.crafting.execution.CraftingCpuLogic;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.syaru.ae2craftingoptimizer.optimization.CraftingExecutionBudget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Issue #161: repair the coprocessor int boundary without wrapping native dispatch.
 * AE2 owns operation history, provider order, backpressure and execution throughput.
 */
/*
 * issue #102/#123: 同じAE2実行箇所を専門アドオンもRedirectする環境でACOが先に取得すると、
 * 相手側の巨大CPU・一括処理とAE2標準の面ラウンドロビンを壊していた。
 * Mixin標準優先度1000より低い900にして、競合時は実行経路の所有者へ譲る。
 * ACO単独環境では競合がないため、このMixinは従来どおり適用される。
 */
@Mixin(value = CraftingCpuLogic.class, remap = false, priority = 900)
public abstract class CraftingCpuLogicExecutionBudgetMixin {
    @Redirect(
            method = "tickCraftingLogic",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/me/cluster/implementations/CraftingCPUCluster;getCoProcessors()I"),
            // issue #123: 高優先度の専門実装へ譲った場合だけ0件を許可する。
            require = 0,
            expect = 1)
    private int aco$restoreRepresentableCoProcessors(CraftingCPUCluster cluster) {
        return CraftingExecutionBudget.limitCoProcessors(this, cluster, cluster.getCoProcessors());
    }
}
