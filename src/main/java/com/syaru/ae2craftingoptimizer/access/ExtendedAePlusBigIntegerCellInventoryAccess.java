package com.syaru.ae2craftingoptimizer.access;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import java.math.BigInteger;
import java.util.UUID;

/** ExtendedAE PlusのBigIntegerセルへ安全に接続する、Mixinではない内部契約。 */
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
