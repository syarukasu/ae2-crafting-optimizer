package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.CraftingSimulationState;
import com.syaru.ae2craftingoptimizer.access.CheckedCraftingArithmeticHookAccess;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Craft回数のMap加算とdouble bytes蓄積をAE2へ渡す前に検査する。 */
@Mixin(value = CraftingSimulationState.class, remap = false)
public abstract class CraftingSimulationStateCheckedMathMixin
        implements CheckedCraftingArithmeticHookAccess {
    @Shadow
    private double bytes;

    @Shadow
    private Map<IPatternDetails, Long> crafts;

    @Shadow
    private KeyCounter modifiableCache;

    @Shadow
    private KeyCounter emittedItems;

    @Inject(method = "insert", at = @At("HEAD"), require = 1)
    private void aco$validateInventoryInsert(
            AEKey key,
            long amount,
            Actionable mode,
            CallbackInfo ci) {
        if (!ACOConfig.enableCheckedAe2CraftingArithmetic()) {
            return;
        }
        if (amount < 0L) {
            throw new ArithmeticException("negative AE2 crafting inventory insert");
        }
        if (mode == Actionable.MODULATE) {
            Math.addExact(modifiableCache.get(key), amount);
        }
    }

    @Inject(method = "emitItems", at = @At("HEAD"), require = 1)
    private void aco$validateEmittedItems(AEKey key, long amount, CallbackInfo ci) {
        if (!ACOConfig.enableCheckedAe2CraftingArithmetic()) {
            return;
        }
        if (amount < 0L) {
            throw new ArithmeticException("negative AE2 emitted item count");
        }
        Math.addExact(emittedItems.get(key), amount);
    }

    @Inject(method = "addCrafting", at = @At("HEAD"), require = 1)
    private void aco$validateCraftCount(IPatternDetails details, long count, CallbackInfo ci) {
        if (!ACOConfig.enableCheckedAe2CraftingArithmetic()) {
            return;
        }
        if (count < 0L) {
            throw new ArithmeticException("negative AE2 crafting count");
        }
        Math.addExact(crafts.getOrDefault(details, 0L), count);
    }

    @Inject(method = "addBytes", at = @At("HEAD"), require = 1)
    private void aco$validateBytes(double amount, CallbackInfo ci) {
        if (!ACOConfig.enableCheckedAe2CraftingArithmetic()) {
            return;
        }
        double next = bytes + amount;
        /*
         * Issue #167: AE2は有限doubleを最終的なlong castでLong.MAX_VALUEへ飽和する。
         * ACOだけが有限値を拒否するとCPU容量判定の意味を変えるため、非有限値だけを止める。
         */
        if (!Double.isFinite(amount) || amount < 0.0D || !Double.isFinite(next)) {
            throw new ArithmeticException("AE2 crafting bytes are not finite");
        }
    }
}
