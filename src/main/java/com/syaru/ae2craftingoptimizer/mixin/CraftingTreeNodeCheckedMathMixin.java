package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.inv.CraftingSimulationState;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.engine.CheckedLongMath;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** CraftingTreeNode内のunchecked LMULが負数へ巻き戻る前に停止する。 */
@Mixin(value = CraftingTreeNode.class, remap = false)
public abstract class CraftingTreeNodeCheckedMathMixin {
    @Shadow
    @Final
    private AEKey what;

    @Shadow
    @Final
    private long amount;

    @Inject(method = "request", at = @At("HEAD"))
    private void aco$validateNodeRequest(
            CraftingSimulationState inventory,
            long requestedAmount,
            KeyCounter containerItems,
            CallbackInfo ci) {
        if (!ACOConfig.enableCheckedAe2CraftingArithmetic()) {
            return;
        }
        CheckedLongMath.requireNonNegative(requestedAmount, "ae2/node/requestedAmount");
        CheckedLongMath.multiply(amount, requestedAmount, "ae2/node/" + what.getId());
    }
}
