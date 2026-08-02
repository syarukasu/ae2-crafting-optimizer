package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.access.ExtendedAePlusBigIntegerCellInventoryAccess;
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
public interface ExtendedAePlusBigIntegerCellInventoryAccessor
        extends ExtendedAePlusBigIntegerCellInventoryAccess {
    @Override
    @Invoker("getCellStoredMap")
    Object2ObjectMap<AEKey, BigInteger> aco$getExactStoredAmounts();

    @Override
    @Accessor("totalAEKeyType")
    int aco$getExactStoredTypeCount();

    @Override
    @Accessor("totalAEKeyType")
    void aco$setExactStoredTypeCount(int value);

    @Override
    @Accessor("totalAEKey2Amounts")
    BigInteger aco$getExactStoredTotal();

    @Override
    @Accessor("totalAEKey2Amounts")
    void aco$setExactStoredTotal(BigInteger value);

    @Override
    @Invoker("saveChanges")
    void aco$saveExactChanges();

    @Override
    @Invoker("hasUUID")
    boolean aco$hasExactStorageUuid();

    @Override
    @Invoker("getUUID")
    UUID aco$getExactStorageUuid();

    @Override
    @Invoker("assignNewUUID")
    UUID aco$assignExactStorageUuid();
}
