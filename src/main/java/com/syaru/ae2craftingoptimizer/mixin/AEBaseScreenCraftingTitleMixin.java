package com.syaru.ae2craftingoptimizer.mixin;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.crafting.CraftConfirmScreen;
import com.syaru.ae2craftingoptimizer.client.BigAmountFormatter;
import com.syaru.ae2craftingoptimizer.client.BigCraftingPlanClientStore;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Repairs exact plan bytes even when an addon writes the title after AE2. */
@Mixin(value = AEBaseScreen.class, remap = false)
public abstract class AEBaseScreenCraftingTitleMixin {
    @ModifyVariable(method = "setTextContent", at = @At("HEAD"),
            argsOnly = true, ordinal = 0, require = 1)
    private Component aco$exactCraftingTitle(Component original, String id, Component text) {
        if (!((Object) this instanceof CraftConfirmScreen) || !"dialog_title".equals(id)) return original;
        // Issue #224: the facade may be long-saturated; never format it as the truth.
        return BigCraftingPlanClientStore.current()
                .map(snapshot -> BigAmountFormatter.formatPlanTitle(original, snapshot.usedBytes()))
                .orElse(original);
    }
}
