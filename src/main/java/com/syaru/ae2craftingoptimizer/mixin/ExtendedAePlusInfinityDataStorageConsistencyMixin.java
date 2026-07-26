package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.integration.ExactBigIntegerCellConsistency;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import java.math.BigInteger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ExtendedAE Plus SavedDataがMapと同じ正確な総量を保存するよう整合させる。
 */
@Pseudo
@Mixin(
        targets = "com.extendedae_plus.util.storage.InfinityDataStorage",
        remap = false)
public abstract class ExtendedAePlusInfinityDataStorageConsistencyMixin {
    @Shadow
    @Final
    public Object2ObjectMap<AEKey, BigInteger> amounts;

    @Shadow
    public BigInteger itemCount;

    @Inject(
            method = "serializeNBT",
            at = @At("HEAD"),
            remap = false,
            require = 1)
    private void aco$synchronizeExactTotalBeforeSave(
            CallbackInfoReturnable<?> callback) {
        ExactBigIntegerCellConsistency.expectedTotal(amounts)
                .ifPresent(total -> itemCount = total);
    }
}
