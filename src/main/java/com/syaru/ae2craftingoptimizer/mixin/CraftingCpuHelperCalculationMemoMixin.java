package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.execution.CraftingCpuHelper;
import com.syaru.ae2craftingoptimizer.optimization.CraftingCalculationMemo;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** AE2標準Planner内で証明済みの読取専用Input metadataだけを一計算内で再利用する。 */
@Mixin(value = CraftingCpuHelper.class, remap = false)
public abstract class CraftingCpuHelperCalculationMemoMixin {
    @Redirect(
            method = "getValidItemTemplates",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/crafting/IPatternDetails$IInput;getPossibleInputs()[Lappeng/api/stacks/GenericStack;"),
            require = 1)
    private static GenericStack[] aco$reusePossibleInputs(IPatternDetails.IInput input) {
        return CraftingCalculationMemo.possibleInputs(input);
    }

    @Redirect(
            method = "lambda$getValidItemTemplates$0",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/crafting/IPatternDetails$IInput;isValid(Lappeng/api/stacks/AEKey;Lnet/minecraft/world/level/Level;)Z"),
            require = 1)
    private static boolean aco$memoizePureInputValidation(
            IPatternDetails.IInput input,
            AEKey candidate,
            Level level) {
        return CraftingCalculationMemo.inputValid(
                input,
                candidate,
                level,
                () -> input.isValid(candidate, level));
    }
}
