package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.InputTemplate;
import appeng.crafting.inv.ICraftingInventory;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.optimization.CraftingExecutionTemplates;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Execution-only allocation reduction; standalone planner enumeration is unchanged. */
@Mixin(value = CraftingCpuHelper.class, remap = false)
public class CraftingCpuHelperExecutionMixin {
    @Redirect(method = "extractPatternInputs", at = @At(value = "INVOKE",
            target = "Lappeng/crafting/execution/CraftingCpuHelper;getValidItemTemplates(Lappeng/crafting/inv/ICraftingInventory;Lappeng/api/crafting/IPatternDetails$IInput;Lnet/minecraft/world/level/Level;)Ljava/lang/Iterable;"),
            require = 1)
    private static Iterable<InputTemplate> aco$executionTemplates(ICraftingInventory inventory,
            IPatternDetails.IInput input, Level level) {
        return ACOConfig.enableNativeExecutionQueries()
                ? CraftingExecutionTemplates.get(inventory, input, level)
                : CraftingCpuHelper.getValidItemTemplates(inventory, input, level);
    }
}

