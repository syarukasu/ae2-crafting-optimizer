package com.syaru.ae2craftingoptimizer.mixin;

import com.syaru.ae2craftingoptimizer.gtceu.GTCEuRecipeIntentFastPath;
import java.util.Iterator;
import java.util.function.Predicate;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.recipe.kind.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.gregtechceu.gtceu.api.machine.trait.RecipeLogic", remap = false)
public abstract class GTCEuRecipeLogicIntentFastPathMixin {
    @Redirect(method = "searchRecipe", at = @At(value = "INVOKE", target =
            "Lcom/gregtechceu/gtceu/api/recipe/GTRecipeType;searchRecipe(Lcom/gregtechceu/gtceu/api/capability/recipe/IRecipeCapabilityHolder;Ljava/util/function/Predicate;)Ljava/util/Iterator;"), require = 1)
    @SuppressWarnings("unchecked")
    private Iterator<GTRecipe> ae2CraftingOptimizer$deferSearch(GTRecipeType type,
            IRecipeCapabilityHolder holder, Predicate<GTRecipe> predicate) {
        return (Iterator<GTRecipe>) GTCEuRecipeIntentFastPath.search(this,
                () -> type.searchRecipe(holder, predicate));
    }
}
