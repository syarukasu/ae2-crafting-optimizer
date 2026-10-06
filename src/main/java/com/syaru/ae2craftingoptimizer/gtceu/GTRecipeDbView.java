package com.syaru.ae2craftingoptimizer.gtceu;

import java.util.List;

/** #179: only recipes actually accepted by the native GT lookup database. */
public interface GTRecipeDbView {
    long aco$recipeRevision();
    List<?> aco$acceptedRecipes();
}
