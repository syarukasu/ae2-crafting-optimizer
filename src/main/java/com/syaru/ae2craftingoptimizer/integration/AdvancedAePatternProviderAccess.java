package com.syaru.ae2craftingoptimizer.integration;

import appeng.api.crafting.IPatternDetails;
import com.syaru.ae2craftingoptimizer.api.batch.v2.NativeBatchReceiptStore;
import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost;
import net.pedroksl.advanced_ae.common.patterns.AdvPatternDetails;
import org.jetbrains.annotations.Nullable;

/** Isolates direct Advanced AE references so common code remains safe without the optional mod. */
public final class AdvancedAePatternProviderAccess {
    private AdvancedAePatternProviderAccess() {
    }

    public static boolean hasDirectionalInputs(IPatternDetails pattern) {
        return pattern instanceof AdvPatternDetails advancedPattern
                && advancedPattern.directionalInputsSet();
    }

    @Nullable
    public static NativeBatchReceiptStore receiptStore(@Nullable Object host) {
        if (!(host instanceof AdvPatternProviderLogicHost providerHost)) {
            return null;
        }
        return providerHost.getLogic() instanceof NativeBatchReceiptStore store ? store : null;
    }
}
