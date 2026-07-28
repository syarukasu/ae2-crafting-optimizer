package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

    @Shadow
    private void refreshCachedStateFromStorage() {
        throw new AssertionError();
    }

    /**
     * 同じUUIDを指す別Inventory wrapperがACO経由でMapを更新していても、
     * ExtendedAE Plus本来のlong搬入出を古いinstance cacheで計算させない。
     */
    @Inject(
            method = {
                "insert",
                "extract"
            },
            at = @At("HEAD"),
            remap = false,
            require = 2)
    private void aco$refreshBeforeNormalMutation(
            AEKey key,
            long amount,
            Actionable mode,
            IActionSource source,
            CallbackInfoReturnable<Long> callback) {
        refreshCachedStateFromStorage();
    }

    /**
     * ExtendedAE Plus本来の搬入出後も共有Mapの正確な総量をSidecarへ反映する。
     */
    @Inject(
            method = {
                "insert",
                "extract"
            },
            at = @At("RETURN"),
            remap = false,
            require = 2)
    private void aco$recordAfterNormalMutation(
            AEKey key,
            long amount,
            Actionable mode,
            IActionSource source,
            CallbackInfoReturnable<Long> callback) {
        /*
         * SIMULATEまたは受理量0では保存Mapが変わらないため、
         * 既存の正本総量を同じ値で書き直さない。
         */
        if (mode != Actionable.MODULATE
                || callback.getReturnValue()
                        <= 0L) {
            return;
        }
        Object2ObjectMap<AEKey, BigInteger> amounts =
                getCellStoredMap();
        ExactBigIntegerCellConsistency.record(
                amounts,
                totalAEKey2Amounts);
    }

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
