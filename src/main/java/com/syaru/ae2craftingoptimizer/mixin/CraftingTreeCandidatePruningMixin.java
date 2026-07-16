package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingTreeNode;
import com.syaru.ae2craftingoptimizer.optimization.PatternCandidatePruner;
import com.syaru.ae2craftingoptimizer.optimization.CraftingCalculationMemo;
import appeng.crafting.CraftingCalculation;
import java.util.Collection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = CraftingTreeNode.class, remap = false)
public abstract class CraftingTreeCandidatePruningMixin {
    @Shadow
    @Final
    private AEKey what;

    @Shadow
    @Final
    private CraftingCalculation job;

    @Redirect(
            method = "buildChildPatterns",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/networking/crafting/ICraftingService;getCraftingFor(Lappeng/api/stacks/AEKey;)Ljava/util/Collection;"),
            require = 0)
    private Collection<IPatternDetails> aco$pruneInvalidCandidates(ICraftingService craftingService, AEKey output) {
        return PatternCandidatePruner.prune(
                CraftingCalculationMemo.patterns(job, craftingService, output), what);
    }
}
