package com.syaru.ae2craftingoptimizer.mixin;

import com.syaru.ae2craftingoptimizer.mekanism.MekanismItemRecipeIntent;
import mekanism.api.recipes.MekanismRecipe;
import mekanism.api.recipes.inputs.IInputHandler;
import mekanism.common.tile.prefab.TileEntityElectricMachine;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "mekanism.common.tile.prefab.TileEntityElectricMachine", remap = false)
public abstract class MekanismItemLookupIntentMixin {
    @Redirect(method = "getRecipe(I)Lmekanism/api/recipes/ItemStackToItemStackRecipe;", at = @At(value = "INVOKE", target =
            "Lmekanism/common/tile/prefab/TileEntityElectricMachine;findFirstRecipe(Lmekanism/api/recipes/inputs/IInputHandler;)Lmekanism/api/recipes/MekanismRecipe;"), require = 1)
    private MekanismRecipe aco$lookupIntendedRecipe(TileEntityElectricMachine machine, IInputHandler<ItemStack> input) {
        return MekanismItemRecipeIntent.find(machine, input);
    }
}
