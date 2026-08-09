package com.syaru.ae2craftingoptimizer.access;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import java.math.BigInteger;
import java.util.UUID;

/**
 * Runtime contract exposed by the optional ExtendedAE Plus BigInteger-cell
 * mixins. It is a normal interface so storage code never directly loads a
 * Mixin-defined type.
 */
public interface ExtendedAePlusBigIntegerCellInventoryAccess {
    Object2ObjectMap<AEKey, BigInteger> aco$getExactStoredAmounts();

    int aco$getExactStoredTypeCount();

    void aco$setExactStoredTypeCount(int value);

    BigInteger aco$getExactStoredTotal();

    void aco$setExactStoredTotal(BigInteger value);

    void aco$saveExactChanges();

    boolean aco$hasExactStorageUuid();

    UUID aco$getExactStorageUuid();

    UUID aco$assignExactStorageUuid();
}
