package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;

/** One synchronous counted payload, not a stock or acceptance cache. */
public final class GTCountedInputPush {
    private static final ThreadLocal<Payload> ACTIVE = new ThreadLocal<>();
    private GTCountedInputPush() {}

    public static boolean supported(IPatternDetails pattern) {
        return pattern.getClass() == AEProcessingPattern.class
                || CountedNativeContracts.defaultExternalPush(pattern);
    }

    public static boolean push(ICraftingProvider provider, IPatternDetails pattern, KeyCounter[] inputs) {
        var previous = ACTIVE.get();
        ACTIVE.set(new Payload(pattern, inputs));
        try { return provider.pushPattern(pattern, inputs); }
        finally {
            if (previous == null) ACTIVE.remove();
            else ACTIVE.set(previous);
        }
    }

    public static void toSlot(IPatternDetails pattern, KeyCounter[] inputs,
            IPatternDetails.PatternInputSink sink) {
        var payload = ACTIVE.get();
        if (payload != null && payload.pattern == pattern && payload.inputs == inputs
                && pattern.getClass() == AEProcessingPattern.class) {
            // #245: sparse native reconstruction forwards one recipe, not the counted payload.
            // GT's original sink merges by key; sparse slot order is irrelevant here.
            for (var slot : inputs)
                for (var entry : slot) sink.pushInput(entry.getKey(), entry.getLongValue());
        } else {
            pattern.pushInputsToExternalInventory(inputs, sink);
        }
    }

    private record Payload(IPatternDetails pattern, KeyCounter[] inputs) {}
}

