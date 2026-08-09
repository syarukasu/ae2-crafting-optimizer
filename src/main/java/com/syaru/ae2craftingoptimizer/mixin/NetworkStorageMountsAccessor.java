package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import com.syaru.ae2craftingoptimizer.access.NetworkStorageMountsAccess;
import java.util.List;
import java.util.NavigableMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** NetworkStorage本来の優先順をExact BigInteger取引でも維持するための限定Accessor。 */
@Mixin(value = NetworkStorage.class, remap = false)
public interface NetworkStorageMountsAccessor extends NetworkStorageMountsAccess {
    @Accessor("priorityInventory")
    NavigableMap<Integer, List<MEStorage>> aco$getPriorityInventory();
}
