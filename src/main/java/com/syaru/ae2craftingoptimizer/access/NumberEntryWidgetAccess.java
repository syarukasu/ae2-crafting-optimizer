package com.syaru.ae2craftingoptimizer.access;

import appeng.client.gui.widgets.ConfirmableTextField;
import java.text.DecimalFormat;

/** Runtime contract for the AE2 NumberEntryWidget accessor. */
public interface NumberEntryWidgetAccess {
    ConfirmableTextField aco$getTextField();

    DecimalFormat aco$getDecimalFormat();
}
