package com.syaru.ae2craftingoptimizer.client;

import appeng.client.gui.MathExpressionParser;
import appeng.client.gui.widgets.NumberEntryWidget;
import com.syaru.ae2craftingoptimizer.craftingamount.LongCraftAmountRules;
import com.syaru.ae2craftingoptimizer.mixin.NumberEntryWidgetAccessor;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * NumberEntryWidgetのlongValue()が範囲外で折り返さないよう、
 * CraftAmountScreenの入力だけをBigDecimalから厳密変換する。
 */
public final class LongCraftAmountClientParser {
    private LongCraftAmountClientParser() {
    }

    public static OptionalLong parseExact(NumberEntryWidget widget) {
        NumberEntryWidgetAccessor accessor = (NumberEntryWidgetAccessor) (Object) widget;
        String expression = accessor.aco$getTextField().getValue();
        // AE2の「=現在総数」記法は数式本体から接頭辞だけを外して解析する。
        if (expression.startsWith("=")) {
            expression = expression.substring(1);
        }

        try {
            Optional<BigDecimal> parsed = MathExpressionParser.parse(
                    expression,
                    accessor.aco$getDecimalFormat());
            // 解析不能な文字列は本家と同様に確定ボタンを無効化する。
            if (parsed.isEmpty()) {
                return OptionalLong.empty();
            }
            BigDecimal entered = parsed.get();
            // 個数単位では小数を許可せず、Fluid/Chemicalの単位換算時だけ小数を許可する。
            if (widget.getType().amountPerUnit() == 1 && entered.scale() > 0) {
                return OptionalLong.empty();
            }
            return LongCraftAmountRules.toExactExternalAmount(
                    entered,
                    widget.getType().amountPerUnit());
        } catch (RuntimeException invalidExpression) {
            // 数式Parserや単位換算が拒否した入力をlongへ丸めず、画面上で無効として扱う。
            return OptionalLong.empty();
        }
    }
}
