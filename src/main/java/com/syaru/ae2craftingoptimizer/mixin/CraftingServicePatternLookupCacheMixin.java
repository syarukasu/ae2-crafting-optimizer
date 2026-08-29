package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.optimization.PatternLookupCache;
import java.util.Collection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingService.class, remap = false)
public abstract class CraftingServicePatternLookupCacheMixin {
    @Inject(method = "getCraftingFor", at = @At("HEAD"), cancellable = true)
    private void aco$getCachedCraftingFor(AEKey key, CallbackInfoReturnable<Collection<IPatternDetails>> cir) {
        Collection<IPatternDetails> cached = PatternLookupCache.get((CraftingService) (Object) this, key);
        if (cached != null) {
            cir.setReturnValue(cached);
        }
    }

    @Inject(method = "getCraftingFor", at = @At("RETURN"), cancellable = true)
    private void aco$rememberCraftingFor(AEKey key, CallbackInfoReturnable<Collection<IPatternDetails>> cir) {
        PatternLookupCache.put((CraftingService) (Object) this, key, cir.getReturnValue());
    }
}
