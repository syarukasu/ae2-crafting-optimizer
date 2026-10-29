package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.access.CountedPatternBufferSlot;
import it.unimi.dsi.fastutil.objects.Object2LongOpenCustomHashMap;
import java.math.BigInteger;
import java.util.Map;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferPartMachine$InternalSlot", remap = false)
public abstract class GTBufferCountedSlotMixin implements CountedPatternBufferSlot {
    @Shadow @Final private Object2LongOpenCustomHashMap<ItemStack> itemInventory;
    @Shadow @Final private Object2LongOpenCustomHashMap<FluidStack> fluidInventory;

    @Override public long aco$countedHeadroom(Map<AEKey, BigInteger> inputs, long offered) {
        for (var entry : inputs.entrySet()) {
            long stored;
            if (entry.getKey() instanceof AEItemKey item) stored = itemInventory.getLong(item.toStack());
            else if (entry.getKey() instanceof AEFluidKey fluid) stored = fluidInventory.getLong(fluid.toStack(1));
            else return 0;
            if (stored < 0 || entry.getValue().signum() <= 0) return 0;
            var available = BigInteger.valueOf(Long.MAX_VALUE).subtract(BigInteger.valueOf(stored));
            offered = BigInteger.valueOf(offered).min(available.divide(entry.getValue())).longValueExact();
        }
        return offered;
    }
}
