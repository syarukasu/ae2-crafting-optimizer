package com.syaru.ae2craftingoptimizer.mixin;

import appeng.client.gui.widgets.NumberEntryWidget;
import com.syaru.ae2craftingoptimizer.client.BigAmountFormatter;
import com.syaru.ae2craftingoptimizer.client.CraftAmountTooltipAccess;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = NumberEntryWidget.class, remap = false)
public abstract class NumberEntryWidgetCraftAmountTooltipMixin implements CraftAmountTooltipAccess {
    @Shadow @Final private DecimalFormat decimalFormat;
    @Shadow private Optional<BigDecimal> getValueInternal() { throw new AssertionError(); }
    @Unique private boolean aco$compactCraftTooltip;

    @Override
    public void aco$enableCompactCraftTooltip() {
        this.aco$compactCraftTooltip = true;
    }

    @ModifyArg(method = "validate", at = @At(value = "INVOKE",
            target = "Lappeng/client/gui/widgets/ConfirmableTextField;setTooltipMessage(Ljava/util/List;)V"),
            index = 0, require = 1)
    private List<Component> aco$boundedFieldTooltip(List<Component> original) {
        return aco$boundedEvaluatedAmount(original);
    }

    @ModifyArg(method = "validate", at = @At(value = "INVOKE",
            target = "Lappeng/client/gui/widgets/ValidationIcon;setTooltip(Ljava/util/List;)V"),
            index = 0, require = 1)
    private List<Component> aco$boundedIconTooltip(List<Component> original) {
        return aco$boundedEvaluatedAmount(original);
    }

    @Unique
    private List<Component> aco$boundedEvaluatedAmount(List<Component> original) {
        if (!this.aco$compactCraftTooltip) return original;
        // Issue #224: never replace native validation errors with an evaluated value.
        if (original.size() != 1 || !(original.get(0).getContents() instanceof PlainTextContents)
                || !original.get(0).getString().startsWith("= ")) return original;
        return this.getValueInternal().<List<Component>>map(amount ->
                List.of(BigAmountFormatter.formatAmountTooltip(amount, this.decimalFormat)))
                .orElse(original);
    }
}
