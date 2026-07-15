package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEFluidKey;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.InputTemplate;
import appeng.crafting.inv.ICraftingInventory;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.util.List;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingCpuHelper.class, remap = false)
public abstract class CraftingCpuHelperFluidFastPathMixin {
    @Inject(method = "getValidItemTemplates", at = @At("HEAD"), cancellable = true)
    private static void aco$useExactSingleFluidInput(
            ICraftingInventory inventory,
            IPatternDetails.IInput input,
            Level level,
            CallbackInfoReturnable<Iterable<InputTemplate>> cir) {
        if (!ACOConfig.deepFluidPatternRework()) {
            return;
        }

        var possibleInputs = input.getPossibleInputs();
        if (possibleInputs.length != 1
                || !(possibleInputs[0].what() instanceof AEFluidKey fluidKey)
                || fluidKey.getFuzzySearchMaxValue() != 0
                || !input.isValid(fluidKey, level)) {
            return;
        }

        if (inventory.extract(fluidKey, 1, Actionable.SIMULATE) > 0) {
            cir.setReturnValue(List.of(new InputTemplate(fluidKey, possibleInputs[0].amount())));
        } else {
            cir.setReturnValue(List.of());
        }
    }
}
