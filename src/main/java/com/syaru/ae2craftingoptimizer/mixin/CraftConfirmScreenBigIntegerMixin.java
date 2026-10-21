package com.syaru.ae2craftingoptimizer.mixin;

import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.core.localization.GuiText;
import com.syaru.ae2craftingoptimizer.client.BigAmountFormatter;
import com.syaru.ae2craftingoptimizer.client.BigCraftingPlanClientStore;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Crafting Planの使用byte数をBigInteger正本へ差し替える。 */
@Mixin(value = CraftConfirmScreen.class, remap = false)
public abstract class CraftConfirmScreenBigIntegerMixin {
    @Shadow @Final private appeng.client.gui.me.crafting.CraftConfirmTableRenderer table;

    // Issue #224: copy exact sidecar counts, never the shortened visual description.
    @Inject(method = {"keyPressed", "m_7933_"}, at = @At("HEAD"), cancellable = true, require = 1)
    private void aco$copyExactHoveredCounts(int key, int scan, int modifiers,
            CallbackInfoReturnable<Boolean> cir) {
        if (!net.minecraft.client.gui.screens.Screen.isCopy(key)) return;
        var hovered = table.getHoveredStack();
        if (hovered == null) return;
        BigCraftingPlanClientStore.entry(hovered.stack().what()).ifPresent(entry -> {
            String text = BigAmountFormatter.exactCountsForClipboard(
                    hovered.stack().what().getDisplayName().getString(),
                    entry.stored(), entry.missing(), entry.craft());
            net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(text);
            cir.setReturnValue(true);
        });
    }

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
