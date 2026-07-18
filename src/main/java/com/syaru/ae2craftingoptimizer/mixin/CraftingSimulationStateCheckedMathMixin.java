package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.CraftingSimulationState;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Craft回数のMap加算とdouble bytes蓄積をAE2へ渡す前に検査する。 */
@Mixin(value = CraftingSimulationState.class, remap = false)
public abstract class CraftingSimulationStateCheckedMathMixin {
    @Shadow
    private double bytes;

    @Shadow
    private Map<IPatternDetails, Long> crafts;

    @Shadow
    private KeyCounter modifiableCache;

    @Shadow
    private KeyCounter emittedItems;

    @Inject(method = "insert", at = @At("HEAD"))
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

    @Inject(method = "emitItems", at = @At("HEAD"))
    private void aco$validateEmittedItems(AEKey key, long amount, CallbackInfo ci) {
        if (!ACOConfig.enableCheckedAe2CraftingArithmetic()) {
            return;
        }
        if (amount < 0L) {
            throw new ArithmeticException("negative AE2 emitted item count");
        }
        Math.addExact(emittedItems.get(key), amount);
    }

    @Inject(method = "addCrafting", at = @At("HEAD"))
    private void aco$validateCraftCount(IPatternDetails details, long count, CallbackInfo ci) {
        if (ACOConfig.enableCheckedAe2CraftingArithmetic()) {
            if (count < 0L) {
                throw new ArithmeticException("negative AE2 crafting count");
            }
            Math.addExact(crafts.getOrDefault(details, 0L), count);
        }
    }

    @Inject(method = "addBytes", at = @At("HEAD"))
    private void aco$validateBytes(double amount, CallbackInfo ci) {
        if (!ACOConfig.enableCheckedAe2CraftingArithmetic()) {
            return;
        }
        double next = bytes + amount;
        if (!Double.isFinite(amount) || amount < 0.0D || !Double.isFinite(next) || next >= Long.MAX_VALUE) {
            throw new ArithmeticException("AE2 crafting bytes exceed the supported long range");
        }
    }
}
