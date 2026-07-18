package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.NetworkCraftingSimulationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** CraftingCalculationが実際に固定した在庫Snapshotを高速Plannerへ渡す。 */
@Mixin(value = NetworkCraftingSimulationState.class, remap = false)
public interface NetworkCraftingSimulationStateAccessor {
    @Accessor("list")
    KeyCounter aco$getNetworkSnapshot();
}
