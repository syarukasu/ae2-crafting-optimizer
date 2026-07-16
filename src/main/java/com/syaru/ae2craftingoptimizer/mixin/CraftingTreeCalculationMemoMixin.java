package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import com.syaru.ae2craftingoptimizer.optimization.CraftingCalculationMemo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = CraftingTreeNode.class, remap = false)
public abstract class CraftingTreeCalculationMemoMixin {
    @Shadow
    @Final
    private CraftingCalculation job;

    @Shadow
    @Final
    private IPatternDetails.IInput parentInput;

    @Redirect(
            method = {"<init>", "findCraftedStack"},
            at = @At(value = "INVOKE", target = "Lappeng/api/networking/crafting/ICraftingService;canEmitFor(Lappeng/api/stacks/AEKey;)Z"),
            require = 0)
    private boolean aco$memoizeCanEmit(ICraftingService service, AEKey key) {
        return CraftingCalculationMemo.canEmit(job, service, key);
    }

    @Redirect(
            method = "findCraftedStack",
            at = @At(value = "INVOKE", target = "Lappeng/api/networking/crafting/ICraftingService;getCraftingFor(Lappeng/api/stacks/AEKey;)Ljava/util/Collection;"),
            require = 0)
    private java.util.Collection<IPatternDetails> aco$memoizePatternLookup(ICraftingService service, AEKey key) {
        return CraftingCalculationMemo.patterns(job, service, key);
    }

    @Redirect(
            method = "findCraftedStack",
            at = @At(value = "INVOKE", target = "Lappeng/api/networking/crafting/ICraftingService;getFuzzyCraftable(Lappeng/api/stacks/AEKey;Lappeng/api/storage/AEKeyFilter;)Lappeng/api/stacks/AEKey;"),
            require = 0)
    private AEKey aco$memoizeFuzzyCraftable(
            ICraftingService service, AEKey key, appeng.api.storage.AEKeyFilter filter) {
        return CraftingCalculationMemo.fuzzyCraftable(
                job, service, parentInput, key, () -> service.getFuzzyCraftable(key, filter));
    }

    @Redirect(
            method = "addContainerItems",
            at = @At(value = "INVOKE", target = "Lappeng/api/crafting/IPatternDetails$IInput;getRemainingKey(Lappeng/api/stacks/AEKey;)Lappeng/api/stacks/AEKey;"),
            require = 0)
    private AEKey aco$memoizeRemainingKey(IPatternDetails.IInput input, AEKey template) {
        return CraftingCalculationMemo.remainingKey(job, input, template);
    }
}
