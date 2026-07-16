package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public final class PatternCandidatePruner {
    private PatternCandidatePruner() {
    }

    public static Collection<IPatternDetails> prune(Collection<IPatternDetails> patterns, AEKey requestedOutput) {
        if (!ACOConfig.pruneInvalidCraftingCandidates() || patterns == null || patterns.isEmpty()) {
            return patterns;
        }

        Set<IPatternDetails> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        var result = new ArrayList<IPatternDetails>(patterns.size());
        for (IPatternDetails pattern : patterns) {
            if (pattern == null || !seen.add(pattern)) {
                continue;
            }

            try {
                if (hasRequestedOutput(pattern, requestedOutput) && hasStructurallyValidInputs(pattern)) {
                    result.add(pattern);
                }
            } catch (RuntimeException ignored) {
                // Add-on patterns may compute metadata lazily. Preserve AE2 behavior if inspection itself fails.
                result.add(pattern);
            }
        }

        return result.size() == patterns.size() ? patterns : List.copyOf(result);
    }

    private static boolean hasRequestedOutput(IPatternDetails pattern, AEKey requestedOutput) {
        GenericStack[] outputs = pattern.getOutputs();
        if (outputs == null || outputs.length == 0) {
            return false;
        }

        for (GenericStack output : outputs) {
            if (output != null && output.amount() > 0 && requestedOutput.matches(output)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasStructurallyValidInputs(IPatternDetails pattern) {
        IPatternDetails.IInput[] inputs = pattern.getInputs();
        if (inputs == null) {
            return false;
        }

        for (IPatternDetails.IInput input : inputs) {
            if (input == null || input.getMultiplier() <= 0) {
                return false;
            }

            GenericStack[] possibleInputs = input.getPossibleInputs();
            if (possibleInputs == null || possibleInputs.length == 0) {
                return false;
            }

            boolean hasUsableInput = false;
            for (GenericStack possibleInput : possibleInputs) {
                if (possibleInput != null && possibleInput.what() != null && possibleInput.amount() > 0) {
                    hasUsableInput = true;
                    break;
                }
            }
            if (!hasUsableInput) {
                return false;
            }
        }
        return true;
    }
}
