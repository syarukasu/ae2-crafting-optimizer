package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.energy.IEnergyService;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.optimization.CountedCraftingDispatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** After exact/V2 ownership checks. Unknown acceptance never falls back on a later tick. */
@Mixin(value = CraftingCpuLogic.class, remap = false, priority = 900)
public abstract class CraftingCpuLogicCountedTransferMixin {
    @Unique private boolean aco$countedTransferFaulted;

    @Inject(method = "executeCrafting", at = @At("HEAD"), cancellable = true, require = 1)
    private void aco$deliverCounted(int grant, CraftingService service, IEnergyService energy,
            Level level, CallbackInfoReturnable<Integer> cir) {
        if (aco$countedTransferFaulted) { cir.setReturnValue(0); return; }
        try {
            int accepted = CountedCraftingDispatch.execute(this, grant, service, energy, level);
            if (accepted != CountedCraftingDispatch.NOT_HANDLED) cir.setReturnValue(accepted);
        } catch (RuntimeException | LinkageError failure) {
            aco$countedTransferFaulted = true;
            if (this instanceof com.syaru.ae2craftingoptimizer.access.CraftingLogicTransactionAccess access)
                access.aco$getCraftingOwner().aco$markCraftingOwnerDirty();
            throw failure;
        }
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"), require = 1)
    private void aco$saveCountedFault(CompoundTag tag, CallbackInfo ci) {
        if (aco$countedTransferFaulted) tag.putBoolean("acoCountedTransferFaulted", true);
        else tag.remove("acoCountedTransferFaulted");
    }

    @Inject(method = "readFromNBT", at = @At("RETURN"), require = 1)
    private void aco$loadCountedFault(CompoundTag tag, CallbackInfo ci) {
        aco$countedTransferFaulted = tag.getBoolean("acoCountedTransferFaulted");
    }

    @Inject(method = "finishJob", at = @At("HEAD"), require = 1)
    private void aco$clearCountedFault(boolean successful, CallbackInfo ci) {
        aco$countedTransferFaulted = false;
    }
}
