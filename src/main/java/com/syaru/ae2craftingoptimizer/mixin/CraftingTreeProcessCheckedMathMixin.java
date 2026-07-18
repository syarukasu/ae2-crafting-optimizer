package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.inv.CraftingSimulationState;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.engine.CheckedLongMath;
import java.util.Map;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pattern入力Multiplierと出力数量のunchecked LMULを検査する。 */
@Mixin(value = CraftingTreeProcess.class, remap = false)
public abstract class CraftingTreeProcessCheckedMathMixin {
    @Shadow
    @Final
    private IPatternDetails details;

    @Shadow
    @Final
    private Map<CraftingTreeNode, Long> nodes;

    @Inject(method = "request", at = @At("HEAD"))
    private void aco$validateProcessRequest(
            CraftingSimulationState inventory,
            long times,
            CallbackInfo ci) {
        if (!ACOConfig.enableCheckedAe2CraftingArithmetic()) {
            return;
        }
        CheckedLongMath.requireNonNegative(times, "ae2/process/times");
        if (times == 0L) {
            throw new ArithmeticException("AE2 attempted a zero-sized pattern execution");
        }
        long totalOutput = 0L;
        for (long multiplier : nodes.values()) {
            CheckedLongMath.multiply(multiplier, times, "ae2/process/input/" + details.getDefinition().getId());
        }
        for (var output : details.getOutputs()) {
            CheckedLongMath.multiply(output.amount(), times, "ae2/process/output/" + details.getDefinition().getId());
            totalOutput = CheckedLongMath.add(
                    totalOutput,
                    output.amount(),
                    "ae2/process/output-sum/" + details.getDefinition().getId());
        }
    }
}
