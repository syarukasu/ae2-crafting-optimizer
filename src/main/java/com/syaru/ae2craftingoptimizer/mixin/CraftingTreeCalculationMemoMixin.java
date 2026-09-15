package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import com.syaru.ae2craftingoptimizer.optimization.CraftingCalculationMemo;
import com.syaru.ae2craftingoptimizer.access.CraftingCalculationThreadAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = CraftingTreeNode.class, remap = false)
public abstract class CraftingTreeCalculationMemoMixin {
    @Shadow
    @Final
    private CraftingCalculation job;

    @Shadow
    @Final
    private IPatternDetails.IInput parentInput;

    @Shadow
    private java.util.ArrayList<CraftingTreeProcess> nodes;

    @Invoker("buildChildPatterns")
    protected abstract void aco$buildChildPatterns();

    @Invoker("addContainerItems")
    protected abstract void aco$addContainerItems(AEKey key, long amount, KeyCounter outputs);

    @Inject(method = "buildChildPatterns", at = @At("HEAD"), cancellable = true, require = 1)
    private void aco$lookupPatternsOnServer(CallbackInfo ci) {
        // Server側の再入時は元メソッドを実行する。workerは同じnodesを同時に変更しない。
        if (nodes == null && ((CraftingCalculationThreadAccess) job)
                .aco$runPatternLookup(this::aco$buildChildPatterns)) {
            ci.cancel();
        }
    }

    @Inject(method = "addContainerItems", at = @At("HEAD"), cancellable = true, require = 1)
    private void aco$resolveRemainingItemsOnServer(
            AEKey key, long amount, KeyCounter outputs, CallbackInfo ci) {
        // 返却物を要求しない経路にはServer taskを作らない。
        if (outputs != null && ((CraftingCalculationThreadAccess) job).aco$runPatternLookup(
                () -> aco$addContainerItems(key, amount, outputs))) {
            ci.cancel();
        }
    }

    @Redirect(
            method = {"<init>", "findCraftedStack"},
            at = @At(value = "INVOKE", target = "Lappeng/api/networking/crafting/ICraftingService;canEmitFor(Lappeng/api/stacks/AEKey;)Z"),
            require = 2)
    private boolean aco$memoizeCanEmit(ICraftingService service, AEKey key) {
        return CraftingCalculationMemo.canEmit(job, service, key);
    }

    @Redirect(
            method = "findCraftedStack",
            at = @At(value = "INVOKE", target = "Lappeng/api/networking/crafting/ICraftingService;getCraftingFor(Lappeng/api/stacks/AEKey;)Ljava/util/Collection;"),
            require = 1)
    private java.util.Collection<IPatternDetails> aco$memoizePatternLookup(ICraftingService service, AEKey key) {
        return CraftingCalculationMemo.patterns(job, service, key);
    }

    @Redirect(
            method = "buildChildPatterns",
            at = @At(value = "INVOKE", target = "Lappeng/api/networking/crafting/ICraftingService;getCraftingFor(Lappeng/api/stacks/AEKey;)Ljava/util/Collection;"),
            require = 1)
    private java.util.Collection<IPatternDetails> aco$reuseUnmodifiedPatternCandidates(
            ICraftingService service,
            AEKey key) {
        ((CraftingCalculationThreadAccess) job).aco$observeCraftingService(service);
        // Issue #167: 候補をpruneせず、AE2が返した同一順序のListだけを一計算内で再利用する。
        return CraftingCalculationMemo.patternCandidates(job, service, key);
    }

    @Redirect(
            method = "findCraftedStack",
            at = @At(value = "INVOKE", target = "Lappeng/api/crafting/IPatternDetails$IInput;getPossibleInputs()[Lappeng/api/stacks/GenericStack;"),
            require = 2)
    private GenericStack[] aco$reuseFindCraftedStackInputs(IPatternDetails.IInput input) {
        return CraftingCalculationMemo.possibleInputs(input);
    }

    @Redirect(
            method = "notRecursive",
            at = @At(value = "INVOKE", target = "Lappeng/api/crafting/IPatternDetails$IInput;getPossibleInputs()[Lappeng/api/stacks/GenericStack;"),
            require = 1)
    private GenericStack[] aco$reuseRecursionCheckInputs(IPatternDetails.IInput input) {
        return CraftingCalculationMemo.possibleInputs(input);
    }

    @Redirect(
            method = "findCraftedStack",
            at = @At(value = "INVOKE", target = "Lappeng/api/networking/crafting/ICraftingService;getFuzzyCraftable(Lappeng/api/stacks/AEKey;Lappeng/api/storage/AEKeyFilter;)Lappeng/api/stacks/AEKey;"),
            require = 1)
    private AEKey aco$memoizeFuzzyCraftable(
            ICraftingService service, AEKey key, appeng.api.storage.AEKeyFilter filter) {
        return CraftingCalculationMemo.fuzzyCraftable(
                job, service, parentInput, key, () -> service.getFuzzyCraftable(key, filter));
    }

    @Redirect(
            method = "addContainerItems",
            at = @At(value = "INVOKE", target = "Lappeng/api/crafting/IPatternDetails$IInput;getRemainingKey(Lappeng/api/stacks/AEKey;)Lappeng/api/stacks/AEKey;"),
            require = 1)
    private AEKey aco$memoizeRemainingKey(IPatternDetails.IInput input, AEKey template) {
        return CraftingCalculationMemo.remainingKey(job, input, template);
    }
}
