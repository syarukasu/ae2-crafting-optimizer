package com.syaru.ae2craftingoptimizer.intent;

import java.util.List;

public record InputIntent(List<StackIntent> possibleInputs, long multiplier) {
    public InputIntent {
        possibleInputs = List.copyOf(possibleInputs);
    }
}
