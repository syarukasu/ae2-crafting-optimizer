package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.crafting.execution.CraftingCpuHelper;
import com.syaru.ae2craftingoptimizer.optimization.CraftingCalculationMemo;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** AE2計算中に同じ入力候補へ繰り返されるrecipe妥当性判定だけを再利用する。 */
@Mixin(value = CraftingCpuHelper.class, remap = false)
public abstract class CraftingCpuHelperCalculationMemoMixin {
    @Redirect(
            method = "lambda$getValidItemTemplates$0",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/crafting/IPatternDetails$IInput;isValid(Lappeng/api/stacks/AEKey;Lnet/minecraft/world/level/Level;)Z"),
            require = 1)
    private static boolean aco$memoizeInputValidation(
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
