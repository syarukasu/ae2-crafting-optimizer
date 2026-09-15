package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import com.syaru.ae2craftingoptimizer.optimization.CraftingCalculationMemo;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = KeyCounter.class, remap = false)
public class KeyCounterCalculationIndexMixin {
    @Shadow @Final private Reference2ObjectMap<Object, ?> lists;

    @Inject(method = "getSubIndex", at = @At("HEAD"), cancellable = true, require = 1)
    private void aco$reuseExistingCalculationIndex(AEKey key, CallbackInfoReturnable<Object> cir) {
        if (CraftingCalculationMemo.isActive()) {
            // Issue #179: computeIfAbsent would return this same index regardless of current durability.
            Object existing = lists.get(key.getPrimaryKey());
            if (existing != null) {
                cir.setReturnValue(existing);
            }
        }
    }
}
