package com.syaru.ae2craftingoptimizer.intent;

import java.util.List;

public record RecipeIntentSignature(
        String patternDefinitionId,
        List<InputIntent> inputs,
        List<StackIntent> concreteInputs,
        List<StackIntent> outputs) {
    public RecipeIntentSignature {
        inputs = List.copyOf(inputs);
        concreteInputs = List.copyOf(concreteInputs);
        outputs = List.copyOf(outputs);
    }

    public static RecipeIntentSignature of(RecipeIntent intent) {
        return new RecipeIntentSignature(
                intent.patternDefinitionId(),
                intent.inputs(),
                intent.concreteInputs(),
                intent.outputs());
    }

}
