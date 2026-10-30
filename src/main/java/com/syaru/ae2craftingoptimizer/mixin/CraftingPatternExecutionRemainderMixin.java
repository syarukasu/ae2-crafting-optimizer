package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AECraftingPattern;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.optimization.CraftingExecutionRemainders;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Avoid rebuilding AE2's whole crafting frame for a proven slot-local remainder. */
@Mixin(value = AECraftingPattern.class, remap = false)
public class CraftingPatternExecutionRemainderMixin {
    @Shadow @Final private CraftingRecipe recipe;
    @Shadow @Final private GenericStack[] sparseInputs;
    @Shadow @Final private CraftingContainer testFrame;

    @Inject(method = "getRecipeRemainder", at = @At("HEAD"), cancellable = true, require = 1)
    private void aco$nativeRemainder(int slot, AEItemKey selected, CallbackInfoReturnable<ItemStack> cir) {
        if (!ACOConfig.enableNativeExecutionQueries() || sparseInputs == null
                || selected == null || slot < 0 || slot >= sparseInputs.length || sparseInputs[slot] == null) {
            return;
        }
        if (CraftingExecutionRemainders.nativeItem(selected.getItem())
                && CraftingExecutionRemainders.nativeFrame(recipe, testFrame, slot)) {
            cir.setReturnValue(CraftingExecutionRemainders.remainder(selected));
        }
    }
}

