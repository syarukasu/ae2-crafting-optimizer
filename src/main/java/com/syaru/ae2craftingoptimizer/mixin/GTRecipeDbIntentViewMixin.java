package com.syaru.ae2craftingoptimizer.mixin;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.syaru.ae2craftingoptimizer.gtceu.GTRecipeDbView;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.gregtechceu.gtceu.api.recipe.lookup.RecipeDB", remap = false)
public abstract class GTRecipeDbIntentViewMixin implements GTRecipeDbView {
    @Unique private final Set<GTRecipe> aco$recipes = Collections.newSetFromMap(new IdentityHashMap<>());
    @Unique private long aco$revision;

    @Inject(method = "add", at = @At("RETURN"), require = 1)
    private void aco$accepted(GTRecipe recipe, List<?> ingredients, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && aco$recipes.add(recipe)) aco$revision++;
    }

    @Inject(method = "clear", at = @At("RETURN"), require = 1)
    private void aco$cleared(CallbackInfo ci) {
        aco$recipes.clear();
        aco$revision++;
    }

    @Override public long aco$recipeRevision() { return aco$revision; }
    @Override public List<?> aco$acceptedRecipes() { return List.copyOf(aco$recipes); }
}
