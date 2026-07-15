package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.stacks.AEKey;
import appeng.api.storage.AEKeyFilter;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.optimization.CraftableSetCache;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingService.class, remap = false)
public abstract class CraftingServiceCraftableSetCacheMixin {
    @Inject(method = "getCraftables", at = @At("HEAD"), cancellable = true)
    private void aco$getCachedCraftables(AEKeyFilter filter, CallbackInfoReturnable<Set<AEKey>> cir) {
        Set<AEKey> cached = CraftableSetCache.get((CraftingService) (Object) this, filter);
        if (cached != null) {
            cir.setReturnValue(cached);
        }
    }

    @Inject(method = "getCraftables", at = @At("RETURN"))
    private void aco$rememberCraftables(AEKeyFilter filter, CallbackInfoReturnable<Set<AEKey>> cir) {
        CraftableSetCache.put((CraftingService) (Object) this, filter, cir.getReturnValue());
    }
}
