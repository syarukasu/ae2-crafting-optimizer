package com.syaru.ae2craftingoptimizer.mixin;

import appeng.client.gui.NumberEntryType;
import appeng.client.gui.widgets.ConfirmableTextField;
import appeng.client.gui.widgets.NumberEntryWidget;
import com.syaru.ae2craftingoptimizer.access.ExactCraftAmountWidgetAccess;
import com.syaru.ae2craftingoptimizer.client.LongCraftAmountClientParser;
import com.syaru.ae2craftingoptimizer.craftingamount.CraftAmountExpression;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.util.Optional;
import java.util.OptionalLong;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** #224: exact parsing is enabled only by the crafting screen, never for other AE2 widgets. */
@Mixin(value = NumberEntryWidget.class, remap = false)
public abstract class NumberEntryWidgetExactCraftAmountMixin implements ExactCraftAmountWidgetAccess {
    @Shadow @Final private ConfirmableTextField textField;
    @Shadow @Final private DecimalFormat decimalFormat;
    @Shadow @Final private int normalTextColor;
    @Shadow @Final private int errorTextColor;
    @Shadow private NumberEntryType type;
    @Shadow private void validate() { throw new AssertionError(); }
    @Unique private boolean aco$exactCraftAmounts;
    @Unique private String aco$lastExpression;
    @Unique private int aco$lastUnit;
    @Unique private int aco$lastMaximumBits;
    @Unique private Optional<BigInteger> aco$parsedAmount = Optional.empty();

    @Override
    public void aco$enableExactCraftAmounts() {
        this.aco$exactCraftAmounts = true;
        this.textField.setMaxLength(CraftAmountExpression.MAXIMUM_CHARACTERS);
        this.validate();
    }

    @Override
    public Optional<BigInteger> aco$getExactCraftAmount() {
        String expression = this.textField.getValue();
        int unit = this.type.amountPerUnit();
        int bits = LongCraftAmountClientParser.maximumBits();
        if (!expression.equals(this.aco$lastExpression) || unit != this.aco$lastUnit
                || bits != this.aco$lastMaximumBits) {
            this.aco$parsedAmount = CraftAmountExpression.parse(expression, this.decimalFormat, unit, bits);
            this.aco$lastExpression = expression;
            this.aco$lastUnit = unit;
            this.aco$lastMaximumBits = bits;
        }
        return this.aco$parsedAmount;
    }

    @Override
    public void aco$setExactCraftAmount(BigInteger amount) {
        var displayed = new BigDecimal(amount).divide(BigDecimal.valueOf(this.type.amountPerUnit()));
        this.textField.setValue(this.decimalFormat.format(displayed));
        this.textField.moveCursorToEnd();
        this.textField.setHighlightPos(0);
    }

    @Inject(method = "getLongValue", at = @At("HEAD"), cancellable = true, require = 1)
    private void aco$exactLongValue(CallbackInfoReturnable<OptionalLong> cir) {
        if (!this.aco$exactCraftAmounts) return;
        var value = this.aco$getExactCraftAmount();
        cir.setReturnValue(value.isPresent() && value.get().bitLength() < Long.SIZE
                ? OptionalLong.of(value.get().longValueExact()) : OptionalLong.empty());
    }

    @Inject(method = "getValueInternal", at = @At("HEAD"), cancellable = true, require = 1)
    private void aco$exactInternalValue(CallbackInfoReturnable<Optional<BigDecimal>> cir) {
        if (!this.aco$exactCraftAmounts) return;
        cir.setReturnValue(this.aco$getExactCraftAmount().map(value ->
                new BigDecimal(value).divide(BigDecimal.valueOf(this.type.amountPerUnit()))));
    }

    @Inject(method = "validate", at = @At("HEAD"), cancellable = true, require = 1)
    private void aco$validateExactValue(CallbackInfo ci) {
        if (!this.aco$exactCraftAmounts) return;
        var amount = this.aco$getExactCraftAmount();
        this.textField.setTextColor(amount.isPresent() ? this.normalTextColor : this.errorTextColor);
        this.textField.setTooltipMessage(java.util.List.of(amount.<Component>map(value ->
                Component.literal("= " + this.decimalFormat.format(
                        new BigDecimal(value).divide(BigDecimal.valueOf(this.type.amountPerUnit())))))
                .orElseGet(() -> appeng.core.localization.GuiText.InvalidNumber.text())));
        ci.cancel();
    }

    @Inject(method = "addQty", at = @At("HEAD"), cancellable = true, require = 1)
    private void aco$addExactQuantity(long delta, CallbackInfo ci) {
        if (!this.aco$exactCraftAmounts) return;
        boolean targetTotal = this.textField.getValue().startsWith("=");
        var unit = BigInteger.valueOf(this.type.amountPerUnit());
        var current = this.aco$getExactCraftAmount().orElse(BigInteger.ZERO);
        var next = current.add(unit.multiply(BigInteger.valueOf(delta)));
        if (current.equals(unit) && delta > 0 && delta % 10 == 0) next = next.subtract(unit);
        next = next.max(BigInteger.ONE);
        if (next.bitLength() <= LongCraftAmountClientParser.maximumBits()
                && next.compareTo(com.syaru.ae2craftingoptimizer.engine.BigCountMath.hardMaximumValue()) <= 0) {
            this.aco$setExactCraftAmount(next);
            if (targetTotal) this.textField.setValue("=" + this.textField.getValue());
        }
        ci.cancel();
    }
}
