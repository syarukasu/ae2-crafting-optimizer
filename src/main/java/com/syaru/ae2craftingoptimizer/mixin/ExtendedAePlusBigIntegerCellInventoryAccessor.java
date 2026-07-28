package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import java.math.BigInteger;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** ExtendedAE PlusのBigIntegerセルから、キー別の正確な在庫Mapを取得するOptional Accessor。 */
@Pseudo
@Mixin(
        targets = "com.extendedae_plus.api.storage.InfinityBigIntegerCellInventory",
        remap = false)
public interface ExtendedAePlusBigIntegerCellInventoryAccessor {
    @Invoker("getCellStoredMap")
    Object2ObjectMap<AEKey, BigInteger> aco$getExactStoredAmounts();

    @Accessor("totalAEKeyType")
    int aco$getExactStoredTypeCount();

    @Accessor("totalAEKeyType")
    void aco$setExactStoredTypeCount(int value);

    @Accessor("totalAEKey2Amounts")
    BigInteger aco$getExactStoredTotal();

    @Accessor("totalAEKey2Amounts")
    void aco$setExactStoredTotal(BigInteger value);

    @Invoker("saveChanges")
    void aco$saveExactChanges();

    @Invoker("hasUUID")
    boolean aco$hasExactStorageUuid();

    @Invoker("getUUID")
    UUID aco$getExactStorageUuid();

    @Invoker("assignNewUUID")
    UUID aco$assignExactStorageUuid();
}
