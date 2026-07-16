package com.syaru.ae2craftingoptimizer.mixin;

import com.syaru.ae2craftingoptimizer.optimization.CircuitCutterRecipeCache;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.glodblock.github.extendedae.util.recipe.RecipeSearchContext", remap = false)
public abstract class ExtendedAeCircuitCutterRecipeCacheMixin {
    @Shadow
    public abstract boolean testRecipe(Recipe<?> recipe);

    @Inject(method = "searchRecipe", at = @At("HEAD"), cancellable = true, require = 0)
    private void aco$reuseCircuitCutterRecipe(CallbackInfoReturnable<Recipe<?>> cir) {
        Object context = this;
        if (!CircuitCutterRecipeCache.supports(context)) {
            return;
        }

        CircuitCutterRecipeCache.Lookup lookup = CircuitCutterRecipeCache.find(context, this::testRecipe);
        if (lookup.hit()) {
            cir.setReturnValue(lookup.recipe());
        }
    }

    @Inject(method = "searchRecipe", at = @At("RETURN"), require = 0)
    private void aco$rememberCircuitCutterRecipe(CallbackInfoReturnable<Recipe<?>> cir) {
        Object context = this;
        if (CircuitCutterRecipeCache.supports(context)) {
            CircuitCutterRecipeCache.remember(context, cir.getReturnValue());
        }
    }
}
