package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.client.gui.me.crafting.CraftConfirmTableRenderer;
import appeng.core.localization.GuiText;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;
import com.syaru.ae2craftingoptimizer.client.BigAmountFormatter;
import com.syaru.ae2craftingoptimizer.client.BigCraftingPlanClientStore;
import com.syaru.ae2craftingoptimizer.engine.BigCraftingPlanSummary;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Craft確認表の個別数量を、long飽和値ではなくBigInteger正本で描画する。 */
@Mixin(value = CraftConfirmTableRenderer.class, remap = false)
public abstract class CraftConfirmTableRendererBigIntegerMixin {
    @Inject(method = "getEntryDescription", at = @At("HEAD"), cancellable = true, require = 1)
    private void aco$getExactDescription(
            CraftingPlanSummaryEntry vanillaEntry,
            CallbackInfoReturnable<List<Component>> cir) {
        BigCraftingPlanClientStore.entry(vanillaEntry.getWhat()).ifPresent(entry ->
                cir.setReturnValue(aco$description(vanillaEntry.getWhat(), entry, AmountFormat.SLOT)));
    }

    @Inject(method = "getEntryTooltip", at = @At("HEAD"), cancellable = true, require = 1)
    private void aco$getExactTooltip(
            CraftingPlanSummaryEntry vanillaEntry,
            CallbackInfoReturnable<List<Component>> cir) {
        AEKey key = vanillaEntry.getWhat();
        BigCraftingPlanClientStore.entry(key).ifPresent(entry -> {
            List<Component> tooltip = new ArrayList<>(AEKeyRendering.getTooltip(key));
            tooltip.addAll(aco$description(key, entry, AmountFormat.FULL));
            cir.setReturnValue(tooltip);
        });
    }

    @Inject(method = "getEntryOverlayColor", at = @At("HEAD"), cancellable = true, require = 1)
    private void aco$getExactOverlayColor(
            CraftingPlanSummaryEntry vanillaEntry,
            CallbackInfoReturnable<Integer> cir) {
        BigCraftingPlanClientStore.entry(vanillaEntry.getWhat()).ifPresent(entry ->
                cir.setReturnValue(entry.missing().signum() > 0 ? 0x1BFF0000 : 0));
    }

    private static List<Component> aco$description(
            AEKey key,
            BigCraftingPlanSummary.Entry entry,
            AmountFormat format) {
        List<Component> result = new ArrayList<>(3);
        aco$add(result, GuiText.FromStorage, key, entry.stored(), format);
        aco$add(result, GuiText.Missing, key, entry.missing(), format);
        aco$add(result, GuiText.ToCraft, key, entry.craft(), format);
        return result;
    }

    private static void aco$add(
            List<Component> target,
            GuiText label,
            AEKey key,
            BigInteger amount,
            AmountFormat format) {
        if (amount.signum() > 0) {
            target.add(label.text(BigAmountFormatter.format(key, amount, format)));
        }
    }
}
