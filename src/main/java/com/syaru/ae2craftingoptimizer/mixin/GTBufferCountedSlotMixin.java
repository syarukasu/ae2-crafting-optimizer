package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import com.syaru.ae2craftingoptimizer.optimization.GTCountedInputPush;
import com.syaru.ae2craftingoptimizer.access.CountedPatternBufferSlot;
import com.syaru.ae2craftingoptimizer.access.SharedPatternBufferAmounts;
import it.unimi.dsi.fastutil.objects.Object2LongOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.math.BigInteger;
import java.util.Map;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferPartMachine$InternalSlot", remap = false)
public abstract class GTBufferCountedSlotMixin implements CountedPatternBufferSlot, SharedPatternBufferAmounts {
    @Redirect(method = "pushPattern", at = @At(value = "INVOKE",
            target = "Lappeng/api/crafting/IPatternDetails;pushInputsToExternalInventory([Lappeng/api/stacks/KeyCounter;Lappeng/api/crafting/IPatternDetails$PatternInputSink;)V"),
            require = 1)
    private void aco$pushCountedInputs(IPatternDetails pattern, KeyCounter[] inputs,
            IPatternDetails.PatternInputSink sink) {
        GTCountedInputPush.toSlot(pattern, inputs, sink);
    }

    @Unique private boolean aco$itemTotalValid;
    @Unique private boolean aco$fluidTotalValid;
    @Unique private double aco$itemTotal;
    @Unique private double aco$fluidTotal;

    @Inject(method = "onContentsChanged", at = @At("HEAD"), require = 1)
    private void aco$invalidateSharedTotals(CallbackInfo ci) {
        aco$itemTotalValid = false;
        aco$fluidTotalValid = false;
    }

    @Inject(method = "deserializeNBT(Lnet/minecraft/nbt/CompoundTag;)V",
            at = {@At("HEAD"), @At("RETURN")}, require = 1)
    private void aco$invalidateLoadedTotals(net.minecraft.nbt.CompoundTag tag, CallbackInfo ci) {
        aco$itemTotalValid = false;
        aco$fluidTotalValid = false;
    }

    @Override public double aco$totalItemAmount() {
        if (!aco$itemTotalValid) {
            var total = BigInteger.ZERO;
            for (var entry : itemInventory.object2LongEntrySet())
                if (!entry.getKey().isEmpty() && entry.getLongValue() > 0)
                    total = total.add(BigInteger.valueOf(entry.getLongValue()));
            aco$itemTotal = total.doubleValue();
            aco$itemTotalValid = true;
        }
        return aco$itemTotal;
    }

    @Override public double aco$totalFluidAmount() {
        if (!aco$fluidTotalValid) {
            var total = BigInteger.ZERO;
            for (var entry : fluidInventory.object2LongEntrySet())
                if (!entry.getKey().isEmpty() && entry.getLongValue() > 0)
                    total = total.add(BigInteger.valueOf(entry.getLongValue()));
            aco$fluidTotal = total.doubleValue();
            aco$fluidTotalValid = true;
        }
        return aco$fluidTotal;
    }
    @Shadow @Final private Object2LongOpenCustomHashMap<ItemStack> itemInventory;
    @Shadow @Final private Object2LongOpenHashMap<FluidStack> fluidInventory;

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
