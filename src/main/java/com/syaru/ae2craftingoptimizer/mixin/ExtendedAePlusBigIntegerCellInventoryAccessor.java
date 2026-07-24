package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import java.math.BigInteger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

/** ExtendedAE PlusのBigIntegerセルから、キー別の正確な在庫Mapを取得するOptional Accessor。 */
@Pseudo
@Mixin(
        targets = "com.extendedae_plus.api.storage.InfinityBigIntegerCellInventory",
        remap = false)
public interface ExtendedAePlusBigIntegerCellInventoryAccessor {
    @Invoker("getCellStoredMap")
    Object2ObjectMap<AEKey, BigInteger> aco$getExactStoredAmounts();
}
