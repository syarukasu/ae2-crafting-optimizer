package com.syaru.ae2craftingoptimizer.access;

import java.util.function.IntSupplier;

/** MekanismのCachedRecipeへ接続する、MixinではないOptional契約。 */
public interface MekanismCachedRecipeAccess {
    IntSupplier aco$getBaselineMaxOperations();
}
