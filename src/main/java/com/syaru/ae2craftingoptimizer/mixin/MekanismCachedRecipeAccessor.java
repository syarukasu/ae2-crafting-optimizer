package com.syaru.ae2craftingoptimizer.mixin;

import com.syaru.ae2craftingoptimizer.access.MekanismCachedRecipeAccess;
import java.util.function.IntSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "mekanism.api.recipes.cache.CachedRecipe", remap = false)
public interface MekanismCachedRecipeAccessor extends MekanismCachedRecipeAccess {
    @Override
    @Accessor("baselineMaxOperations")
    IntSupplier aco$getBaselineMaxOperations();
}
