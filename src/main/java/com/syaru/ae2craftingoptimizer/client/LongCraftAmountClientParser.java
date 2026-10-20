package com.syaru.ae2craftingoptimizer.client;

import appeng.client.gui.widgets.NumberEntryWidget;
import com.syaru.ae2craftingoptimizer.access.ExactCraftAmountWidgetAccess;
import com.syaru.ae2craftingoptimizer.access.NumberEntryWidgetAccess;
import com.syaru.ae2craftingoptimizer.craftingamount.CraftAmountExpression;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.math.BigInteger;
import java.util.Optional;
import java.util.OptionalLong;

/** Exact parsing shared by the crafting screen and its legacy long boundary. */
public final class LongCraftAmountClientParser {
    private LongCraftAmountClientParser() { }

    public static Optional<BigInteger> parseBigInteger(NumberEntryWidget widget) {
        if (widget instanceof ExactCraftAmountWidgetAccess exact) {
            return exact.aco$getExactCraftAmount();
        }
        NumberEntryWidgetAccess accessor = (NumberEntryWidgetAccess) (Object) widget;
        return CraftAmountExpression.parse(accessor.aco$getTextField().getValue(),
                accessor.aco$getDecimalFormat(), widget.getType().amountPerUnit(), maximumBits());
    }

    public static int maximumBits() {
        return ACOConfig.enableBigIntegerCraftingBackend()
                ? ACOConfig.getBigIntegerMaximumBits() : Long.SIZE - 1;
    }

    public static OptionalLong parseExact(NumberEntryWidget widget) {
        var value = parseBigInteger(widget);
        return value.isPresent() && value.get().bitLength() < Long.SIZE
                ? OptionalLong.of(value.get().longValueExact()) : OptionalLong.empty();
    }
}
