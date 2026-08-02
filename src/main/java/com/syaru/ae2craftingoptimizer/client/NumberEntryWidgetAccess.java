package com.syaru.ae2craftingoptimizer.client;

import appeng.client.gui.widgets.ConfirmableTextField;
import java.text.DecimalFormat;

/** CraftAmount画面から入力式を読む、Mixinではないクライアント専用契約。 */
public interface NumberEntryWidgetAccess {
    ConfirmableTextField aco$getTextField();

    DecimalFormat aco$getDecimalFormat();
}
