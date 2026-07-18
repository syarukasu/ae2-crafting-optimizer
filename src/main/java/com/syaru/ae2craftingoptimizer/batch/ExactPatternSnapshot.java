package com.syaru.ae2craftingoptimizer.batch;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.syaru.ae2craftingoptimizer.api.batch.PatternBatchContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record ExactPatternSnapshot(Map<AEKey, Long> inputs, Map<AEKey, Long> outputs) {
    public ExactPatternSnapshot {
        inputs = Map.copyOf(inputs);
        outputs = Map.copyOf(outputs);
    }

    public static ExactPatternSnapshot of(PatternBatchContext context) {
        Map<AEKey, Long> inputs = new HashMap<>();
        for (KeyCounter counter : context.copyInputsPerExecution()) {
            for (var entry : counter) {
                inputs.merge(entry.getKey(), entry.getLongValue(), Math::addExact);
            }
        }
        return new ExactPatternSnapshot(inputs, counterTotals(context.copyOutputsPerExecution()));
    }

    public boolean hasOnlyPositiveAmounts() {
        return !inputs.isEmpty()
                && !outputs.isEmpty()
                && inputs.values().stream().allMatch(value -> value > 0L)
                && outputs.values().stream().allMatch(value -> value > 0L);
    }

    public boolean outputsEqual(List<GenericStack> expected) {
        Map<AEKey, Long> totals = new HashMap<>();
        for (GenericStack stack : expected) {
            if (stack == null || stack.amount() <= 0L) {
                return false;
            }
            totals.merge(stack.what(), stack.amount(), Math::addExact);
        }
        return outputs.equals(totals);
    }

    private static Map<AEKey, Long> counterTotals(KeyCounter counter) {
        Map<AEKey, Long> totals = new HashMap<>();
        for (var entry : counter) {
            totals.merge(entry.getKey(), entry.getLongValue(), Math::addExact);
        }
        return totals;
    }
}
