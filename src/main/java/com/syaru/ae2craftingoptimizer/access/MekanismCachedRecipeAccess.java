package com.syaru.ae2craftingoptimizer.access;

import java.util.function.IntSupplier;

/** Runtime contract for the optional Mekanism CachedRecipe accessor. */
public interface MekanismCachedRecipeAccess {
    IntSupplier aco$getBaselineMaxOperations();
}
