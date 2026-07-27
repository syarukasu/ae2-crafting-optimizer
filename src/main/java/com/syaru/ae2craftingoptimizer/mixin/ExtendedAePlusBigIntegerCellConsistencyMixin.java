package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.integration.ExactBigIntegerCellConsistency;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import java.math.BigInteger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ACO直接変更後の正確な総量を、ExtendedAE Plusのcache refreshへ再適用する。
 */
@Pseudo
@Mixin(
        targets = "com.extendedae_plus.api.storage.InfinityBigIntegerCellInventory",
        remap = false)
public abstract class ExtendedAePlusBigIntegerCellConsistencyMixin {
    @Shadow
    private int totalAEKeyType;

    @Shadow
    private BigInteger totalAEKey2Amounts;

    @Shadow
    protected abstract Object2ObjectMap<AEKey, BigInteger>
            getCellStoredMap();

    @Inject(
            method = "refreshCachedStateFromStorage",
            at = @At("RETURN"),
            remap = false,
            require = 1)
    private void aco$restoreExactDirectMutationCache(
            CallbackInfo callback) {
        Object2ObjectMap<AEKey, BigInteger> amounts =
                getCellStoredMap();
        ExactBigIntegerCellConsistency.expectedTotal(amounts)
                .ifPresent(total -> {
                    totalAEKey2Amounts = total;
                    totalAEKeyType = amounts.size();
                });
    }
}
