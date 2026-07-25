package com.syaru.ae2craftingoptimizer.mixin;

import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.core.localization.GuiText;
import com.syaru.ae2craftingoptimizer.client.BigAmountFormatter;
import com.syaru.ae2craftingoptimizer.client.BigCraftingPlanClientStore;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Crafting Planの使用byte数をBigInteger正本へ差し替える。 */
@Mixin(value = CraftConfirmScreen.class, remap = false)
public abstract class CraftConfirmScreenBigIntegerMixin {
    @ModifyArg(
            method = "updateBeforeRender",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/client/gui/me/crafting/CraftConfirmScreen;setTextContent(Ljava/lang/String;Lnet/minecraft/network/chat/Component;)V",
                    ordinal = 0),
            index = 1,
            require = 1)
    private Component aco$showExactUsedBytes(Component original) {
        return BigCraftingPlanClientStore.current()
                .<Component>map(snapshot -> GuiText.CraftingPlan.text(
                        GuiText.BytesUsed.text(
                                BigAmountFormatter.formatCompact(snapshot.usedBytes()))))
                .orElse(original);
    }
}
