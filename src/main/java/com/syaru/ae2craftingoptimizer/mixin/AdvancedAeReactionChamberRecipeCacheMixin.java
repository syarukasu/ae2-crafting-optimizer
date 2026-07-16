package com.syaru.ae2craftingoptimizer.mixin;

import com.syaru.ae2craftingoptimizer.optimization.ReactionChamberRecipeCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.entities.ReactionChamberEntity", remap = false)
public abstract class AdvancedAeReactionChamberRecipeCacheMixin {
    @Inject(method = "findRecipe", at = @At("RETURN"), require = 0)
    private void aco$rememberResolvedRecipe(CallbackInfoReturnable<Object> cir) {
        ReactionChamberRecipeCache.remember(this, cir.getReturnValue());
    }
}
