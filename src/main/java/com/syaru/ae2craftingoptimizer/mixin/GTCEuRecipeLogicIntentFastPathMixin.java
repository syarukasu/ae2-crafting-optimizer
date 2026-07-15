package com.syaru.ae2craftingoptimizer.mixin;

import com.syaru.ae2craftingoptimizer.gtceu.GTCEuRecipeIntentFastPath;
import java.util.Iterator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.gregtechceu.gtceu.api.machine.trait.RecipeLogic", remap = false)
public abstract class GTCEuRecipeLogicIntentFastPathMixin {
    @Inject(method = "searchRecipe", at = @At("RETURN"), cancellable = true, require = 0)
    private void ae2CraftingOptimizer$prependIntentMatchedRecipes(CallbackInfoReturnable<Iterator<?>> cir) {
        Iterator<?> original = cir.getReturnValue();
        Iterator<?> wrapped = GTCEuRecipeIntentFastPath.wrapSearchIterator(this, original);
        if (wrapped != original) {
            cir.setReturnValue(wrapped);
        }
    }
}
