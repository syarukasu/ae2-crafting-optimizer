package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.optimization.PatternLookupCache;
import com.syaru.ae2craftingoptimizer.optimization.PatternAvailabilitySorter;
import java.util.Collection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingService.class, remap = false)
public abstract class CraftingServicePatternLookupCacheMixin {
    @Shadow
    @Final
    private IGrid grid;

    @Inject(method = "getCraftingFor", at = @At("HEAD"), cancellable = true)
    private void aco$getCachedCraftingFor(AEKey key, CallbackInfoReturnable<Collection<IPatternDetails>> cir) {
        Collection<IPatternDetails> cached = PatternLookupCache.get((CraftingService) (Object) this, key);
        if (cached != null) {
            cir.setReturnValue(aco$orderByAvailability(cached));
        }
    }

    @Inject(method = "getCraftingFor", at = @At("RETURN"), cancellable = true)
    private void aco$rememberCraftingFor(AEKey key, CallbackInfoReturnable<Collection<IPatternDetails>> cir) {
        Collection<IPatternDetails> ordered = aco$orderByAvailability(cir.getReturnValue());
        cir.setReturnValue(ordered);
        PatternLookupCache.put((CraftingService) (Object) this, key, ordered);
    }

    private Collection<IPatternDetails> aco$orderByAvailability(Collection<IPatternDetails> patterns) {
        if (!ACOConfig.deepPatternSelectionByAvailability()) {
            return patterns;
        }
        return PatternAvailabilitySorter.order(patterns, grid.getStorageService().getCachedInventory());
    }
}
