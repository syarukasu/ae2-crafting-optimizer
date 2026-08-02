package com.syaru.ae2craftingoptimizer.mixin;

import appeng.client.gui.widgets.ConfirmableTextField;
import appeng.client.gui.widgets.NumberEntryWidget;
import com.syaru.ae2craftingoptimizer.client.NumberEntryWidgetAccess;
import java.text.DecimalFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * CraftAmountScreenだけが入力を厳密に再検証するための読み取り専用Accessor。
 */
@Mixin(value = NumberEntryWidget.class, remap = false)
public interface NumberEntryWidgetAccessor extends NumberEntryWidgetAccess {
    @Override
    @Accessor("textField")
    ConfirmableTextField aco$getTextField();

    @Override
    @Accessor("decimalFormat")
    DecimalFormat aco$getDecimalFormat();
}
