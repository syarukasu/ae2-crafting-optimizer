package com.syaru.ae2craftingoptimizer.mixin;

import com.syaru.ae2craftingoptimizer.mekanism.MekanismRecipeIntentFastPath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(
        targets = {
                "mekanism.common.tile.prefab.TileEntityElectricMachine",
                "mekanism.common.tile.prefab.TileEntityAdvancedElectricMachine",
                "mekanism.common.tile.factory.TileEntityItemStackToItemStackFactory",
                "mekanism.common.tile.factory.TileEntityItemStackGasToItemStackFactory",
                "mekanism.common.tile.factory.TileEntityMetallurgicInfuserFactory",
                "mekanism.common.tile.factory.TileEntityCombiningFactory",
                "mekanism.common.tile.factory.TileEntitySawingFactory",
                "mekanism.common.tile.machine.TileEntityAntiprotonicNucleosynthesizer",
                "mekanism.common.tile.machine.TileEntityChemicalCrystallizer",
                "mekanism.common.tile.machine.TileEntityChemicalDissolutionChamber",
                "mekanism.common.tile.machine.TileEntityChemicalInfuser",
                "mekanism.common.tile.machine.TileEntityChemicalOxidizer",
                "mekanism.common.tile.machine.TileEntityChemicalWasher",
                "mekanism.common.tile.machine.TileEntityCombiner",
                "mekanism.common.tile.machine.TileEntityElectrolyticSeparator",
                "mekanism.common.tile.machine.TileEntityIsotopicCentrifuge",
                "mekanism.common.tile.machine.TileEntityMetallurgicInfuser",
                "mekanism.common.tile.machine.TileEntityNutritionalLiquifier",
                "mekanism.common.tile.machine.TileEntityPaintingMachine",
                "mekanism.common.tile.machine.TileEntityPigmentExtractor",
                "mekanism.common.tile.machine.TileEntityPigmentMixer",
                "mekanism.common.tile.machine.TileEntityPrecisionSawmill",
                "mekanism.common.tile.machine.TileEntityPressurizedReactionChamber",
                "mekanism.common.tile.machine.TileEntityRotaryCondensentrator",
                "mekanism.common.tile.machine.TileEntitySolarNeutronActivator"
        },
        remap = false
)
public abstract class MekanismRecipeIntentFastPathMixin {
    @Inject(
            method = "getRecipe(I)Lmekanism/api/recipes/MekanismRecipe;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private void aco$getRecipeFromIntent(int cacheIndex, CallbackInfoReturnable<Object> cir) {
        Object recipe = MekanismRecipeIntentFastPath.findRecipe(this, cacheIndex);
        if (recipe != null) {
            cir.setReturnValue(recipe);
        }
    }
}
