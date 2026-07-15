package com.syaru.ae2craftingoptimizer.intent;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.ArrayList;
import java.util.List;

public final class PatternIntentExtractor {
    private PatternIntentExtractor() {
    }

    public static String patternDefinitionId(IPatternDetails pattern) {
        if (pattern == null || pattern.getDefinition() == null) {
            return "unknown";
        }
        return pattern.getDefinition().getId().toString();
    }

    public static List<InputIntent> inputs(IPatternDetails pattern) {
        if (pattern == null) {
            return List.of();
        }
        List<InputIntent> inputs = new ArrayList<>();
        for (IPatternDetails.IInput input : pattern.getInputs()) {
            List<StackIntent> possible = new ArrayList<>();
            for (GenericStack stack : input.getPossibleInputs()) {
                possible.add(StackIntent.of(stack));
            }
            inputs.add(new InputIntent(possible, input.getMultiplier()));
        }
        return inputs;
    }

    public static List<StackIntent> outputs(IPatternDetails pattern) {
        if (pattern == null) {
            return List.of();
        }
        List<StackIntent> outputs = new ArrayList<>();
        for (GenericStack stack : pattern.getOutputs()) {
            outputs.add(StackIntent.of(stack));
        }
        return outputs;
    }

    public static List<StackIntent> concreteInputs(KeyCounter[] inputHolder) {
        if (inputHolder == null) {
            return List.of();
        }
        List<StackIntent> inputs = new ArrayList<>();
        for (KeyCounter counter : inputHolder) {
            if (counter == null || counter.isEmpty()) {
                continue;
            }
            for (Object2LongMap.Entry<AEKey> entry : counter) {
                inputs.add(StackIntent.of(entry.getKey(), entry.getLongValue()));
            }
        }
        return inputs;
    }
}
