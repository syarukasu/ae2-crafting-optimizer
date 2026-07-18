package com.syaru.ae2craftingoptimizer.batch;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import com.syaru.ae2craftingoptimizer.util.StableFingerprint;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class PatternTaskFingerprint {
    private static final Map<IPatternDetails, String> CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private PatternTaskFingerprint() {
    }

    public static String of(IPatternDetails details) {
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(details, PatternTaskFingerprint::compute);
        }
    }

    public static void clear() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    private static String compute(IPatternDetails details) {
        StringBuilder value = new StringBuilder(256);
        value.append(details.getClass().getName())
                .append('|')
                .append(details.getDefinition().toTagGeneric());
        for (IPatternDetails.IInput input : details.getInputs()) {
            value.append("|i:").append(input.getMultiplier());
            for (GenericStack possible : input.getPossibleInputs()) {
                value.append(':')
                        .append(possible.what().toTagGeneric())
                        .append('@')
                        .append(possible.amount());
            }
        }
        for (GenericStack output : details.getOutputs()) {
            value.append("|o:")
                    .append(output.what().toTagGeneric())
                    .append('@')
                    .append(output.amount());
        }
        return StableFingerprint.sha256(value) + ':' + details.getDefinition().getId();
    }
}
