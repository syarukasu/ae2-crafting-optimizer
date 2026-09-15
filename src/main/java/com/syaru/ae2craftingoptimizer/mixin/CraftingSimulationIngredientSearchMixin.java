package com.syaru.ae2craftingoptimizer.mixin;

import appeng.crafting.inv.CraftingSimulationState;
import com.syaru.ae2craftingoptimizer.optimization.CraftingCalculationMemo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CraftingSimulationState.class, remap = false)
public abstract class CraftingSimulationIngredientSearchMixin {
    @Inject(method = "cacheFuzzy", at = @At(value = "INVOKE",
            target = "Lappeng/crafting/inv/CraftingSimulationState;simulateExtractParent(Lappeng/api/stacks/AEKey;J)J"),
            require = 1)
    private void aco$pauseBetweenSnapshotCandidates(CallbackInfo ci) throws InterruptedException {
        // Issue #179: both the iterator and partial cache belong to this calculation, not ME storage.
        CraftingCalculationMemo.checkpointIngredientSearch();
    }
}
