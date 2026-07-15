package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.util.concurrent.TimeUnit;

public final class CraftingCalculationDiagnostics {
    private CraftingCalculationDiagnostics() {
    }

    public static void logIfSlow(AEKey output, long requestedAmount, ICraftingPlan plan, long elapsedNanos) {
        if (!ACOConfig.logSlowCraftCalculations()) {
            return;
        }

        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
        if (elapsedMillis < ACOConfig.getSlowCraftCalculationMillis()) {
            return;
        }

        GenericStack finalOutput = plan != null ? plan.finalOutput() : null;
        int missingCount = plan != null ? plan.missingItems().size() : -1;
        long bytes = plan != null ? plan.bytes() : -1L;
        AE2CraftingOptimizer.LOGGER.info(
                "Slow AE2 crafting calculation: requested {} x{}, final {}, missing entries {}, bytes {}, elapsed {} ms",
                output.getId(),
                requestedAmount,
                finalOutput,
                missingCount,
                bytes,
                elapsedMillis);
    }
}
